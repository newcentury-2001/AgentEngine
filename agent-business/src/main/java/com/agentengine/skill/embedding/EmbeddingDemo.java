package com.agentengine.skill.embedding;

import com.agentengine.skill.model.McpSkill;
import com.agentengine.skill.parser.McpJsonParser;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Embedding 生成演示程序
 */
public class EmbeddingDemo {

    public static void main(String[] args) {
        try {
            System.out.println("=== Embedding Generation Demo ===");
            System.out.println();

            // 1. 解析 JSON 文件
            System.out.println("Step 1: Parsing JSON file...");
            List<McpSkill> skills = McpJsonParser.parseFromFile("dataset/mcp_final_summary.json");
            System.out.println("✓ Parsed " + skills.size() + " skills");

            // 2. 获取统计信息
            Map<String, Integer> stats = McpJsonParser.getStatistics(skills);
            System.out.println("✓ Total tools: " + stats.get("toolCount"));
            System.out.println();

            // 3. 显示使用说明
            System.out.println("Step 2: Embedding Generation Setup");
            System.out.println("To generate embeddings, you need to:");
            System.out.println("1. Set the zhipukey environment variable");
            System.out.println("2. Ensure embedding.enabled=true in application.yml");
            System.out.println();

            // 4. 检查 API Key
            String apiKey = System.getenv("zhipukey");
            if (apiKey == null || apiKey.isEmpty()) {
                System.out.println("⚠ API Key not set. Please set zhipukey environment variable.");
                System.out.println();
                System.out.println("Example usage:");
                System.out.println("  export zhipukey=your-api-key");
                System.out.println("  mvn exec:java -Dexec.mainClass=\"com.agentengine.skill.embedding.EmbeddingDemo\"");
                return;
            }

            System.out.println("✓ API Key found");
            System.out.println();

            // 5. 显示计划
            System.out.println("Step 3: Generation Plan");
            System.out.println("  Total tools to process: " + stats.get("toolCount"));
            System.out.println("  Thread pool size: 6 core, 12 max");
            System.out.println("  QPS limit: 8");
            System.out.println("  Estimated time: ~" + (stats.get("toolCount") / 8.0) + " seconds");
            System.out.println();

            // 6. 显示工具列表（前5个）
            System.out.println("Step 4: Sample Tools");
            List<com.agentengine.skill.model.McpTool> allTools = McpJsonParser.flattenTools(skills);
            for (int i = 0; i < Math.min(5, allTools.size()); i++) {
                com.agentengine.skill.model.McpTool tool = allTools.get(i);
                System.out.println("  " + (i + 1) + ". " + tool.getToolName() + " (" + tool.getSkillName() + ")");
            }
            if (allTools.size() > 5) {
                System.out.println("  ... and " + (allTools.size() - 5) + " more tools");
            }
            System.out.println();

            System.out.println("=== Demo Completed ===");
            System.out.println();
            System.out.println("To actually generate embeddings, use the Spring Boot application:");
            System.out.println("  @Autowired private ToolEmbeddingGenerator generator;");
            System.out.println("  generator.parseAndGenerateEmbeddings(\"dataset/mcp_final_summary.json\").join();");

        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
