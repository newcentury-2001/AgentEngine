package com.agentops.service;

import com.agentcommon.concurrent.ExecutorSaturatedException;
import com.agentcommon.http.HttpRequestClient;
import com.agentcommon.http.LlmHttpClientRouter;
import com.agentcommon.http.ZhipuHttpProtocol;
import com.agentcommon.mcp.model.InputSlot;
import com.agentcommon.mcp.model.McpSkill;
import com.agentcommon.mcp.model.McpTool;
import com.agentops.config.OpsMcpProperties;
import com.agentops.mcpclean.McpSummaryCleanTaskPublisher;
import com.agentops.mcpclean.McpSummaryCleanTaskTracker;
import com.agentops.mcpclean.model.McpSummaryCleanTaskMessage;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class McpSummaryLlmCleanService {

    private static final int DESC_MAX_LEN = 30;
    private static final DateTimeFormatter BCK_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");
    private static final Pattern JSON_BLOCK = Pattern.compile("```json\\s*(\\{[\\s\\S]*?\\})\\s*```");

    private static final String CTX_BASE_PREFIX = "mcpclean:ctx:base:";
    private static final String CTX_DESC_HASH_PREFIX = "mcpclean:ctx:desc:";
    private static final String CTX_TOOL_DESC_HASH_PREFIX = "mcpclean:ctx:tooldesc:";
    private static final String CTX_TOOL_SLOTS_HASH_PREFIX = "mcpclean:ctx:toolslots:";
    private static final String CTX_SKILL_TAGS_HASH_PREFIX = "mcpclean:ctx:skilltags:";
    private static final String CTX_FINALIZED_PREFIX = "mcpclean:ctx:finalized:";
    private static final String CTX_FINALIZING_PREFIX = "mcpclean:ctx:finalizing:";

    private final OpsMcpProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpRequestClient httpRequestClient;
    private final LlmHttpClientRouter llmHttpClientRouter;
    private final McpSummaryCleanTaskPublisher taskPublisher;
    private final McpSummaryCleanTaskTracker taskTracker;
    private final StringRedisTemplate stringRedisTemplate;

    @Value("${agent.mcp-cleaner.context.ttl-hours:24}")
    private long contextTtlHours;

    public McpSummaryLlmCleanService(
            OpsMcpProperties properties,
            ObjectMapper objectMapper,
            HttpRequestClient httpRequestClient,
            LlmHttpClientRouter llmHttpClientRouter,
            McpSummaryCleanTaskPublisher taskPublisher,
            McpSummaryCleanTaskTracker taskTracker,
            StringRedisTemplate stringRedisTemplate) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpRequestClient = httpRequestClient;
        this.llmHttpClientRouter = llmHttpClientRouter;
        this.taskPublisher = taskPublisher;
        this.taskTracker = taskTracker;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public Map<String, Object> enqueueLlmCleanTasks() {
        Path summaryPath = resolvePath(properties.getSummaryJsonPath());
        Path exportJsonPath = resolvePath(properties.getOutputJsonPath());
        List<McpSkill> exportSkills = buildSummaryFromExport(exportJsonPath);
        List<McpSkill> skills = loadBaseSummaryForCleaning(summaryPath, exportSkills);

        String taskId = UUID.randomUUID().toString().replace("-", "");
        initializeTaskContext(taskId, skills);

        int success = 0;
        List<String> failedSkills = new ArrayList<>();
        long now = System.currentTimeMillis();
        taskTracker.markQueued(taskId, skills.size(), now);

        for (McpSkill skill : skills) {
            McpSummaryCleanTaskMessage msg = new McpSummaryCleanTaskMessage();
            msg.setTaskId(taskId);
            msg.setSkillName(safe(skill.getSkillName()));
            msg.setPendingToolNames(List.of());
            msg.setSkillPending(true);
            msg.setRetryCount(0);
            msg.setMaxRetry(taskPublisher.maxRetry());
            msg.setCreatedAtEpochMs(now);
            if (taskPublisher.sendWithRetry(msg)) {
                success++;
            } else {
                failedSkills.add(msg.getSkillName());
                taskTracker.markSkillFailed(taskId, msg.getSkillName(), "enqueue failed");
            }
        }

        if (success == 0) {
            finalizeTask(taskId);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("message", "llm summary clean tasks enqueued");
        out.put("taskId", taskId);
        out.put("sourceExportPath", exportJsonPath.toString());
        out.put("summaryPath", summaryPath.toString());
        out.put("totalSkills", skills.size());
        out.put("enqueuedCount", success);
        out.put("failedCount", failedSkills.size());
        out.put("failedSkills", failedSkills);
        return out;
    }

    public TaskProcessResult processTask(McpSummaryCleanTaskMessage task) {
        McpSkill skill = loadSkillFromTaskContext(task.getTaskId(), task.getSkillName());
        if (skill == null) {
            return TaskProcessResult.done();
        }

        List<String> pendingToolNames = normalizePendingToolNames(task, skill);
        List<String> retryToolNames = new ArrayList<>();
        for (McpTool tool : safeTools(skill)) {
            String toolName = safe(tool.getToolName());
            if (toolName.isBlank() || !pendingToolNames.contains(toolName)) {
                continue;
            }
            LlmCleanResult toolDesc = cleanToolDescriptionWithLlm(skill, tool);
            if (toolDesc.retryable) {
                retryToolNames.add(toolName);
                continue;
            }
            String normalizedToolDesc = normalizeDesc(toolDesc.description);
            if (!normalizedToolDesc.isBlank() && !normalizedToolDesc.equals(safe(tool.getToolDescription()))) {
                saveToolDescriptionToContext(task.getTaskId(), task.getSkillName(), toolName, normalizedToolDesc);
                tool.setToolDescription(normalizedToolDesc);
            }
            if (toolDesc.inputSlots != null && !toolDesc.inputSlots.isEmpty()) {
                saveToolInputSlotsToContext(task.getTaskId(), task.getSkillName(), toolName, toolDesc.inputSlots);
                tool.setInputSlots(toolDesc.inputSlots);
            }
        }
        if (!retryToolNames.isEmpty()) {
            return new TaskProcessResult(retryToolNames, false);
        }

        boolean retrySkill = false;
        if (Boolean.TRUE.equals(task.getSkillPending())) {
            LlmCleanResult skillDesc = cleanSkillDescriptionWithLlm(skill);
            if (skillDesc.retryable) {
                retrySkill = true;
            } else {
                String normalized = normalizeDesc(skillDesc.description);
                if (!normalized.isBlank() && !normalized.equals(safe(skill.getSkillDescription()))) {
                    saveSkillDescriptionToContext(task.getTaskId(), task.getSkillName(), normalized);
                }
                if (skillDesc.tags != null && !skillDesc.tags.isEmpty()) {
                    List<String> normalizedTags = normalizePrimaryTags(skillDesc.tags);
                    if (!normalizedTags.isEmpty()) {
                        saveSkillTagsToContext(task.getTaskId(), task.getSkillName(), normalizedTags);
                    }
                }
            }
        }
        return new TaskProcessResult(retryToolNames, retrySkill);
    }

    public boolean finalizeTask(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            return false;
        }
        String finalizedKey = CTX_FINALIZED_PREFIX + taskId;
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(finalizedKey))) {
            return false;
        }

        String finalizingKey = CTX_FINALIZING_PREFIX + taskId;
        Boolean lock = stringRedisTemplate.opsForValue().setIfAbsent(finalizingKey, "1", 5, TimeUnit.MINUTES);
        if (!Boolean.TRUE.equals(lock)) {
            return false;
        }
        try {
            if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(finalizedKey))) {
                return false;
            }
            List<McpSkill> skills = loadBaseSkills(taskId);
            if (skills.isEmpty()) {
                skills = readSummary(resolvePath(properties.getSummaryJsonPath()));
            }

            Map<Object, Object> descMap = stringRedisTemplate.opsForHash().entries(ctxDescKey(taskId));
            if (descMap != null && !descMap.isEmpty()) {
                for (McpSkill one : skills) {
                    Object val = descMap.get(safe(one.getSkillName()));
                    if (val == null) {
                        continue;
                    }
                    String desc = safe(String.valueOf(val));
                    if (!desc.isBlank()) {
                        one.setSkillDescription(desc);
                    }
                }
            }
            Map<Object, Object> skillTagsMap = stringRedisTemplate.opsForHash().entries(ctxSkillTagsKey(taskId));
            if (skillTagsMap != null && !skillTagsMap.isEmpty()) {
                for (McpSkill one : skills) {
                    Object val = skillTagsMap.get(safe(one.getSkillName()));
                    if (val == null) {
                        continue;
                    }
                    List<String> tags = parseTagsJson(String.valueOf(val));
                    if (!tags.isEmpty()) {
                        one.setTags(tags);
                        one.setIntent(tags.get(0));
                    }
                    one.setActionType(null);
                }
            }

            Map<Object, Object> toolDescMap = stringRedisTemplate.opsForHash().entries(ctxToolDescKey(taskId));
            if (toolDescMap != null && !toolDescMap.isEmpty()) {
                for (McpSkill one : skills) {
                    for (McpTool tool : safeTools(one)) {
                        String field = toolDescField(one.getSkillName(), tool.getToolName());
                        Object val = toolDescMap.get(field);
                        if (val == null) {
                            continue;
                        }
                        String desc = safe(String.valueOf(val));
                        if (!desc.isBlank()) {
                            tool.setToolDescription(desc);
                        }
                    }
                }
            }

            Map<Object, Object> toolSlotsMap = stringRedisTemplate.opsForHash().entries(ctxToolSlotsKey(taskId));
            if (toolSlotsMap != null && !toolSlotsMap.isEmpty()) {
                for (McpSkill one : skills) {
                    for (McpTool tool : safeTools(one)) {
                        String field = toolDescField(one.getSkillName(), tool.getToolName());
                        Object val = toolSlotsMap.get(field);
                        if (val == null) {
                            continue;
                        }
                        List<InputSlot> slots = parseInputSlotsJson(String.valueOf(val));
                        if (!slots.isEmpty()) {
                            tool.setInputSlots(slots);
                        }
                    }
                }
            }

            for (McpSkill one : skills) {
                one.setActionType(null);
                if (safe(one.getIntent()).isBlank()) {
                    List<String> tags = normalizePrimaryTags(one.getTags());
                    one.setTags(tags);
                    if (!tags.isEmpty()) {
                        one.setIntent(tags.get(0));
                    }
                } else {
                    String canonicalIntent = canonicalizeSkillTag(one.getIntent());
                    if (!canonicalIntent.isBlank()) {
                        one.setIntent(canonicalIntent);
                        one.setTags(List.of(canonicalIntent));
                    }
                }
            }

            writeSummaryWithBackup(resolvePath(properties.getSummaryJsonPath()), skills, taskId);
            stringRedisTemplate.opsForValue().set(finalizedKey, "1", Math.max(1, contextTtlHours), TimeUnit.HOURS);
            cleanupTaskContext(taskId);
            return true;
        } finally {
            stringRedisTemplate.delete(finalizingKey);
        }
    }

    private void initializeTaskContext(String taskId, List<McpSkill> skills) {
        try {
            stringRedisTemplate.opsForValue().set(
                    ctxBaseKey(taskId),
                    objectMapper.writeValueAsString(skills),
                    Math.max(1, contextTtlHours),
                    TimeUnit.HOURS
            );
            stringRedisTemplate.delete(ctxDescKey(taskId));
            stringRedisTemplate.delete(ctxToolDescKey(taskId));
            stringRedisTemplate.delete(ctxToolSlotsKey(taskId));
            stringRedisTemplate.delete(ctxSkillTagsKey(taskId));
            stringRedisTemplate.delete(CTX_FINALIZED_PREFIX + taskId);
        } catch (Exception e) {
            throw new IllegalStateException("init mcp clean task context failed. taskId=" + taskId, e);
        }
    }

    private McpSkill loadSkillFromTaskContext(String taskId, String skillName) {
        List<McpSkill> skills = loadBaseSkills(taskId);
        if (skills.isEmpty()) {
            skills = readSummary(resolvePath(properties.getSummaryJsonPath()));
        }
        McpSkill skill = findSkill(skills, skillName);
        if (skill != null) {
            applyToolDescriptionsFromContext(taskId, skill);
        }
        return skill;
    }

    private List<McpSkill> loadBaseSkills(String taskId) {
        try {
            String raw = stringRedisTemplate.opsForValue().get(ctxBaseKey(taskId));
            if (raw == null || raw.isBlank()) {
                return List.of();
            }
            return objectMapper.readValue(raw, new TypeReference<List<McpSkill>>() {
            });
        } catch (Exception e) {
            return List.of();
        }
    }

    private void saveSkillDescriptionToContext(String taskId, String skillName, String description) {
        String key = ctxDescKey(taskId);
        stringRedisTemplate.opsForHash().put(key, safe(skillName), safe(description));
        stringRedisTemplate.expire(key, Math.max(1, contextTtlHours), TimeUnit.HOURS);
    }

    private void saveSkillTagsToContext(String taskId, String skillName, List<String> tags) {
        List<String> normalized = normalizePrimaryTags(tags);
        if (normalized.isEmpty()) {
            return;
        }
        String key = ctxSkillTagsKey(taskId);
        try {
            stringRedisTemplate.opsForHash().put(key, safe(skillName), objectMapper.writeValueAsString(normalized));
            stringRedisTemplate.expire(key, Math.max(1, contextTtlHours), TimeUnit.HOURS);
        } catch (Exception ignored) {
        }
    }

    private void saveToolDescriptionToContext(String taskId, String skillName, String toolName, String description) {
        String key = ctxToolDescKey(taskId);
        String field = toolDescField(skillName, toolName);
        stringRedisTemplate.opsForHash().put(key, field, safe(description));
        stringRedisTemplate.expire(key, Math.max(1, contextTtlHours), TimeUnit.HOURS);
    }

    private void saveToolInputSlotsToContext(String taskId, String skillName, String toolName, List<InputSlot> slots) {
        String key = ctxToolSlotsKey(taskId);
        String field = toolDescField(skillName, toolName);
        try {
            stringRedisTemplate.opsForHash().put(key, field, objectMapper.writeValueAsString(slots));
            stringRedisTemplate.expire(key, Math.max(1, contextTtlHours), TimeUnit.HOURS);
        } catch (Exception ignored) {
        }
    }

    private void applyToolDescriptionsFromContext(String taskId, McpSkill skill) {
        List<McpTool> tools = safeTools(skill);
        if (tools.isEmpty()) {
            return;
        }
        String key = ctxToolDescKey(taskId);
        List<Object> fields = new ArrayList<>(tools.size());
        for (McpTool tool : tools) {
            fields.add(toolDescField(skill.getSkillName(), tool.getToolName()));
        }
        List<Object> values = stringRedisTemplate.opsForHash().multiGet(key, fields);
        if (values == null || values.isEmpty()) {
            return;
        }
        for (int i = 0; i < tools.size() && i < values.size(); i++) {
            Object value = values.get(i);
            if (value == null) {
                continue;
            }
            String desc = safe(String.valueOf(value));
            if (!desc.isBlank()) {
                tools.get(i).setToolDescription(desc);
            }
        }

        String slotsKey = ctxToolSlotsKey(taskId);
        List<Object> slotValues = stringRedisTemplate.opsForHash().multiGet(slotsKey, fields);
        if (slotValues == null || slotValues.isEmpty()) {
            return;
        }
        for (int i = 0; i < tools.size() && i < slotValues.size(); i++) {
            Object value = slotValues.get(i);
            if (value == null) {
                continue;
            }
            List<InputSlot> slots = parseInputSlotsJson(String.valueOf(value));
            if (!slots.isEmpty()) {
                tools.get(i).setInputSlots(slots);
            }
        }
    }

    private void cleanupTaskContext(String taskId) {
        stringRedisTemplate.delete(ctxBaseKey(taskId));
        stringRedisTemplate.delete(ctxDescKey(taskId));
        stringRedisTemplate.delete(ctxToolDescKey(taskId));
        stringRedisTemplate.delete(ctxToolSlotsKey(taskId));
        stringRedisTemplate.delete(ctxSkillTagsKey(taskId));
    }

    private String ctxBaseKey(String taskId) {
        return CTX_BASE_PREFIX + safe(taskId);
    }

    private String ctxDescKey(String taskId) {
        return CTX_DESC_HASH_PREFIX + safe(taskId);
    }

    private String ctxToolDescKey(String taskId) {
        return CTX_TOOL_DESC_HASH_PREFIX + safe(taskId);
    }

    private String ctxToolSlotsKey(String taskId) {
        return CTX_TOOL_SLOTS_HASH_PREFIX + safe(taskId);
    }

    private String ctxSkillTagsKey(String taskId) {
        return CTX_SKILL_TAGS_HASH_PREFIX + safe(taskId);
    }

    private String toolDescField(String skillName, String toolName) {
        return safe(skillName) + "|" + safe(toolName);
    }

    private List<String> normalizePendingToolNames(McpSummaryCleanTaskMessage task, McpSkill skill) {
        List<String> all = new ArrayList<>();
        for (McpTool tool : safeTools(skill)) {
            String toolName = safe(tool.getToolName());
            if (!toolName.isBlank()) {
                all.add(toolName);
            }
        }
        List<String> pending = task.getPendingToolNames();
        if (pending == null || pending.isEmpty()) {
            return all;
        }
        List<String> normalized = new ArrayList<>();
        for (String one : pending) {
            String toolName = safe(one);
            if (!toolName.isBlank() && all.contains(toolName)) {
                normalized.add(toolName);
            }
        }
        return normalized.isEmpty() ? all : normalized;
    }

    private LlmCleanResult cleanToolDescriptionWithLlm(McpSkill skill, McpTool tool) {
        try {
            String promptTemplate = readPromptTemplate(properties.getCleanToolPromptPath());
            String prompt = promptTemplate
                    .replace("{{skillName}}", safe(skill.getSkillName()))
                    .replace("{{toolName}}", safe(tool.getToolName()))
                    .replace("{{toolDescription}}", safe(tool.getToolDescription()))
                    .replace("{{inputSchema}}", toJson(tool.getInputSchema()))
                    .replace("{{globalSlots}}", toJson(defaultGlobalSlots()));
            JsonNode cleaned = callLlmForJson(prompt);
            String description = safe(cleaned.path("description").asText(""));
            List<InputSlot> slots = parseInputSlotsNode(cleaned.path("inputSlots"), tool);
            return new LlmCleanResult(description, slots, List.of(), false);
        } catch (Exception ex) {
            if (isRetryable(ex)) {
                return new LlmCleanResult("", List.of(), List.of(), true);
            }
            return new LlmCleanResult(safe(tool.getToolDescription()), List.of(), List.of(), false);
        }
    }

    private LlmCleanResult cleanSkillDescriptionWithLlm(McpSkill skill) {
        try {
            List<Map<String, String>> tools = new ArrayList<>();
            for (McpTool t : safeTools(skill)) {
                Map<String, String> one = new LinkedHashMap<>();
                one.put("toolName", safe(t.getToolName()));
                one.put("toolDescription", safe(t.getToolDescription()));
                tools.add(one);
            }
            String promptTemplate = readPromptTemplate(properties.getCleanSkillPromptPath());
            String prompt = promptTemplate
                    .replace("{{skillName}}", safe(skill.getSkillName()))
                    .replace("{{skillDescription}}", safe(skill.getSkillDescription()))
                    .replace("{{tools}}", toJson(tools))
                    .replace("{{globalIntents}}", toJson(defaultGlobalSkillTags()));
            JsonNode cleaned = callLlmForJson(prompt);
            String description = safe(cleaned.path("description").asText(""));
            String intent = canonicalizeSkillTag(cleaned.path("intent").asText(""));
            if (intent.isBlank()) {
                List<String> tags = parseTagsNode(cleaned.path("tags"));
                if (!tags.isEmpty()) {
                    intent = tags.get(0);
                }
            }
            if (intent.isBlank()) {
                intent = canonicalizeSkillTag(skill.getIntent());
            }
            if (intent.isBlank()) {
                List<String> tags = normalizePrimaryTags(skill.getTags());
                if (!tags.isEmpty()) {
                    intent = tags.get(0);
                }
            }
            if (intent.isBlank()) {
                intent = "utility_tools";
            }
            return new LlmCleanResult(description, List.of(), List.of(intent), false);
        } catch (Exception ex) {
            if (isRetryable(ex)) {
                return new LlmCleanResult("", List.of(), List.of(), true);
            }
            String intent = canonicalizeSkillTag(skill.getIntent());
            if (intent.isBlank()) {
                List<String> tags = normalizePrimaryTags(skill.getTags());
                if (!tags.isEmpty()) {
                    intent = tags.get(0);
                }
            }
            if (intent.isBlank()) {
                intent = "utility_tools";
            }
            return new LlmCleanResult(safe(skill.getSkillDescription()), List.of(), List.of(intent), false);
        }
    }

    private String callLlmForDescription(String userPrompt) throws Exception {
        JsonNode node = callLlmForJson(userPrompt);
        return safe(node.path("description").asText(""));
    }

    private JsonNode callLlmForJson(String userPrompt) throws Exception {
        ObjectNode req = objectMapper.createObjectNode();
        req.put("model", safe(properties.getModel()).isBlank() ? "glm-4-flash" : safe(properties.getModel()));
        req.put("stream", false);
        req.put("temperature", 0.1);

        ArrayNode messages = objectMapper.createArrayNode();
        ObjectNode system = objectMapper.createObjectNode();
        system.put("role", "system");
        system.put("content", readPromptTemplate(properties.getCleanSystemPromptPath()));
        ObjectNode user = objectMapper.createObjectNode();
        user.put("role", "user");
        user.put("content", userPrompt);
        messages.add(system);
        messages.add(user);
        req.set("messages", messages);

        String responseBody = httpRequestClient.post(
                llmHttpClientRouter.getClient(properties.getModel()),
                ZhipuHttpProtocol.endpoint(properties.getBaseUrl(), ZhipuHttpProtocol.CHAT_COMPLETIONS_PATH),
                objectMapper.writeValueAsString(req),
                ZhipuHttpProtocol.jsonHeaders(properties.getApiKey())
        );
        JsonNode root = objectMapper.readTree(responseBody);
        String content = root.path("choices").path(0).path("message").path("content").asText("");
        return extractJsonObject(content);
    }

    private JsonNode extractJsonObject(String content) {
        String text = safe(content);
        if (text.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(text);
        } catch (Exception ignored) {
        }
        Matcher m = JSON_BLOCK.matcher(text);
        if (m.find()) {
            try {
                return objectMapper.readTree(m.group(1));
            } catch (Exception ignored) {
            }
        }
        ObjectNode fallback = objectMapper.createObjectNode();
        int nl = text.indexOf('\n');
        fallback.put("description", nl > 0 ? text.substring(0, nl) : text);
        return fallback;
    }

    private boolean isRetryable(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof ExecutorSaturatedException || current instanceof RejectedExecutionException) {
                return true;
            }
            String msg = safe(current.getMessage()).toLowerCase();
            if (msg.contains("http post request failed")
                    || msg.contains("http request failed with status")
                    || msg.contains("status:")) {
                return true;
            }
            if (msg.contains("timeout")
                    || msg.contains("timed out")
                    || msg.contains("429")
                    || msg.contains("too many")
                    || msg.contains("rate limit")
                    || msg.contains("503")
                    || msg.contains("502")
                    || msg.contains("connection reset")
                    || msg.contains("connection refused")
                    || msg.contains("temporarily")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private List<McpTool> safeTools(McpSkill skill) {
        return skill.getTools() == null ? List.of() : skill.getTools();
    }

    private List<InputSlot> parseInputSlotsNode(JsonNode node, McpTool tool) {
        if (node == null || !node.isArray()) {
            return fallbackSlotsFromInputSchema(tool);
        }
        List<InputSlot> out = new ArrayList<>();
        for (JsonNode item : node) {
            if (item == null || !item.isObject()) {
                continue;
            }
            String field = safe(item.path("field").asText(""));
            if (field.isBlank()) {
                field = safe(item.path("fieldPath").asText(""));
            }
            if (field.isBlank()) {
                continue;
            }
            String slotKey = normalizeSlotKey(safe(item.path("slotKey").asText("")));
            if (slotKey.isBlank()) {
                slotKey = normalizeSlotKey(field);
            }
            String type = safe(item.path("type").asText(""));
            if (type.isBlank()) {
                type = safe(item.path("fieldType").asText(""));
            }
            if (type.isBlank()) {
                type = "string";
            }
            boolean required = item.path("required").asBoolean(false);
            InputSlot slot = new InputSlot();
            slot.setFieldPath(field);
            slot.setSlotKey(slotKey);
            slot.setFieldType(type.toLowerCase());
            slot.setRequired(required);
            out.add(slot);
        }
        return out.isEmpty() ? fallbackSlotsFromInputSchema(tool) : out;
    }

    private List<InputSlot> parseInputSlotsJson(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(raw, new TypeReference<List<InputSlot>>() {
            });
        } catch (Exception e) {
            return List.of();
        }
    }

    private List<String> parseTagsNode(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (JsonNode item : node) {
            String tag = safe(item == null ? "" : item.asText(""));
            if (!tag.isBlank()) {
                out.add(tag);
            }
        }
        return normalizePrimaryTags(out);
    }

    private List<String> parseTagsJson(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        try {
            List<String> tags = objectMapper.readValue(raw, new TypeReference<List<String>>() {
            });
            return normalizePrimaryTags(tags);
        } catch (Exception e) {
            return List.of();
        }
    }

    private List<String> normalizePrimaryTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return List.of();
        }
        String chosen = "";
        for (String one : tags) {
            String tag = safe(one).toLowerCase();
            if (tag.isBlank()) {
                continue;
            }
            String[] parts = tag.split("[/:|]");
            String primary = parts.length == 0 ? tag : safe(parts[0]).toLowerCase();
            if (primary.isBlank()) {
                continue;
            }
            if (isActionTag(primary)) {
                continue;
            }
            String canonical = canonicalizeSkillTag(primary);
            if (canonical.isBlank()) {
                continue;
            }
            chosen = canonical;
            break;
        }
        return chosen.isBlank() ? List.of() : List.of(chosen);
    }

    private boolean isActionTag(String tag) {
        return "read".equals(tag)
                || "write".equals(tag)
                || "delete".equals(tag)
                || "update".equals(tag)
                || "create".equals(tag);
    }

    private String canonicalizeSkillTag(String raw) {
        String tag = safe(raw).toLowerCase();
        if (tag.isBlank()) {
            return "";
        }
        if (defaultGlobalSkillTags().contains(tag)) {
            return tag;
        }
        return switch (tag) {
            case "query", "search", "retrieval" -> "knowledge_retrieval";
            case "image", "vision" -> "vision_image";
            case "map", "geo", "navigation" -> "geo_navigation";
            case "travel", "trip", "aviation", "flight" -> "travel_transport";
            case "policy", "compliance", "regulation" -> "policy_compliance";
            case "calendar", "time", "date", "holiday" -> "time_calendar";
            case "finance", "price", "exchange", "market" -> "finance_market";
            case "delivery", "logistics", "express" -> "logistics_delivery";
            case "identity", "business", "tax" -> "identity_business";
            case "lifestyle", "entertainment", "fortune", "lottery" -> "lifestyle_entertainment";
            case "reasoning", "planning", "thinking" -> "reasoning_planning";
            case "product", "barcode", "lookup" -> "product_lookup";
            case "tool", "tools", "utility" -> "utility_tools";
            default -> "";
        };
    }

    private List<InputSlot> fallbackSlotsFromInputSchema(McpTool tool) {
        Map<String, Object> schema = tool.getInputSchema();
        if (schema == null || schema.isEmpty()) {
            return List.of();
        }
        Object propsObj = schema.get("properties");
        if (!(propsObj instanceof Map<?, ?> props)) {
            return List.of();
        }
        List<String> requiredFields = new ArrayList<>();
        Object requiredObj = schema.get("required");
        if (requiredObj instanceof List<?> reqList) {
            for (Object one : reqList) {
                requiredFields.add(safe(one == null ? "" : String.valueOf(one)));
            }
        }
        List<InputSlot> out = new ArrayList<>();
        for (Map.Entry<?, ?> entry : props.entrySet()) {
            String field = safe(entry.getKey() == null ? "" : String.valueOf(entry.getKey()));
            if (field.isBlank()) {
                continue;
            }
            String type = "string";
            Object propDef = entry.getValue();
            if (propDef instanceof Map<?, ?> propMap) {
                Object typeObj = propMap.get("type");
                if (typeObj != null) {
                    String rawType = safe(String.valueOf(typeObj));
                    if (!rawType.isBlank()) {
                        type = rawType.toLowerCase();
                    }
                }
            }
            InputSlot slot = new InputSlot();
            slot.setFieldPath(field);
            slot.setSlotKey(normalizeSlotKey(field));
            slot.setFieldType(type);
            slot.setRequired(requiredFields.contains(field));
            out.add(slot);
        }
        return out;
    }

    private String normalizeSlotKey(String raw) {
        String s = safe(raw).toLowerCase();
        if (s.isBlank()) {
            return "";
        }
        s = s.replaceAll("[^a-z0-9]+", "_");
        s = s.replaceAll("_+", "_");
        s = s.replaceAll("^_|_$", "");
        return s;
    }

    private List<Map<String, String>> defaultGlobalSlots() {
        List<Map<String, String>> slots = new ArrayList<>();
        slots.add(slotDef("destination", "目的地"));
        slots.add(slotDef("origin", "出发地"));
        slots.add(slotDef("travel_date", "出发日期"));
        slots.add(slotDef("return_date", "返程日期"));
        slots.add(slotDef("datetime", "时间点"));
        slots.add(slotDef("location", "地点"));
        slots.add(slotDef("keyword", "检索关键词"));
        slots.add(slotDef("query", "查询语句"));
        slots.add(slotDef("page", "页码"));
        slots.add(slotDef("page_size", "分页大小"));
        slots.add(slotDef("image_url", "图片地址"));
        slots.add(slotDef("text", "文本内容"));
        return slots;
    }

    private List<String> defaultGlobalSkillTags() {
        return List.of(
                "vision_image",
                "geo_navigation",
                "travel_transport",
                "policy_compliance",
                "knowledge_retrieval",
                "time_calendar",
                "finance_market",
                "logistics_delivery",
                "identity_business",
                "lifestyle_entertainment",
                "utility_tools",
                "reasoning_planning",
                "product_lookup"
        );
    }

    private Map<String, String> slotDef(String slotKey, String meaning) {
        Map<String, String> one = new LinkedHashMap<>();
        one.put("slotKey", slotKey);
        one.put("meaning", meaning);
        return one;
    }

    private McpSkill findSkill(List<McpSkill> skills, String skillName) {
        for (McpSkill s : skills) {
            if (safe(s.getSkillName()).equals(safe(skillName))) {
                return s;
            }
        }
        return null;
    }

    private List<McpSkill> loadBaseSummaryForCleaning(Path summaryPath, List<McpSkill> exportSkills) {
        List<McpSkill> baseSkills;
        if (Files.exists(summaryPath)) {
            try {
                baseSkills = readSummary(summaryPath);
            } catch (Exception e) {
                baseSkills = new ArrayList<>();
            }
        } else {
            baseSkills = new ArrayList<>();
        }

        if (baseSkills.isEmpty()) {
            normalizeSkillTagsInPlace(exportSkills);
            return exportSkills;
        }
        normalizeSkillTagsInPlace(baseSkills);
        mergeExportInfoIntoBase(baseSkills, exportSkills);
        normalizeSkillTagsInPlace(baseSkills);
        return baseSkills;
    }

    private void normalizeSkillTagsInPlace(List<McpSkill> skills) {
        for (McpSkill skill : skills) {
            String intent = canonicalizeSkillTag(skill.getIntent());
            List<String> tags = normalizePrimaryTags(skill.getTags());
            if (intent.isBlank() && !tags.isEmpty()) {
                intent = tags.get(0);
            }
            if (intent.isBlank()) {
                intent = "utility_tools";
            }
            skill.setIntent(intent);
            skill.setTags(List.of(intent));
            skill.setActionType(null);
        }
    }

    private void mergeExportInfoIntoBase(List<McpSkill> baseSkills, List<McpSkill> exportSkills) {
        Map<String, McpSkill> baseByName = new LinkedHashMap<>();
        for (McpSkill skill : baseSkills) {
            baseByName.put(safe(skill.getSkillName()), skill);
        }

        for (McpSkill exportSkill : exportSkills) {
            String skillName = safe(exportSkill.getSkillName());
            if (skillName.isBlank()) {
                continue;
            }
            McpSkill baseSkill = baseByName.get(skillName);
            if (baseSkill == null) {
                baseSkills.add(exportSkill);
                baseByName.put(skillName, exportSkill);
                continue;
            }

            if (safe(baseSkill.getServerUrl()).isBlank()) {
                baseSkill.setServerUrl(safe(exportSkill.getServerUrl()));
            }
            if (safe(baseSkill.getSkillDescription()).isBlank()) {
                baseSkill.setSkillDescription(safe(exportSkill.getSkillDescription()));
            }

            List<McpTool> baseTools = baseSkill.getTools();
            if (baseTools == null || baseTools.isEmpty()) {
                baseSkill.setTools(exportSkill.getTools());
                continue;
            }

            Map<String, McpTool> baseToolsByName = new LinkedHashMap<>();
            for (McpTool tool : baseTools) {
                baseToolsByName.put(safe(tool.getToolName()), tool);
            }
            for (McpTool exportTool : safeTools(exportSkill)) {
                String toolName = safe(exportTool.getToolName());
                if (toolName.isBlank()) {
                    continue;
                }
                McpTool baseTool = baseToolsByName.get(toolName);
                if (baseTool == null) {
                    baseTools.add(exportTool);
                    baseToolsByName.put(toolName, exportTool);
                    continue;
                }
                if (safe(baseTool.getToolDescription()).isBlank()) {
                    baseTool.setToolDescription(safe(exportTool.getToolDescription()));
                }
                if (baseTool.getInputSchema() == null || baseTool.getInputSchema().isEmpty()) {
                    baseTool.setInputSchema(exportTool.getInputSchema());
                }
                if (safe(baseTool.getServerUrl()).isBlank()) {
                    baseTool.setServerUrl(safe(exportTool.getServerUrl()));
                }
                if (safe(baseTool.getSkillName()).isBlank()) {
                    baseTool.setSkillName(safe(baseSkill.getSkillName()));
                }
            }
        }
    }

    private List<McpSkill> buildSummaryFromExport(Path exportJsonPath) {
        if (!Files.exists(exportJsonPath)) {
            throw new IllegalStateException("export json not found: " + exportJsonPath);
        }
        try {
            String raw = Files.readString(exportJsonPath, StandardCharsets.UTF_8);
            JsonNode root = objectMapper.readTree(raw);
            JsonNode results = root.path("results");
            if (!results.isArray()) {
                throw new IllegalStateException("invalid export json: missing results array");
            }

            List<McpSkill> skills = new ArrayList<>();
            for (JsonNode r : results) {
                if (!r.path("success").asBoolean(false)) {
                    continue;
                }
                String skillName = safe(r.path("serverLabel").asText(""));
                if (skillName.isBlank()) {
                    continue;
                }
                McpSkill skill = new McpSkill();
                skill.setSkillName(skillName);
                skill.setSkillDescription(skillName + "服务提供的工具能力。");
                skill.setServerUrl(safe(r.path("serverUrl").asText("")));

                List<McpTool> tools = new ArrayList<>();
                JsonNode toolArr = r.path("tools");
                if (toolArr.isArray()) {
                    for (JsonNode t : toolArr) {
                        String toolName = safe(t.path("name").asText(""));
                        if (toolName.isBlank()) {
                            continue;
                        }
                        McpTool tool = new McpTool();
                        tool.setSkillName(skillName);
                        tool.setToolName(toolName);
                        tool.setToolDescription(safe(t.path("description").asText("")));
                        JsonNode inputSchema = t.path("inputSchema");
                        if (inputSchema.isObject()) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> schema = objectMapper.convertValue(inputSchema, Map.class);
                            tool.setInputSchema(schema);
                        } else if (inputSchema.isTextual()) {
                            String rawSchema = safe(inputSchema.asText(""));
                            if (!rawSchema.isBlank()) {
                                try {
                                    @SuppressWarnings("unchecked")
                                    Map<String, Object> schema = objectMapper.readValue(rawSchema, Map.class);
                                    tool.setInputSchema(schema);
                                } catch (Exception ignored) {
                                }
                            }
                        }
                        tool.setServerUrl(skill.getServerUrl());
                        tools.add(tool);
                    }
                }
                skill.setTools(tools);
                skills.add(skill);
            }
            return skills;
        } catch (IOException e) {
            throw new IllegalStateException("read export json failed", e);
        }
    }

    private List<McpSkill> readSummary(Path summaryPath) {
        try {
            String raw = Files.readString(summaryPath, StandardCharsets.UTF_8);
            return objectMapper.readValue(raw, new TypeReference<List<McpSkill>>() {
            });
        } catch (IOException e) {
            throw new IllegalStateException("read summary failed", e);
        }
    }

    private void writeSummaryWithBackup(Path summaryPath, List<McpSkill> skills, String taskId) {
        try {
            if (summaryPath.getParent() != null) {
                Files.createDirectories(summaryPath.getParent());
            }
            if (isBackupEnabled() && Files.exists(summaryPath) && shouldCreateBackupForTask(summaryPath, taskId)) {
                Path backupPath = summaryPath.resolveSibling(
                        summaryPath.getFileName() + ".bck." + backupTaskSuffix(taskId) + "." + LocalDateTime.now().format(BCK_FMT)
                );
                Files.copy(summaryPath, backupPath, StandardCopyOption.REPLACE_EXISTING);
                pruneBackups(summaryPath);
            }
            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(skills);
            Files.writeString(summaryPath, json, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("write summary failed", e);
        }
    }

    private boolean shouldCreateBackupForTask(Path summaryPath, String taskId) throws IOException {
        if (taskId == null || taskId.isBlank()) {
            return true;
        }
        Path dir = summaryPath.getParent();
        if (dir == null || !Files.exists(dir)) {
            return true;
        }
        String marker = summaryPath.getFileName() + ".bck." + backupTaskSuffix(taskId) + ".";
        try (var stream = Files.list(dir)) {
            return stream.noneMatch(p -> p.getFileName().toString().startsWith(marker));
        }
    }

    private String backupTaskSuffix(String taskId) {
        return (taskId == null || taskId.isBlank()) ? "adhoc" : taskId;
    }

    private void pruneBackups(Path summaryPath) throws IOException {
        int maxBackups = properties.getSummaryBackupMaxFiles();
        if (maxBackups <= 0) {
            return;
        }
        Path dir = summaryPath.getParent();
        if (dir == null || !Files.exists(dir)) {
            return;
        }
        String prefix = summaryPath.getFileName() + ".bck.";
        List<Path> backups;
        try (var stream = Files.list(dir)) {
            backups = stream
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().startsWith(prefix))
                    .sorted((a, b) -> Long.compare(lastModified(b), lastModified(a)))
                    .toList();
        }
        for (int i = maxBackups; i < backups.size(); i++) {
            try {
                Files.deleteIfExists(backups.get(i));
            } catch (Exception ignored) {
            }
        }
    }

    private long lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private boolean isBackupEnabled() {
        return properties.getSummaryBackupMaxFiles() > 0;
    }

    private String normalizeDesc(String raw) {
        String out = safe(raw);
        while (out.endsWith(".") || out.endsWith(";") || out.endsWith("!")) {
            out = out.substring(0, out.length() - 1).trim();
        }
        if (out.length() > DESC_MAX_LEN) {
            out = out.substring(0, DESC_MAX_LEN);
        }
        return out.isBlank() ? "" : out + "。";
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "{}";
        }
    }

    private String readPromptTemplate(String path) {
        Path p = resolvePath(path);
        try {
            return Files.readString(p, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("read prompt failed: " + p, e);
        }
    }

    private String safe(String s) {
        return s == null ? "" : s.trim();
    }

    private Path resolvePath(String configuredPath) {
        String raw = configuredPath == null ? "" : configuredPath.trim();
        if (raw.isBlank()) {
            throw new IllegalArgumentException("path is blank");
        }
        Path p = Path.of(raw);
        if (p.isAbsolute()) {
            return p.normalize();
        }
        Path cwd = Path.of(System.getProperty("user.dir")).normalize();
        String rel = raw.replace("\\", "/");
        if (rel.startsWith("./")) {
            rel = rel.substring(2);
        }
        List<Path> candidates = new ArrayList<>();
        candidates.add(cwd.resolve(p).normalize());
        if (rel.startsWith("AgentEngine/")) {
            candidates.add(cwd.resolve(rel.substring("AgentEngine/".length())).normalize());
        } else {
            candidates.add(cwd.resolve("AgentEngine").resolve(rel).normalize());
        }
        candidates.add(cwd.resolve("..").resolve(p).normalize());
        if (!rel.startsWith("AgentEngine/")) {
            candidates.add(cwd.resolve("..").resolve("AgentEngine").resolve(rel).normalize());
        }
        for (Path one : candidates) {
            if (Files.exists(one)) {
                return one;
            }
        }
        for (Path one : candidates) {
            Path parent = one.getParent();
            if (parent != null && Files.exists(parent)) {
                return one;
            }
        }
        return candidates.get(0);
    }

    public record TaskProcessResult(List<String> retryToolNames, boolean retrySkill) {
        static TaskProcessResult done() {
            return new TaskProcessResult(List.of(), false);
        }
    }

    private record LlmCleanResult(String description, List<InputSlot> inputSlots, List<String> tags, boolean retryable) {
    }
}
