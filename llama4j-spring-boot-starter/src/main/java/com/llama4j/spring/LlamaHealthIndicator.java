package com.llama4j.spring;

import com.llama4j.core.ModelRegistry;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;

/**
 * 健康检查 — 报告所有已注册模型的状态
 */
public class LlamaHealthIndicator implements HealthIndicator {

    private final ModelRegistry modelRegistry;

    public LlamaHealthIndicator(ModelRegistry modelRegistry) {
        this.modelRegistry = modelRegistry;
    }

    @Override
    public Health health() {
        Health.Builder builder = Health.up();
        builder.withDetail("models", modelRegistry.modelNames());
        builder.withDetail("defaultModel", modelRegistry.defaultModelName());
        return builder.build();
    }
}
