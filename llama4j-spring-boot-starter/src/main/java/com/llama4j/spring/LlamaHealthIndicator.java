package com.llama4j.spring;

import com.llama4j.native_.LlamaContext;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;

/**
 * llama4j 健康检查指示器 — Spring Boot Actuator 集成
 *
 * <p>报告原生模型上下文的健康状态，包括模型是否已加载、上下文大小、
 * 词汇表大小和 KV 缓存利用率。可通过 {@code /actuator/health} 端点访问。</p>
 *
 * <h2>健康检查响应示例</h2>
 * <pre>
 * {
 *   "status": "UP",
 *   "details": {
 *     "llama4j": {
 *       "status": "UP",
 *       "modelLoaded": true,
 *       "contextSize": 4096,
 *       "vocabSize": 151936,
 *       "kvCacheTokens": 128,
 *       "kvCacheUsage": "3.1%"
 *     }
 *   }
 * }
 * </pre>
 *
 * <p>当模型上下文已关闭或查询出错时，状态变为 DOWN。</p>
 */
public class LlamaHealthIndicator implements HealthIndicator {

    private final LlamaContext context;

    public LlamaHealthIndicator(LlamaContext context) {
        this.context = context;
    }

    @Override
    public Health health() {
        Health.Builder builder;

        try {
            if (context.isClosed()) {
                builder = Health.down();
                builder.withDetail("modelLoaded", false);
                builder.withDetail("error", "LlamaContext 已关闭");
            } else {
                builder = Health.up();
                int contextSize = context.getContextSize();
                int vocabSize = context.getVocabSize();
                int kvTokens = context.getKvCacheTokenCount();
                double kvUsage = contextSize > 0 ? (double) kvTokens / contextSize : 0.0;

                builder.withDetail("modelLoaded", true);
                builder.withDetail("contextSize", contextSize);
                builder.withDetail("vocabSize", vocabSize);
                builder.withDetail("kvCacheTokens", kvTokens);
                builder.withDetail("kvCacheUsage", String.format("%.1f%%", kvUsage * 100));
            }
        } catch (Exception e) {
            builder = Health.down();
            builder.withDetail("modelLoaded", false);
            builder.withDetail("error", e.getMessage());
        }

        return builder.build();
    }
}
