package com.llama4j.sample;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 示例 Spring Boot 应用 — 演示 llama4j 多模型集成
 *
 * <p>暴露 OpenAI 兼容 API：</p>
 * <ul>
 *   <li>{@code POST /v1/chat/completions} — 聊天补全（支持同步和流式）</li>
 *   <li>{@code GET /v1/models} — 查看已注册的模型列表</li>
 * </ul>
 *
 * <h2>测试</h2>
 * <pre>
 * # 使用默认模型
 * curl -X POST http://localhost:8080/v1/chat/completions \
 *   -H "Content-Type: application/json" \
 *   -d '{"messages": [{"role": "user", "content": "你好！"}]}'
 *
 * # 指定模型
 * curl -X POST http://localhost:8080/v1/chat/completions \
 *   -H "Content-Type: application/json" \
 *   -d '{"model": "deepseek", "messages": [{"role": "user", "content": "Hello!"}]}'
 *
 * # 流式
 * curl -X POST http://localhost:8080/v1/chat/completions \
 *   -H "Content-Type: application/json" \
 *   -d '{"stream": true, "messages": [{"role": "user", "content": "讲个故事"}]}'
 *
 * # 查看模型列表
 * curl http://localhost:8080/v1/models
 * </pre>
 */
@SpringBootApplication
public class SimpleChatApplication {

    public static void main(String[] args) {
        SpringApplication.run(SimpleChatApplication.class, args);
    }
}
