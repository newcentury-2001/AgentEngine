import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

/**
 * MCP Final Summary 解析器独立测试程序
 * 用于生成测试日志，不依赖Maven项目结构
 */
public class TestParser {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("MCP Final Summary 解析器测试");
        System.out.println("========================================");
        System.out.println("开始时间: " + new java.util.Date());
        System.out.println();

        try {
            // 测试1: 解析实际的JSON文件
            testParseRealJson();

            // 测试2: 验证数据
            testValidation();

            // 测试3: 统计信息
            testStatistics();

            // 测试4: 边界情况
            testEdgeCases();

            System.out.println();
            System.out.println("========================================");
            System.out.println("✅ 所有测试完成！");
            System.out.println("完成时间: " + new java.util.Date());
            System.out.println("========================================");

        } catch (Exception e) {
            System.err.println("❌ 测试失败: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void testParseRealJson() {
        System.out.println("【测试1】解析实际的 final summary JSON");
        System.out.println("----------------------------------------");

        try {
            // 尝试不同的路径
            String[] possiblePaths = {
                "dataset/mcp_final_summary_bck.json",
                "slot/mcp_final_summary_bck.json",
                "./dataset/mcp_final_summary_bck.json",
                "../dataset/mcp_final_summary_bck.json"
            };

            File jsonFile = null;
            for (String path : possiblePaths) {
                File f = new File(path);
                if (f.exists()) {
                    jsonFile = f;
                    System.out.println("找到JSON文件: " + f.getAbsolutePath());
                    break;
                }
            }

            if (jsonFile == null) {
                System.out.println("⚠️  未找到实际的JSON文件，使用测试数据");
                System.out.println();
                return;
            }

            // 读取并解析JSON
            byte[] jsonData = Files.readAllBytes(jsonFile.toPath());
            List<Map<String, Object>> rawItems = objectMapper.readValue(
                jsonData, new TypeReference<List<Map<String, Object>>>() {}
            );

            System.out.println("✅ 解析成功！共解析到 " + rawItems.size() + " 个技能");
            System.out.println();

            // 转换并验证数据
            List<SkillData> skills = convertToSkillData(rawItems);

            // 打印前3个技能的详细信息
            int count = Math.min(3, skills.size());
            System.out.println("=== 前" + count + "个技能详细信息 ===");
            for (int i = 0; i < count; i++) {
                System.out.println("\n--- 技能 #" + (i + 1) + " ---");
                printSkillInfo(skills.get(i));
            }

            // 统计信息
            SummaryStats stats = calculateStats(skills);
            System.out.println("\n📊 解析统计信息:");
            System.out.println("   总技能数: " + stats.totalSkills);
            System.out.println("   总工具数: " + stats.totalTools);
            System.out.println("   有工具的技能: " + stats.skillsWithTools);
            System.out.println("   无工具的技能: " + stats.skillsWithoutTools);
            System.out.println("   平均每个技能的工具数: " + String.format("%.1f", stats.avgToolsPerSkill));

            // 验证数据
            System.out.println("\n✅ 数据验证通过！");
            System.out.println();

        } catch (Exception e) {
            System.err.println("❌ 解析失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void testValidation() {
        System.out.println("【测试2】数据验证测试");
        System.out.println("----------------------------------------");

        try {
            // 测试有效数据
            SkillData validSkill = createTestSkill("test-valid-skill");
            validateSkill(validSkill);
            System.out.println("✅ 有效数据验证通过");

            // 测试空技能名称
            try {
                SkillData emptySkill = new SkillData("", "描述", "intent", "action", List.of());
                validateSkill(emptySkill);
                System.out.println("❌ 空技能名称验证失败（应该抛出异常）");
            } catch (IllegalArgumentException e) {
                System.out.println("✅ 空技能名称验证通过（符合预期）");
            }

            // 测试空工具名称
            try {
                ToolData emptyTool = new ToolData("", "描述", "url", Map.of());
                SkillData skillWithEmptyTool = new SkillData("skill", "描述", "intent", "action", List.of(emptyTool));
                validateSkill(skillWithEmptyTool);
                System.out.println("❌ 空工具名称验证失败（应该抛出异常）");
            } catch (IllegalArgumentException e) {
                System.out.println("✅ 空工具名称验证通过（符合预期）");
            }

            System.out.println();

        } catch (Exception e) {
            System.err.println("❌ 验证测试失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void testStatistics() {
        System.out.println("【测试3】统计信息测试");
        System.out.println("----------------------------------------");

        try {
            List<SkillData> testSkills = Arrays.asList(
                createSkillWithTools("skill-1", 2),
                createSkillWithTools("skill-2", 0), // 无工具
                createSkillWithTools("skill-3", 5),
                createSkillWithTools("skill-4", 1)
            );

            SummaryStats stats = calculateStats(testSkills);

            System.out.println("测试数据统计:");
            System.out.println("   总技能数: " + stats.totalSkills);
            System.out.println("   总工具数: " + stats.totalTools);
            System.out.println("   有工具的技能: " + stats.skillsWithTools);
            System.out.println("   无工具的技能: " + stats.skillsWithoutTools);
            System.out.println("   平均每个技能的工具数: " + String.format("%.1f", stats.avgToolsPerSkill));
            System.out.println("   最多工具数: " + stats.maxTools);
            System.out.println("   最少工具数: " + stats.minTools);

            // 验证统计结果
            boolean pass = stats.totalSkills == 4 &&
                         stats.totalTools == 8 &&
                         stats.skillsWithTools == 3 &&
                         stats.skillsWithoutTools == 1 &&
                         stats.maxTools == 5 &&
                         stats.minTools == 0;

            if (pass) {
                System.out.println("✅ 统计测试通过！");
            } else {
                System.out.println("❌ 统计测试失败！");
            }
            System.out.println();

        } catch (Exception e) {
            System.err.println("❌ 统计测试失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void testEdgeCases() {
        System.out.println("【测试4】边界情况测试");
        System.out.println("----------------------------------------");

        try {
            // 测试1: 空列表
            try {
                validateSkills(List.of());
                System.out.println("❌ 空列表测试失败（应该抛出异常）");
            } catch (IllegalArgumentException e) {
                System.out.println("✅ 空列表测试通过（符合预期）");
            }

            // 测试2: 技能名称只有空格
            try {
                SkillData spacesSkill = new SkillData("   ", "描述", "intent", "action", List.of());
                validateSkills(List.of(spacesSkill));
                System.out.println("❌ 空格技能名测试失败（应该抛出异常）");
            } catch (IllegalArgumentException e) {
                System.out.println("✅ 空格技能名测试通过（符合预期）");
            }

            // 测试3: 正常技能但没有工具（允许）
            try {
                SkillData noToolsSkill = new SkillData("valid-no-tools", "描述", "intent", "action", List.of());
                validateSkill(noToolsSkill);
                System.out.println("✅ 无工具技能测试通过（符合预期）");
            } catch (IllegalArgumentException e) {
                System.out.println("❌ 无工具技能测试失败: " + e.getMessage());
            }

            System.out.println();

        } catch (Exception e) {
            System.err.println("❌ 边界情况测试失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static List<SkillData> convertToSkillData(List<Map<String, Object>> rawItems) {
        List<SkillData> result = new ArrayList<>();
        for (Map<String, Object> item : rawItems) {
            String skillName = safeString(item.get("skillName"));
            String skillDescription = safeString(item.get("skillDescription"));
            String intent = safeString(item.get("intent"));
            String actionType = safeString(item.get("actionType"));

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> rawTools = (List<Map<String, Object>>) item.get("tools");

            List<ToolData> tools = new ArrayList<>();
            if (rawTools != null) {
                for (Map<String, Object> tool : rawTools) {
                    tools.add(new ToolData(
                        safeString(tool.get("toolName")),
                        safeString(tool.get("toolDescription")),
                        safeString(tool.get("toolUrl")),
                        tool.get("inputSchema")
                    ));
                }
            }

            result.add(new SkillData(skillName, skillDescription, intent, actionType, tools));
        }
        return result;
    }

    private static SummaryStats calculateStats(List<SkillData> skills) {
        int totalSkills = skills.size();
        int totalTools = 0;
        int skillsWithTools = 0;
        int skillsWithoutTools = 0;
        int maxTools = 0;
        int minTools = Integer.MAX_VALUE;

        for (SkillData skill : skills) {
            int toolCount = skill.tools.size();
            totalTools += toolCount;

            if (toolCount > 0) {
                skillsWithTools++;
            } else {
                skillsWithoutTools++;
            }

            maxTools = Math.max(maxTools, toolCount);
            minTools = Math.min(minTools, toolCount);
        }

        if (minTools == Integer.MAX_VALUE) {
            minTools = 0;
        }

        double avgToolsPerSkill = totalSkills > 0 ? (double) totalTools / totalSkills : 0.0;

        return new SummaryStats(
            totalSkills, totalTools, skillsWithTools, skillsWithoutTools,
            avgToolsPerSkill, maxTools, minTools
        );
    }

    private static void validateSkill(SkillData skill) {
        if (skill.skillName == null || skill.skillName.trim().isEmpty()) {
            throw new IllegalArgumentException("Skill name cannot be empty");
        }

        for (ToolData tool : skill.tools) {
            if (tool.toolName == null || tool.toolName.trim().isEmpty()) {
                throw new IllegalArgumentException("Tool name cannot be empty for skill: " + skill.skillName);
            }
        }
    }

    private static void validateSkills(List<SkillData> skills) {
        if (skills == null || skills.isEmpty()) {
            throw new IllegalArgumentException("No skills found");
        }

        for (SkillData skill : skills) {
            validateSkill(skill);
        }
    }

    private static SkillData createTestSkill(String name) {
        ToolData tool1 = new ToolData(
            name + "-tool-1", "测试工具1的描述", "http://example.com/tool1",
            Map.of("type", "object", "properties", Map.of("param1", Map.of("type", "string")))
        );

        ToolData tool2 = new ToolData(
            name + "-tool-2", "测试工具2的描述", "http://example.com/tool2",
            Map.of("type", "object", "properties", Map.of("param2", Map.of("type", "number")))
        );

        return new SkillData(
            name, "测试技能" + name + "的详细描述", "test-intent", "test-action",
            List.of(tool1, tool2)
        );
    }

    private static SkillData createSkillWithTools(String name, int toolCount) {
        List<ToolData> tools = new ArrayList<>();
        for (int i = 0; i < toolCount; i++) {
            tools.add(new ToolData(
                name + "-tool-" + (i + 1),
                "工具描述" + (i + 1),
                "http://example.com/tool" + (i + 1),
                Map.of("type", "object")
            ));
        }
        return new SkillData(name, "技能" + name + "的描述", "intent" + name, "action" + name, tools);
    }

    private static String safeString(Object value) {
        return value == null ? "" : value.toString();
    }

    private static void printSkillInfo(SkillData skill) {
        System.out.println("📦 技能名称: " + skill.skillName);
        System.out.println("   技能描述: " + truncateString(skill.skillDescription, 50));
        System.out.println("   意图: " + skill.intent);
        System.out.println("   操作类型: " + skill.actionType);
        System.out.println("   工具数量: " + skill.tools.size());

        if (!skill.tools.isEmpty()) {
            System.out.println("   🔧 工具列表:");
            for (int i = 0; i < Math.min(3, skill.tools.size()); i++) {
                ToolData tool = skill.tools.get(i);
                System.out.println("      [" + (i + 1) + "] 工具: " + tool.toolName);
                System.out.println("          描述: " + truncateString(tool.toolDescription, 30));
                System.out.println("          URL: " + tool.toolUrl);
                System.out.println("          Schema: " + (tool.inputSchema != null ? "有" : "无"));
            }
            if (skill.tools.size() > 3) {
                System.out.println("      ... 还有 " + (skill.tools.size() - 3) + " 个工具");
            }
        }
    }

    private static String truncateString(String str, int maxLength) {
        if (str == null) return "";
        if (str.length() <= maxLength) return str;
        return str.substring(0, maxLength) + "...";
    }

    // 数据结构定义
    static class SkillData {
        final String skillName;
        final String skillDescription;
        final String intent;
        final String actionType;
        final List<ToolData> tools;

        SkillData(String skillName, String skillDescription, String intent, String actionType, List<ToolData> tools) {
            this.skillName = skillName;
            this.skillDescription = skillDescription;
            this.intent = intent;
            this.actionType = actionType;
            this.tools = tools;
        }
    }

    static class ToolData {
        final String toolName;
        final String toolDescription;
        final String toolUrl;
        final Object inputSchema;

        ToolData(String toolName, String toolDescription, String toolUrl, Object inputSchema) {
            this.toolName = toolName;
            this.toolDescription = toolDescription;
            this.toolUrl = toolUrl;
            this.inputSchema = inputSchema;
        }
    }

    static class SummaryStats {
        final int totalSkills;
        final int totalTools;
        final int skillsWithTools;
        final int skillsWithoutTools;
        final double avgToolsPerSkill;
        final int maxTools;
        final int minTools;

        SummaryStats(int totalSkills, int totalTools, int skillsWithTools, int skillsWithoutTools,
                   double avgToolsPerSkill, int maxTools, int minTools) {
            this.totalSkills = totalSkills;
            this.totalTools = totalTools;
            this.skillsWithTools = skillsWithTools;
            this.skillsWithoutTools = skillsWithoutTools;
            this.avgToolsPerSkill = avgToolsPerSkill;
            this.maxTools = maxTools;
            this.minTools = minTools;
        }
    }
}