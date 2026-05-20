package com.llama4j.metrics;

import com.llama4j.core.ChatResponse;
import com.llama4j.core.ChatStreamListener;
import io.micrometer.core.instrument.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 基于 Micrometer 的推理性能指标收集器
 *
 * <p>集成 Micrometer 监控框架，提供全面的模型推理性能指标。
 * 这些指标可导出到 Prometheus、Datadog 或任何 Micrometer 兼容的监控系统。</p>
 *
 * <h2>注册的指标</h2>
 * <table>
 * <caption>Micrometer 指标列表</caption>
 *   <tr><th>指标名</th><th>类型</th><th>说明</th></tr>
 *   <tr><td>llama4j.inference.requests</td><td>Counter</td><td>总推理请求数</td></tr>
 *   <tr><td>llama4j.inference.latency</td><td>Timer</td><td>推理延迟分布</td></tr>
 *   <tr><td>llama4j.tokens.prompt</td><td>DistributionSummary</td><td>提示词 token 数分布</td></tr>
 *   <tr><td>llama4j.tokens.completion</td><td>DistributionSummary</td><td>补全 token 数分布</td></tr>
 *   <tr><td>llama4j.tokens.per.second</td><td>Gauge</td><td>当前生成速度（token/秒）</td></tr>
 *   <tr><td>llama4j.kv.cache.usage</td><td>Gauge</td><td>KV 缓存使用率</td></tr>
 *   <tr><td>llama4j.queue.depth</td><td>Gauge</td><td>请求队列深度</td></tr>
 *   <tr><td>llama4j.inference.errors</td><td>Counter</td><td>推理错误数</td></tr>
 * </table>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * MeterRegistry meterRegistry = new SimpleMeterRegistry();
 * LlamaMetrics metrics = new LlamaMetrics(meterRegistry);
 *
 * // 记录推理
 * metrics.recordInference(response);
 *
 * // 更新 KV 缓存使用率
 * metrics.updateKvCacheUsage(0.75);
 * }</pre>
 */
public final class LlamaMetrics {

    private static final Logger LOG = LoggerFactory.getLogger(LlamaMetrics.class);

    /* Micrometer 指标对象 */
    private final Counter inferenceRequests;
    private final Timer inferenceLatency;
    private final DistributionSummary promptTokens;
    private final DistributionSummary completionTokens;
    private final Counter inferenceErrors;

    /* Gauge 底层存储 — 使用 AtomicLong 保证原子性 */
    private final AtomicLong currentTps = new AtomicLong(0);
    private final AtomicLong kvCacheUsage = new AtomicLong(0);
    private final AtomicLong queueDepth = new AtomicLong(0);

    /**
     * 创建指标收集器。
     *
     * @param registry Micrometer 指标注册表
     */
    public LlamaMetrics(MeterRegistry registry) {
        Objects.requireNonNull(registry, "MeterRegistry 不能为 null");

        this.inferenceRequests = Counter.builder("llama4j.inference.requests")
            .description("总推理请求数")
            .register(registry);

        this.inferenceLatency = Timer.builder("llama4j.inference.latency")
            .description("推理延迟")
            .publishPercentiles(0.5, 0.9, 0.95, 0.99)
            .register(registry);

        this.promptTokens = DistributionSummary.builder("llama4j.tokens.prompt")
            .description("提示词 token 数")
            .register(registry);

        this.completionTokens = DistributionSummary.builder("llama4j.tokens.completion")
            .description("补全 token 数")
            .register(registry);

        this.inferenceErrors = Counter.builder("llama4j.inference.errors")
            .description("推理错误数")
            .register(registry);

        // 注册 Gauge 指标
        Gauge.builder("llama4j.tokens.per.second", currentTps, v -> v.get() / 100.0)
            .description("当前生成速度（token/秒）")
            .register(registry);

        Gauge.builder("llama4j.kv.cache.usage", kvCacheUsage, v -> v.get() / 100.0)
            .description("KV 缓存使用率")
            .register(registry);

        Gauge.builder("llama4j.queue.depth", queueDepth, AtomicLong::get)
            .description("请求队列深度")
            .register(registry);

        LOG.info("llama4j Micrometer 指标已注册");
    }

    /** 记录一次推理调用的指标 */
    public void recordInference(ChatResponse response) {
        inferenceRequests.increment();
        inferenceLatency.record(response.latencyMs(), TimeUnit.MILLISECONDS);
        promptTokens.record(response.promptTokens());
        completionTokens.record(response.completionTokens());
        currentTps.set((long) (response.tokensPerSecond() * 100)); // 以厘 tps 存储以保留精度
    }

    /** 记录推理错误 */
    public void recordError() {
        inferenceErrors.increment();
    }

    /** 更新 KV 缓存使用率（0.0 - 1.0） */
    public void updateKvCacheUsage(double usagePercent) {
        kvCacheUsage.set((long) (usagePercent * 100));
    }

    /** 更新请求队列深度 */
    public void updateQueueDepth(int depth) {
        queueDepth.set(depth);
    }

    /**
     * 创建自动记录指标的流式监听器适配器。
     *
     * @return 自动记录指标的 ChatStreamListener
     */
    public ChatStreamListener asStreamListener() {
        return new ChatStreamListener() {
            @Override
            public void onToken(String token) { /* token 级别指标在完成时统一记录 */ }

            @Override
            public void onComplete(ChatResponse response) {
                recordInference(response);
            }

            @Override
            public void onError(Throwable error) {
                recordError();
            }
        };
    }
}
