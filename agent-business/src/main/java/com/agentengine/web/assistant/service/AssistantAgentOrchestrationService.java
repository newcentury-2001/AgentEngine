package com.agentengine.web.assistant.service;

import com.agentcommon.mcp.model.InputSlot;
import com.agentengine.web.assistant.model.AssistantAgentProcessRequest;
import com.agentengine.web.assistant.model.AssistantExecutionPlan;
import com.agentengine.web.assistant.model.AssistantPlannedTool;
import com.agentengine.web.assistant.model.AssistantStateStartRequest;
import com.agentengine.web.assistant.model.AssistantStateTransitionRequest;
import com.agentengine.web.assistant.model.AssistantUserState;
import com.agentengine.web.assistant.model.IntentCandidate;
import com.agentengine.web.assistant.model.LlmAgentState;
import com.agentengine.web.assistant.service.retrieval.AssistantRetrievalRepository;
import com.agentengine.web.assistant.service.retrieval.SkillVectorRecord;
import com.agentengine.web.assistant.service.stage.AssistantStage;
import com.agentengine.web.assistant.websocket.AssistantTaskWebSocketBroadcaster;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class AssistantAgentOrchestrationService {

    private final AssistantStateMachineService stateMachineService;
    private final AssistantDialogueService assistantDialogueService;
    private final AssistantEntityMemoryService assistantEntityMemoryService;
    private final AssistantPlanService assistantPlanService;
    private final AssistantInferenceService assistantInferenceService;
    private final AssistantRetrievalRepository assistantRetrievalRepository;
    private final AssistantTaskWebSocketBroadcaster taskWebSocketBroadcaster;

    @Value("${agent.assistant.intent.confidence-threshold:0.35}")
    private double confidenceThreshold;

    @Value("${agent.assistant.intent.delta-threshold:0.03}")
    private double deltaThreshold;

    @Value("${agent.assistant.intent.min-sim-threshold:0.25}")
    private double minSimThreshold;

    @Value("${agent.assistant.active.max-turns:6}")
    private int maxActiveTurns;

    @Value("${agent.assistant.intent.ambiguous-template:I think your request may be \"%s\" or \"%s\". Which one do you prefer?}")
    private String ambiguousTemplate;

    /**
     * Main chain entry.
     * IDLE: intent/skill/tool planning.
     * ACTIVE: slot filling + tool execution + final answer.
     */
    public AssistantUserState execute(AssistantAgentProcessRequest request) {
        AssistantUserState current = resolveState(request);
        request.setTaskId(current.getTaskId());

        publish("TASK_STATE_CHANGED", current, "task accepted");
        stateMachineService.touch(current);
        assistantPlanService.touch(current.getTaskId());
        assistantDialogueService.touch(current.getTaskId());
        assistantEntityMemoryService.touch(current.getTaskId());

        if (blank(current.getIntent())) {
            AssistantUserState afterIdle = executeIdle(current, request);
            if (afterIdle.getState() == LlmAgentState.ACTIVE) {
                AssistantAgentProcessRequest nextReq = new AssistantAgentProcessRequest();
                nextReq.setUserId(request.getUserId());
                nextReq.setTaskId(afterIdle.getTaskId());
                nextReq.setTraceId(request.getTraceId());
                nextReq.setMessage("");
                return executeActive(afterIdle, nextReq);
            }
            return afterIdle;
        }
        return executeActive(current, request);
    }

    private AssistantUserState executeIdle(AssistantUserState current, AssistantAgentProcessRequest request) {
        String message = text(request.getMessage());
        String selectedIntent = text(request.getSelectedIntent());
        String context = assistantDialogueService.buildThreeTurnContext(current.getTaskId(), message);

        if (!blank(message)) {
            assistantDialogueService.appendUserMessage(current.getTaskId(), message);
        }

        IntentVoteResult vote = blank(selectedIntent) ? voteIntent(context) : IntentVoteResult.resolved(selectedIntent);
        if (!vote.resolved()) {
            AssistantUserState next = transition(current, LlmAgentState.IDLE, t -> {
                t.setIntent("");
                t.setSkillName("");
                t.setMissingSlots(List.of());
                t.setErrorMessage("");
                t.setNeedClarification(true);
                t.setClarificationType(vote.clarificationType());
                t.setClarificationQuestion(vote.question());
                t.setIntentCandidatesTop3(vote.candidatesTop3());
                t.setAssistantReply(vote.question());
                t.setActiveTurnCount(0);
            });
            publish("TASK_NEED_CLARIFICATION", next, vote.question());
            return next;
        }

        String intent = vote.intent();
        double[] queryVector = assistantInferenceService.embedQuery(context);
        List<ScoredSkill> topSkills = retrieveTopSkillsByIntent(queryVector, intent);
        if (topSkills.isEmpty()) {
            AssistantUserState next = transition(current, LlmAgentState.IDLE, t -> {
                t.setErrorMessage("no skill candidates for intent=" + intent);
                t.setNeedClarification(true);
                t.setClarificationType("LOW_CONFIDENCE");
                t.setClarificationQuestion("I still cannot determine your goal. Please provide one more sentence.");
                t.setAssistantReply("I still cannot determine your goal. Please provide one more sentence.");
            });
            publish("TASK_NEED_CLARIFICATION", next, "low confidence");
            return next;
        }

        List<SkillVectorRecord> skillCandidates = topSkills.stream().map(ScoredSkill::skill).toList();
        String bestSkill = assistantInferenceService.rerankBestSkill(context, intent, skillCandidates);
        if (blank(bestSkill)) {
            bestSkill = topSkills.get(0).skill().getSkillName();
        }
        final String finalBestSkill = bestSkill;

        List<AssistantPlannedTool> toolCandidates = retrieveTopTools(finalBestSkill, queryVector);
        if (toolCandidates.isEmpty()) {
            AssistantUserState next = transition(current, LlmAgentState.IDLE, t -> {
                t.setErrorMessage("no tools for skill=" + finalBestSkill);
                t.setAssistantReply("No executable tools were found for the selected skill. Please rephrase your request.");
                t.setNeedClarification(true);
                t.setClarificationType("LOW_CONFIDENCE");
                t.setClarificationQuestion("No executable tools were found for the selected skill. Please rephrase your request.");
            });
            publish("TASK_NEED_CLARIFICATION", next, "no tools for skill");
            return next;
        }

        List<String> selectedToolNames = assistantInferenceService.selectTools(context, intent, finalBestSkill, toolCandidates);
        List<AssistantPlannedTool> selectedTools = toolCandidates.stream()
                .filter(t -> selectedToolNames.contains(t.getToolName()))
                .toList();
        if (selectedTools.isEmpty()) {
            selectedTools = List.of(toolCandidates.get(0));
        }

        Set<String> slotScope = new LinkedHashSet<>();
        Set<String> requiredSlots = new LinkedHashSet<>();
        for (AssistantPlannedTool tool : selectedTools) {
            if (tool.getRequiredSlots() != null) {
                requiredSlots.addAll(tool.getRequiredSlots());
                slotScope.addAll(tool.getRequiredSlots());
            }
            if (tool.getOptionalSlots() != null) {
                slotScope.addAll(tool.getOptionalSlots());
            }
        }

        Map<String, String> entities = assistantEntityMemoryService.getAll(current.getTaskId());
        List<String> missingSlots = computeMissingFromRequired(requiredSlots, entities);

        // 在切入 ACTIVE 前先用当前用户原话做一次预抽槽，避免“首轮已给参数却未入槽”。
        if (!blank(message) && !missingSlots.isEmpty()) {
            var extraction = assistantInferenceService.inferForSlotFill(AssistantStage.SLOT_CLARIFICATION, missingSlots, message);
            assistantEntityMemoryService.merge(current.getTaskId(), extraction.getEntityMemory());
            entities = assistantEntityMemoryService.getAll(current.getTaskId());
            missingSlots = computeMissingFromRequired(requiredSlots, entities);
        }

        AssistantExecutionPlan plan = AssistantExecutionPlan.builder()
                .taskId(current.getTaskId())
                .userId(current.getUserId())
                .intent(intent)
                .skillName(finalBestSkill)
                .selectedTools(new ArrayList<>(selectedTools))
                .pendingTools(new ArrayList<>(selectedTools))
                .executedTools(new ArrayList<>())
                .slotScope(slotScope)
                .missingSlots(new ArrayList<>(missingSlots))
                .toolOutputSummaries(new LinkedHashMap<>())
                .build();
        assistantPlanService.save(plan);

        final List<String> finalMissingSlots = missingSlots;
        AssistantUserState next = transition(current, LlmAgentState.ACTIVE, t -> {
            t.setIntent(intent);
            t.setSkillName(finalBestSkill);
            t.setMissingSlots(finalMissingSlots);
            t.setErrorMessage("");
            t.setNeedClarification(false);
            t.setClarificationType("");
            t.setClarificationQuestion("");
            t.setIntentCandidatesTop3(List.of());
            t.setAssistantReply("Execution plan created, processing now.");
            t.setActiveTurnCount(0);
        });
        publish("TASK_STATE_CHANGED", next, "enter active");
        return next;
    }

    private AssistantUserState executeActive(AssistantUserState current, AssistantAgentProcessRequest request) {
        AssistantExecutionPlan plan = assistantPlanService.findByTaskId(current.getTaskId()).orElse(null);
        if (plan == null) {
            AssistantUserState next = transition(current, LlmAgentState.IDLE, t -> {
                t.setIntent("");
                t.setSkillName("");
                t.setMissingSlots(List.of());
                t.setErrorMessage("execution plan missing");
                t.setAssistantReply("Execution plan expired. Please describe your request again.");
                t.setNeedClarification(false);
                t.setClarificationType("");
                t.setClarificationQuestion("");
                t.setIntentCandidatesTop3(List.of());
                t.setActiveTurnCount(0);
            });
            publish("TASK_FAILED", next, "execution plan missing");
            return next;
        }

        String message = text(request.getMessage());
        String context = assistantDialogueService.buildThreeTurnContext(current.getTaskId(), message);
        if (!blank(message)) {
            AssistantInferenceService.IntentDriftDecision drift = assistantInferenceService.detectIntentDriftInActive(
                    plan.getIntent(),
                    plan.getSkillName(),
                    plan.getMissingSlots(),
                    plan.getPendingTools(),
                    context,
                    message
            );
            if (drift.intentDrift()) {
                cleanupAfterFinish(current.getTaskId());
                AssistantUserState reset = transition(current, LlmAgentState.IDLE, t -> {
                    t.setIntent("");
                    t.setSkillName("");
                    t.setMissingSlots(List.of());
                    t.setErrorMessage("");
                    t.setNeedClarification(false);
                    t.setClarificationType("");
                    t.setClarificationQuestion("");
                    t.setIntentCandidatesTop3(List.of());
                    t.setAssistantReply("Detected intent switch, replanning now.");
                    t.setActiveTurnCount(0);
                });
                publish("TASK_STATE_CHANGED", reset, "intent drift detected");
                AssistantUserState afterIdle = executeIdle(reset, request);
                if (afterIdle.getState() == LlmAgentState.ACTIVE) {
                    AssistantAgentProcessRequest nextReq = new AssistantAgentProcessRequest();
                    nextReq.setUserId(request.getUserId());
                    nextReq.setTaskId(afterIdle.getTaskId());
                    nextReq.setTraceId(request.getTraceId());
                    nextReq.setMessage("");
                    return executeActive(afterIdle, nextReq);
                }
                return afterIdle;
            }
            assistantDialogueService.appendUserMessage(current.getTaskId(), message);
        }

        if (!blank(message) && plan.getMissingSlots() != null && !plan.getMissingSlots().isEmpty()) {
            var extraction = assistantInferenceService.inferForSlotFill(AssistantStage.SLOT_CLARIFICATION, plan.getMissingSlots(), message);
            assistantEntityMemoryService.merge(current.getTaskId(), extraction.getEntityMemory());
        }

        Map<String, String> entities = assistantEntityMemoryService.getAll(current.getTaskId());
        List<AssistantPlannedTool> pending = plan.getPendingTools() == null ? new ArrayList<>() : new ArrayList<>(plan.getPendingTools());
        List<AssistantPlannedTool> executed = plan.getExecutedTools() == null ? new ArrayList<>() : new ArrayList<>(plan.getExecutedTools());
        Map<String, String> summaries = plan.getToolOutputSummaries() == null ? new LinkedHashMap<>() : new LinkedHashMap<>(plan.getToolOutputSummaries());
        LinkedHashSet<String> dynamicMissingSlots = new LinkedHashSet<>(plan.getMissingSlots() == null ? List.of() : plan.getMissingSlots());

        List<AssistantPlannedTool> executedThisTurn = new ArrayList<>();
        List<String> retryQueuedThisTurn = new ArrayList<>();
        List<String> fastFailedThisTurn = new ArrayList<>();
        while (true) {
            // 每轮循环都先从 Redis 读取最新实体，避免依赖本地旧快照。
            entities = assistantEntityMemoryService.getAll(current.getTaskId());
            // 1) 基于当前实体内存，筛出“硬必填槽位已满足”的可执行工具。
            List<AssistantPlannedTool> executable = new ArrayList<>();
            for (AssistantPlannedTool tool : pending) {
                if (allRequiredSlotsReady(tool, entities)) {
                    executable.add(tool);
                }
            }
            // 没有可执行工具就退出循环，后续进入缺槽追问或结束态判断。
            if (executable.isEmpty()) {
                break;
            }

            boolean progressed = false;
            for (AssistantPlannedTool tool : executable) {
                // 2) 为当前工具准备“全量 slotKeyScope”（覆盖 hard/conditional/optional）。
                List<String> slotKeyScope = resolveToolSlotScope(tool);

                // 3) 调用工具（用当前 entities 组装参数发起执行）。
                AssistantInferenceService.ToolExecutionResult toolExec = assistantInferenceService.executePlannedTool(
                        current.getTaskId(),
                        plan.getIntent(),
                        plan.getSkillName(),
                        tool,
                        entities
                );
                if (toolExec.status() == AssistantInferenceService.ToolExecutionStatus.RETRY_SCHEDULED) {
                    String msg = blank(toolExec.message())
                            ? "tool delayed retry queued: " + tool.getToolName()
                            : toolExec.message() + " [" + tool.getToolName() + "]";
                    retryQueuedThisTurn.add(msg);
                    AssistantUserState running = stateMachineService.findByTaskId(current.getTaskId()).orElse(current);
                    publish("TASK_TOOL_PROGRESS", running, msg);
                    continue;
                }
                if (toolExec.status() == AssistantInferenceService.ToolExecutionStatus.FAST_FAILED) {
                    String failMsg = blank(toolExec.message())
                            ? "tool fast failed: " + tool.getToolName()
                            : toolExec.message() + " [" + tool.getToolName() + "]";
                    fastFailedThisTurn.add(failMsg);
                    summaries.put(tool.getToolName(), failMsg);
                    pending.removeIf(p -> p.getToolName().equals(tool.getToolName()));
                    progressed = true;
                    AssistantUserState running = stateMachineService.findByTaskId(current.getTaskId()).orElse(current);
                    publish("TASK_TOOL_PROGRESS", running, failMsg);
                    continue;
                }
                String rawOutput = toolExec.rawOutput();

                // 4) 对工具输出做结构化评估：
                //    - 产出精炼摘要 summary
                //    - 判断是否属于“参数不足类失败”
                //    - 给出建议补充槽位 suggestedMissingSlots
                AssistantInferenceService.ToolOutputAssessment assessment = assistantInferenceService.analyzeToolOutput(
                        plan.getIntent(),
                        plan.getSkillName(),
                        tool.getToolName(),
                        rawOutput,
                        slotKeyScope
                );
                String summary = assessment.summary();
                // 统一沉淀工具输出摘要，后续用于最终回答生成。
                summaries.put(tool.getToolName(), summary);

                // 5) 若是参数不足：不算执行成功，不移出 pending；只把建议槽位加入动态缺槽集合。
                if (assessment.parameterMissing()) {
                    for (String slot : assessment.suggestedMissingSlots()) {
                        if (!blank(slot) && blank(entities.get(slot))) {
                            dynamicMissingSlots.add(slot);
                        }
                    }
                    AssistantUserState running = stateMachineService.findByTaskId(current.getTaskId()).orElse(current);
                    publish("TASK_TOOL_PROGRESS", running, "tool needs more slots: " + tool.getToolName());
                    continue;
                }

                // 6) 工具执行成功后，尝试从“工具摘要”中二次抽槽，回填到实体内存。
                List<String> currentMissing = computeMissingSlots(pending, entities);
                Map<String, String> newEntities = assistantInferenceService.extractSlotsFromToolRawOutput(
                        plan.getIntent(),
                        plan.getSkillName(),
                        rawOutput,
                        currentMissing
                );
                assistantEntityMemoryService.merge(current.getTaskId(), newEntities);
                entities = assistantEntityMemoryService.getAll(current.getTaskId());

                // 7) 标记工具已完成：从 pending 移除，加入 executed。
                pending.removeIf(p -> p.getToolName().equals(tool.getToolName()));
                executed.add(tool);
                executedThisTurn.add(tool);
                progressed = true;
                AssistantUserState running = stateMachineService.findByTaskId(current.getTaskId()).orElse(current);
                publish("TASK_TOOL_PROGRESS", running, "executed tool: " + tool.getToolName());
            }

            // 本轮没有任何工具真正完成，避免空转死循环。
            if (!progressed) {
                break;
            }
        }

        // 结束循环前再次读取 Redis 最新实体，用于缺槽计算。
        entities = assistantEntityMemoryService.getAll(current.getTaskId());
        List<String> hardMissingSlots = computeMissingSlots(pending, entities);
        LinkedHashSet<String> mergedMissingSlots = new LinkedHashSet<>(hardMissingSlots);
        for (String slot : dynamicMissingSlots) {
            if (blank(slot) || !blank(entities.get(slot))) {
                continue;
            }
            if (isSlotInPendingTools(slot, pending)) {
                mergedMissingSlots.add(slot);
            }
        }
        List<String> missingSlots = new ArrayList<>(mergedMissingSlots);
        plan.setPendingTools(pending);
        plan.setExecutedTools(executed);
        plan.setMissingSlots(missingSlots);
        plan.setToolOutputSummaries(summaries);
        assistantPlanService.save(plan);

        int activeTurns = (current.getActiveTurnCount() == null ? 0 : current.getActiveTurnCount()) + 1;
        if (pending.isEmpty()) {
            String answer = assistantInferenceService.renderFinalAnswer(plan.getIntent(), plan.getSkillName(), summaries);
            String executedToolsText = buildExecutedToolsText(executed);
            String finalReply = executedToolsText.isBlank() ? answer : executedToolsText + "\n" + answer;
            cleanupAfterFinish(current.getTaskId());
            AssistantUserState next = transition(current, LlmAgentState.IDLE, t -> {
                t.setIntent("");
                t.setSkillName("");
                t.setMissingSlots(List.of());
                t.setErrorMessage("");
                t.setAssistantReply(finalReply);
                t.setNeedClarification(false);
                t.setClarificationType("");
                t.setClarificationQuestion("");
                t.setIntentCandidatesTop3(List.of());
                t.setActiveTurnCount(0);
            });
            publish("TASK_FINISHED", next, finalReply);
            return next;
        }

        if (activeTurns > maxActiveTurns) {
            cleanupAfterFinish(current.getTaskId());
            AssistantUserState next = transition(current, LlmAgentState.IDLE, t -> {
                t.setIntent("");
                t.setSkillName("");
                t.setMissingSlots(List.of());
                t.setErrorMessage("active turns exceeded");
                t.setAssistantReply("The interaction reached the max turn limit. Please restate your goal.");
                t.setNeedClarification(false);
                t.setClarificationType("");
                t.setClarificationQuestion("");
                t.setIntentCandidatesTop3(List.of());
                t.setActiveTurnCount(0);
            });
            publish("TASK_FAILED", next, "active turns exceeded");
            return next;
        }

        String executedPart = executedThisTurn.isEmpty()
                ? ""
                : "Executed tools: " + executedThisTurn.stream().map(AssistantPlannedTool::getToolName).collect(Collectors.joining(", ")) + ".";
        String retryPart = retryQueuedThisTurn.isEmpty()
                ? ""
                : String.join("\n", retryQueuedThisTurn);
        String failPart = fastFailedThisTurn.isEmpty()
                ? ""
                : String.join("\n", fastFailedThisTurn);
        String missingPart = missingSlots.isEmpty()
                ? ""
                : "Missing slots: " + String.join(", ", missingSlots) + ". Please provide them.";
        String reply = java.util.stream.Stream.of(executedPart, retryPart, failPart, missingPart)
                .filter(s -> s != null && !s.isBlank())
                .collect(Collectors.joining("\n"))
                .trim();

        AssistantUserState next = transition(current, LlmAgentState.ACTIVE, t -> {
            t.setIntent(plan.getIntent());
            t.setSkillName(plan.getSkillName());
            t.setMissingSlots(missingSlots);
            t.setErrorMessage("");
            t.setAssistantReply(reply);
            t.setNeedClarification(false);
            t.setClarificationType("");
            t.setClarificationQuestion("");
            t.setIntentCandidatesTop3(List.of());
            t.setActiveTurnCount(activeTurns);
        });
        if (!missingSlots.isEmpty()) {
            publish("TASK_NEED_SLOTS", next, reply);
        } else {
            publish("TASK_STATE_CHANGED", next, reply);
        }
        return next;
    }

    private IntentVoteResult voteIntent(String context) {
        double[] queryVector = assistantInferenceService.embedQuery(context);
        List<SkillVectorRecord> top10 = assistantRetrievalRepository.searchTopSkillsByVector(queryVector, 10);
        if (top10.isEmpty()) {
            return IntentVoteResult.lowConfidence(List.of(), "I still cannot determine your goal. Please provide one more sentence.");
        }

        Map<String, Double> voteMap = new LinkedHashMap<>();
        for (SkillVectorRecord skill : top10) {
            double simScore = skill.getSimScore();
            if (simScore < minSimThreshold) {
                continue;
            }
            String intent = text(skill.getIntent());
            if (blank(intent)) {
                continue;
            }
            voteMap.merge(intent, simScore, Math::max);
        }

        List<IntentCandidate> candidates = voteMap.entrySet().stream()
                .map(e -> new IntentCandidate(e.getKey(), e.getKey(), e.getValue()))
                .sorted(Comparator.comparingDouble(IntentCandidate::getScore).reversed())
                .toList();
        if (candidates.isEmpty()) {
            return IntentVoteResult.lowConfidence(List.of(), "I still cannot determine your goal. Please provide one more sentence.");
        }

        IntentCandidate top1 = candidates.get(0);
        IntentCandidate top2 = candidates.size() > 1 ? candidates.get(1) : new IntentCandidate("", "", 0D);
        double confidence = top1.getScore();
        double delta = top1.getScore() - top2.getScore();
        List<IntentCandidate> top3 = candidates.stream().limit(3).toList();

        if (confidence < confidenceThreshold) {
            return IntentVoteResult.lowConfidence(top3, "I still cannot determine your goal. Please provide one more sentence.");
        }
        if (delta < deltaThreshold && !blank(top2.getIntentLabel())) {
            String question = String.format(ambiguousTemplate, top1.getIntentLabel(), top2.getIntentLabel());
            return IntentVoteResult.ambiguous(top3, question);
        }
        return IntentVoteResult.resolved(top1.getIntentCode());
    }

    private List<ScoredSkill> retrieveTopSkillsByIntent(double[] queryVector, String intent) {
        return assistantRetrievalRepository.searchTopSkillsByVectorAndIntent(queryVector, intent, 10).stream()
                .map(skill -> new ScoredSkill(skill, skill.getSimScore()))
                .limit(10)
                .toList();
    }

    private List<AssistantPlannedTool> retrieveTopTools(String skillName, double[] queryVector) {
        return assistantRetrievalRepository.searchTopToolsBySkillAndVector(skillName, queryVector, 5).stream()
                .map(tool -> {
                    List<String> required = new ArrayList<>();
                    List<String> optional = new ArrayList<>();
                    List<InputSlot> slots = tool.getInputSlots() == null ? List.of() : tool.getInputSlots();
                    for (InputSlot slot : slots) {
                        if (slot == null || blank(slot.getSlotKey())) {
                            continue;
                        }
                        if (isBlockingRequiredSlot(slot)) {
                            required.add(slot.getSlotKey());
                        } else {
                            optional.add(slot.getSlotKey());
                        }
                    }
                    return AssistantPlannedTool.builder()
                            .toolName(tool.getToolName())
                            .toolDescription(tool.getToolDescription())
                            .serverUrl(tool.getServerUrl())
                            .toolUrl(tool.getToolUrl())
                            .inputSlots(slots)
                            .requiredSlots(required)
                            .optionalSlots(optional)
                            .simScore(tool.getSimScore())
                            .heatWeight(tool.getHeatWeight())
                            .build();
                })
                .sorted((a, b) -> Double.compare(
                        b.getSimScore() == null ? 0D : b.getSimScore(),
                        a.getSimScore() == null ? 0D : a.getSimScore()))
                .limit(5)
                .toList();
    }

    private List<String> computeMissingSlots(List<AssistantPlannedTool> pending, Map<String, String> entities) {
        Set<String> required = new LinkedHashSet<>();
        if (pending != null) {
            for (AssistantPlannedTool tool : pending) {
                required.addAll(effectiveRequiredSlots(tool));
            }
        }
        return required.stream().filter(slot -> blank(entities.get(slot))).toList();
    }

    private List<String> computeMissingFromRequired(Set<String> requiredSlots, Map<String, String> entities) {
        if (requiredSlots == null || requiredSlots.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String slot : requiredSlots) {
            if (!blank(slot) && blank(entities.get(slot))) {
                out.add(slot);
            }
        }
        return out;
    }

    /**
     * 判断工具是否可执行：仅检查该工具的 HARD_REQUIRED 槽位是否都已在实体内存中填充。
     */
    private boolean allRequiredSlotsReady(AssistantPlannedTool tool, Map<String, String> entities) {
        List<String> effectiveRequired = effectiveRequiredSlots(tool);
        if (effectiveRequired.isEmpty()) {
            return true;
        }
        for (String slot : effectiveRequired) {
            if (blank(entities.get(slot))) {
                return false;
            }
        }
        return true;
    }

    private List<String> effectiveRequiredSlots(AssistantPlannedTool tool) {
        if (tool == null) {
            return List.of();
        }
        List<InputSlot> slots = tool.getInputSlots();
        if (slots == null || slots.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> required = new LinkedHashSet<>();
        for (InputSlot slot : slots) {
            if (slot == null || blank(slot.getSlotKey())) {
                continue;
            }
            if (isBlockingRequiredSlot(slot)) {
                required.add(slot.getSlotKey());
            }
        }
        if (required.isEmpty()) {
            return List.of();
        }
        return new ArrayList<>(required);
    }

    private boolean isBlockingRequiredSlot(InputSlot slot) {
        if (slot == null) {
            return false;
        }
        String requirement = text(slot.getRequirement()).toUpperCase();
        return "HARD_REQUIRED".equals(requirement);
    }

    private List<String> resolveToolSlotScope(AssistantPlannedTool tool) {
        if (tool == null || tool.getInputSlots() == null || tool.getInputSlots().isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (InputSlot slot : tool.getInputSlots()) {
            if (slot == null || blank(slot.getSlotKey())) {
                continue;
            }
            out.add(slot.getSlotKey());
        }
        return new ArrayList<>(out);
    }

    private boolean isSlotInPendingTools(String slotKey, List<AssistantPlannedTool> pending) {
        if (blank(slotKey) || pending == null || pending.isEmpty()) {
            return false;
        }
        for (AssistantPlannedTool tool : pending) {
            if (tool == null || tool.getInputSlots() == null) {
                continue;
            }
            for (InputSlot slot : tool.getInputSlots()) {
                if (slot != null && slotKey.equals(text(slot.getSlotKey()))) {
                    return true;
                }
            }
        }
        return false;
    }

    private void cleanupAfterFinish(String taskId) {
        assistantPlanService.clear(taskId);
        assistantDialogueService.clearUserMessages(taskId);
        assistantEntityMemoryService.clear(taskId);
    }

    private String buildExecutedToolsText(List<AssistantPlannedTool> executedTools) {
        if (executedTools == null || executedTools.isEmpty()) {
            return "";
        }
        String names = executedTools.stream()
                .map(AssistantPlannedTool::getToolName)
                .filter(name -> name != null && !name.isBlank())
                .distinct()
                .collect(Collectors.joining(", "));
        if (names.isBlank()) {
            return "";
        }
        return "Executed tools: " + names + ".";
    }

    private AssistantUserState resolveState(AssistantAgentProcessRequest request) {
        String taskId = text(request.getTaskId());
        String userId = text(request.getUserId());
        if (blank(userId)) {
            throw new IllegalArgumentException("userId is required");
        }
        if (!blank(taskId)) {
            return stateMachineService.findByTaskId(taskId)
                    .orElseGet(() -> startState(userId, taskId, request));
        }
        return stateMachineService.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("taskId is required for first request"));
    }

    private AssistantUserState startState(String userId, String taskId, AssistantAgentProcessRequest request) {
        AssistantStateStartRequest start = new AssistantStateStartRequest();
        start.setUserId(userId);
        start.setTaskId(taskId);
        start.setTraceId(request.getTraceId());
        start.setLastMessage(request.getMessage());
        return stateMachineService.start(start);
    }

    private AssistantUserState transition(AssistantUserState current,
                                          LlmAgentState nextState,
                                          java.util.function.Consumer<AssistantStateTransitionRequest> updater) {
        AssistantStateTransitionRequest t = new AssistantStateTransitionRequest();
        t.setTaskId(current.getTaskId());
        t.setUserId(current.getUserId());
        t.setNextState(nextState);
        t.setLastMessage(current.getLastMessage());
        updater.accept(t);
        return stateMachineService.transition(t);
    }

    private void publish(String eventType, AssistantUserState state, String message) {
        try {
            taskWebSocketBroadcaster.publish(eventType, state, message);
        } catch (Exception e) {
            log.warn("failed to publish assistant websocket event. type={}, taskId={}",
                    eventType, state == null ? "-" : state.getTaskId(), e);
        }
    }

    private String text(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private record ScoredSkill(SkillVectorRecord skill, double score) {
    }

    private record IntentVoteResult(String intent,
                                    String clarificationType,
                                    String question,
                                    List<IntentCandidate> candidatesTop3) {
        static IntentVoteResult resolved(String intent) {
            return new IntentVoteResult(intent, "", "", List.of());
        }

        static IntentVoteResult lowConfidence(List<IntentCandidate> top3, String question) {
            return new IntentVoteResult("", "LOW_CONFIDENCE", question, top3 == null ? List.of() : top3);
        }

        static IntentVoteResult ambiguous(List<IntentCandidate> top3, String question) {
            return new IntentVoteResult("", "AMBIGUOUS_TOP2", question, top3 == null ? List.of() : top3);
        }

        boolean resolved() {
            return !intent.isBlank();
        }
    }
}
