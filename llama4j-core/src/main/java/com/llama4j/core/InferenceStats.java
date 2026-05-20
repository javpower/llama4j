package com.llama4j.core;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.DoubleAdder;

/**
 * 线程安全的推理统计收集器
 *
 * <p>跨多次推理调用追踪性能指标，包括 token 吞吐量、平均延迟
 * 和 KV 缓存利用率。设计为 {@link ChatService} 内部的共享组件，
 * 支持并发更新和查询。</p>
 *
 * <h2>追踪的指标</h2>
 * <ul>
 *   <li>总推理次数</li>
 *   <li>总提示词/补全 token 数</li>
 *   <li>平均延迟和总延迟</li>
 *   <li>最近一次推理的 token/秒</li>
 *   <li>KV 缓存使用率</li>
 *   <li>当前请求队列深度</li>
 * </ul>
 *
 * <h2>线程安全设计</h2>
 * <p>使用 {@link AtomicLong} 和 {@link DoubleAdder} 实现无锁并发更新，
 * 避免在推理热路径上引入锁竞争。volatile 字段用于单写多读的场景
 * （如 KV 缓存使用率），确保可见性而不需要原子性。</p>
 */
public final class InferenceStats {

    /* 原子计数器 — 无锁并发更新 */
    private final AtomicLong totalInferences = new AtomicLong(0);
    private final AtomicLong totalPromptTokens = new AtomicLong(0);
    private final AtomicLong totalCompletionTokens = new AtomicLong(0);
    private final DoubleAdder totalLatencyMs = new DoubleAdder();

    /* volatile 字段 — 单写多读，保证可见性 */
    private volatile double lastTokensPerSecond = 0.0;
    private volatile double lastKvCacheUsage = 0.0;
    private volatile int queueDepth = 0;

    /**
     * 记录一次推理调用的统计数据。
     *
     * @param promptTokens     提示词 token 数
     * @param completionTokens 补全 token 数
     * @param tokensPerSecond  生成速度（token/秒）
     * @param latencyMs        推理延迟（毫秒）
     */
    public void recordInference(int promptTokens, int completionTokens,
                                double tokensPerSecond, long latencyMs) {
        totalInferences.incrementAndGet();
        totalPromptTokens.addAndGet(promptTokens);
        totalCompletionTokens.addAndGet(completionTokens);
        totalLatencyMs.add(latencyMs);
        lastTokensPerSecond = tokensPerSecond;
    }

    /** 更新 KV 缓存使用率（0.0 - 1.0） */
    public void updateKvCacheUsage(double usage) {
        this.lastKvCacheUsage = usage;
    }

    /** 更新请求队列深度 */
    public void updateQueueDepth(int depth) {
        this.queueDepth = depth;
    }

    /* ── 读取方法 ── */

    public long getTotalInferences()      { return totalInferences.get(); }
    public long getTotalPromptTokens()    { return totalPromptTokens.get(); }
    public long getTotalCompletionTokens(){ return totalCompletionTokens.get(); }
    public double getTotalLatencyMs()     { return totalLatencyMs.sum(); }
    public double getTokensPerSecond()    { return lastTokensPerSecond; }
    public double getKvCacheUsage()       { return lastKvCacheUsage; }
    public int getQueueDepth()            { return queueDepth; }

    /** 计算平均推理延迟（毫秒） */
    public double getAvgLatencyMs() {
        long count = totalInferences.get();
        return count > 0 ? totalLatencyMs.sum() / count : 0.0;
    }

    /** 计算平均 token/秒吞吐量 */
    public double getAvgTokensPerSecond() {
        long count = totalInferences.get();
        return count > 0 ? (double) totalCompletionTokens.get() / (totalLatencyMs.sum() / 1000.0) : 0.0;
    }

    @Override
    public String toString() {
        return String.format(
            "InferenceStats{推理次数=%d, 提示词=%d, 补全=%d, " +
            "平均延迟=%.1fms, 平均速度=%.1ftok/s, KV缓存=%.1f%%, 队列=%d}",
            getTotalInferences(), getTotalPromptTokens(), getTotalCompletionTokens(),
            getAvgLatencyMs(), getAvgTokensPerSecond(), getKvCacheUsage() * 100, getQueueDepth());
    }
}
