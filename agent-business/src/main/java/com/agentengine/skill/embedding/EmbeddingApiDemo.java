package com.agentengine.skill.embedding;

/**
 * Embedding API 使用演示
 *
 * 前端可以调用以下接口：
 */
public class EmbeddingApiDemo {

    /*
    ================================================
    API 接口文档
    ================================================

    1. 生成指定工具的 Embedding
    --------------------------------
    POST /api/embedding/generate
    Content-Type: application/json

    请求体：
    {
      "toolNames": [
        "recognition:location_recognition",
        "recognition:person_recognition",
        "image-process:crop"
      ],
      "forceRegenerate": false
    }

    响应：
    {
      "totalTools": 3,
      "successCount": 3,
      "failureCount": 0,
      "failedTools": [],
      "processingTimeMs": 3500,
      "message": "Embedding generation completed. Success: 3, Failure: 0"
    }


    2. 批量生成所有工具的 Embedding
    --------------------------------
    POST /api/embedding/generate-all
    Content-Type: application/json

    响应：
    {
      "totalTools": 88,
      "successCount": 85,
      "failureCount": 3,
      "failedTools": [
        "skill1:tool1",
        "skill2:tool2",
        "skill3:tool3"
      ],
      "processingTimeMs": 12000,
      "message": "Embedding generation completed. Success: 85, Failure: 3"
    }


    3. 查询 Embedding 状态
    --------------------------------
    GET /api/embedding/status?toolNames=recognition:location_recognition&toolNames=recognition:person_recognition

    响应：
    {
      "totalTools": 2,
      "existingCount": 2,
      "missingCount": 0,
      "existingTools": [
        "recognition:location_recognition",
        "recognition:person_recognition"
      ],
      "missingTools": []
    }


    ================================================
    前端调用示例 (JavaScript)
    ================================================

    // 示例 1: 生成指定工具的 Embedding
    async function generateToolEmbeddings() {
      const response = await fetch('/api/embedding/generate', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json'
        },
        body: JSON.stringify({
          toolNames: [
            'recognition:location_recognition',
            'recognition:person_recognition'
          ],
          forceRegenerate: false
        })
      });
      return await response.json();
    }

    // 示例 2: 生成所有工具的 Embedding
    async function generateAllEmbeddings() {
      const response = await fetch('/api/embedding/generate-all', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json'
        }
      });
      return await response.json();
    }

    // 示例 3: 查询 Embedding 状态
    async function checkEmbeddingStatus() {
      const toolNames = ['recognition:location_recognition', 'recognition:person_recognition'];
      const response = await fetch(`/api/embedding/status?toolNames=${toolNames.join('&toolNames=')}`);
      return await response.json();
    }


    ================================================
    数据流说明
    ================================================

    前端请求
       ↓
    Controller (EmbeddingController)
       ↓
    Service (EmbeddingOrchestrationService)
       ↓
    Resource (EmbeddingResource)
       ↓
    第三方 Embedding API (智谱 AI)
       ↓
    返回结果到前端

    线程池: Resource 层从 IOC 容器获取 embeddingExecutor
    返回类型: CompletableFuture<ResponseEntity<EmbeddingResult>>

    */

    public static void main(String[] args) {
        System.out.println("Embedding API 接口已就绪");
        System.out.println();
        System.out.println("启动 Spring Boot 应用后，可以使用以下端点：");
        System.out.println("  POST /api/embedding/generate - 生成指定工具的 embedding");
        System.out.println("  POST /api/embedding/generate-all - 生成所有工具的 embedding");
        System.out.println("  GET  /api/embedding/status - 查询 embedding 状态");
        System.out.println();
        System.out.println("详细的使用说明请查看类文档");
    }
}
