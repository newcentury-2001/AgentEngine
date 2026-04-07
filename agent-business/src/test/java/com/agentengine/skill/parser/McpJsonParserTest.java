package com.agentengine.skill.parser;

import com.agentengine.skill.model.InputSlot;
import com.agentengine.skill.model.McpSkill;
import com.agentengine.skill.model.McpTool;
import com.agentengine.skill.model.OutputSlotInferred;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MCP JSON解析器测试类
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class McpJsonParserTest {

    private static final String JSON_FILE_PATH = "dataset/mcp_final_summary.json";
    private List<McpSkill> skills;

    @BeforeAll
    void setup() throws IOException {
        // 解析JSON文件
        File file = new File(JSON_FILE_PATH);
        if (!file.exists()) {
            System.err.println("Warning: JSON file not found at " + JSON_FILE_PATH);
            return;
        }
        skills = McpJsonParser.parseFromFile(JSON_FILE_PATH);
    }

    @Test
    void testParseFromFile() throws IOException {
        assertNotNull(skills, "Parsed skills should not be null");
        assertFalse(skills.isEmpty(), "Parsed skills should not be empty");

        System.out.println("Total skills parsed: " + skills.size());
    }

    @Test
    void testSkillStructure() {
        if (skills == null || skills.isEmpty()) {
            return;
        }

        McpSkill firstSkill = skills.get(0);
        assertNotNull(firstSkill.getSkillName(), "Skill name should not be null");
        assertNotNull(firstSkill.getSkillDescription(), "Skill description should not be null");
        assertNotNull(firstSkill.getIntent(), "Intent should not be null");
        assertNotNull(firstSkill.getActionType(), "Action type should not be null");

        System.out.println("First skill: " + firstSkill.getSkillName());
        System.out.println("Description: " + firstSkill.getSkillDescription());
        System.out.println("Intent: " + firstSkill.getIntent());
        System.out.println("Action type: " + firstSkill.getActionType());
    }

    @Test
    void testToolStructure() {
        if (skills == null || skills.isEmpty()) {
            return;
        }

        McpSkill firstSkill = skills.get(0);
        List<McpTool> tools = firstSkill.getTools();

        assertNotNull(tools, "Tools should not be null");
        assertFalse(tools.isEmpty(), "First skill should have tools");

        McpTool firstTool = tools.get(0);
        assertNotNull(firstTool.getToolName(), "Tool name should not be null");
        assertNotNull(firstTool.getToolDescription(), "Tool description should not be null");
        assertNotNull(firstTool.getInputSchema(), "Input schema should not be null");
        assertEquals(firstSkill.getSkillName(), firstTool.getSkillName(),
            "Tool should have correct skill name reference");

        System.out.println("First tool: " + firstTool.getToolName());
        System.out.println("Description: " + firstTool.getToolDescription());
        System.out.println("Skill name: " + firstTool.getSkillName());
    }

    @Test
    void testInputSlots() {
        if (skills == null || skills.isEmpty()) {
            return;
        }

        McpSkill firstSkill = skills.get(0);
        McpTool firstTool = firstSkill.getTools().get(0);

        List<InputSlot> inputSlots = firstTool.getInputSlots();
        assertNotNull(inputSlots, "Input slots should not be null");

        if (!inputSlots.isEmpty()) {
            InputSlot firstSlot = inputSlots.get(0);
            assertNotNull(firstSlot.getSlotKey(), "Slot key should not be null");
            assertNotNull(firstSlot.getFieldPath(), "Field path should not be null");
            assertNotNull(firstSlot.getFieldType(), "Field type should not be null");

            System.out.println("First input slot key: " + firstSlot.getSlotKey());
            System.out.println("Field path: " + firstSlot.getFieldPath());
            System.out.println("Field type: " + firstSlot.getFieldType());
            System.out.println("Required: " + firstSlot.isRequired());
        }
    }

    @Test
    void testOutputSlotsInferred() {
        if (skills == null || skills.isEmpty()) {
            return;
        }

        McpSkill firstSkill = skills.get(0);
        McpTool firstTool = firstSkill.getTools().get(0);

        List<OutputSlotInferred> outputSlots = firstTool.getOutputSlotsInferred();
        assertNotNull(outputSlots, "Output slots should not be null");

        if (!outputSlots.isEmpty()) {
            OutputSlotInferred firstSlot = outputSlots.get(0);
            assertNotNull(firstSlot.getSlotKey(), "Slot key should not be null");
            assertNotNull(firstSlot.getConfidence(), "Confidence should not be null");

            System.out.println("First output slot key: " + firstSlot.getSlotKey());
            System.out.println("Confidence: " + firstSlot.getConfidence());
        }
    }

    @Test
    void testFlattenTools() {
        if (skills == null || skills.isEmpty()) {
            return;
        }

        List<McpTool> allTools = McpJsonParser.flattenTools(skills);
        assertNotNull(allTools, "Flattened tools should not be null");
        assertFalse(allTools.isEmpty(), "Flattened tools should not be empty");

        System.out.println("Total tools across all skills: " + allTools.size());
    }

    @Test
    void testBuildSkillNameMap() {
        if (skills == null || skills.isEmpty()) {
            return;
        }

        Map<String, McpSkill> skillMap = McpJsonParser.buildSkillNameMap(skills);
        assertNotNull(skillMap, "Skill map should not be null");
        assertEquals(skills.size(), skillMap.size(), "Skill map size should match skills size");

        // 测试通过技能名称查找
        String firstSkillName = skills.get(0).getSkillName();
        McpSkill foundSkill = skillMap.get(firstSkillName);
        assertNotNull(foundSkill, "Skill should be found by name");
        assertEquals(firstSkillName, foundSkill.getSkillName(), "Found skill should match");

        System.out.println("Skill map contains " + skillMap.size() + " skills");
    }

    @Test
    void testBuildToolKeyMap() {
        if (skills == null || skills.isEmpty()) {
            return;
        }

        Map<String, McpTool> toolMap = McpJsonParser.buildToolKeyMap(skills);
        assertNotNull(toolMap, "Tool map should not be null");

        // 测试通过工具键查找
        if (!skills.get(0).getTools().isEmpty()) {
            McpSkill firstSkill = skills.get(0);
            McpTool firstTool = firstSkill.getTools().get(0);
            String toolKey = firstSkill.getSkillName() + ":" + firstTool.getToolName();
            McpTool foundTool = toolMap.get(toolKey);
            assertNotNull(foundTool, "Tool should be found by key");
            assertEquals(firstTool.getToolName(), foundTool.getToolName(), "Found tool should match");
        }

        System.out.println("Tool map contains " + toolMap.size() + " tools");
    }

    @Test
    void testGetAllInputSlots() {
        if (skills == null || skills.isEmpty()) {
            return;
        }

        List<InputSlot> allSlots = McpJsonParser.getAllInputSlots(skills);
        assertNotNull(allSlots, "All input slots should not be null");
        assertFalse(allSlots.isEmpty(), "All input slots should not be empty");

        System.out.println("Total input slots: " + allSlots.size());
    }

    @Test
    void testGetAllOutputSlots() {
        if (skills == null || skills.isEmpty()) {
            return;
        }

        List<OutputSlotInferred> allSlots = McpJsonParser.getAllOutputSlots(skills);
        assertNotNull(allSlots, "All output slots should not be null");
        assertFalse(allSlots.isEmpty(), "All output slots should not be empty");

        System.out.println("Total output slots: " + allSlots.size());
    }

    @Test
    void testGetStatistics() {
        if (skills == null || skills.isEmpty()) {
            return;
        }

        Map<String, Integer> stats = McpJsonParser.getStatistics(skills);
        assertNotNull(stats, "Statistics should not be null");
        assertTrue(stats.containsKey("skillCount"), "Statistics should contain skillCount");
        assertTrue(stats.containsKey("toolCount"), "Statistics should contain toolCount");
        assertTrue(stats.containsKey("inputSlotCount"), "Statistics should contain inputSlotCount");
        assertTrue(stats.containsKey("outputSlotCount"), "Statistics should contain outputSlotCount");

        System.out.println("=== Statistics ===");
        System.out.println("Skills: " + stats.get("skillCount"));
        System.out.println("Tools: " + stats.get("toolCount"));
        System.out.println("Input slots: " + stats.get("inputSlotCount"));
        System.out.println("Output slots: " + stats.get("outputSlotCount"));
    }

    @Test
    void testParseFromJsonString() throws IOException {
        // 创建简单的测试JSON字符串
        String testJson = "[" +
            "{\"skillName\":\"test\"," +
            "\"skillDescription\":\"test skill\"," +
            "\"intent\":\"query\"," +
            "\"actionType\":\"read\"," +
            "\"tools\":[]" +
            "}" +
            "]";

        List<McpSkill> parsedSkills = McpJsonParser.parseFromString(testJson);
        assertNotNull(parsedSkills, "Parsed skills from string should not be null");
        assertEquals(1, parsedSkills.size(), "Should parse exactly 1 skill");
        assertEquals("test", parsedSkills.get(0).getSkillName(), "Skill name should match");
    }

    @Test
    void testToJson() throws IOException {
        if (skills == null || skills.isEmpty()) {
            return;
        }

        // 转换第一个技能为JSON
        String json = McpJsonParser.toJson(skills.get(0));
        assertNotNull(json, "JSON string should not be null");
        assertFalse(json.isEmpty(), "JSON string should not be empty");
        assertTrue(json.contains("skillName"), "JSON should contain skillName field");

        System.out.println("Sample JSON output (first 200 chars): " +
            json.substring(0, Math.min(200, json.length())));
    }
}
