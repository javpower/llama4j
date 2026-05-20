package com.llama4j.sample;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 示例 Spring Boot 应用 — 演示 llama4j 集成
 *
 * <p>启动一个 Spring Boot 服务器，启用 llama4j 自动配置。
 * 暴露 OpenAI 兼容 API 于 {@code /v1/chat/completions}，
 * 可使用任何 OpenAI 客户端库对接。</p>
 *
 * <h2>配置</h2>
 * <p>在 {@code application.yml} 中设置模型路径：</p>
 * <pre>
 * llama4j:
 *   model:
 *     path: /models/qwen2.5-7b-q4_k_m.gguf
 * </pre>
 *
 * <h2>测试</h2>
 * <pre>
 * curl -X POST http://localhost:8080/v1/chat/completions \
 *   -H "Content-Type: application/json" \
 *   -d '{"messages": [{"role": "user", "content": "你好！"}]}'
 * </pre>
 */
@SpringBootApplication
public class SimpleChatApplication {

    public static void main(String[] args) {
        SpringApplication.run(SimpleChatApplication.class, args);
    }
}
