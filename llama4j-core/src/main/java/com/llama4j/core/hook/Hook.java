package com.llama4j.core.hook;

/**
 * Agent 生命周期 Hook 接口 — 单一方法处理所有事件
 *
 * <p>Hook 按 priority 排序执行（越小优先级越高）。
 * 优先级约定：0-50 系统级、51-100 高优先级、101-500 普通、501+ 低优先级（日志/指标）。</p>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * Hook loggingHook = new Hook() {
 *     public void onEvent(HookEvent event) {
 *         switch (event) {
 *             case PostReasoningEvent e -> log.info("推理完成: {}", e.response().content());
 *             case PostActingEvent e    -> log.info("工具执行: {} 个结果", e.results().size());
 *             default -> {}
 *         }
 *     }
 *     public int priority() { return 500; }
 * };
 * }</pre>
 */
@FunctionalInterface
public interface Hook {

    /**
     * 处理 Agent 生命周期事件。
     *
     * @param event 生命周期事件
     */
    void onEvent(HookEvent event);

    /**
     * Hook 优先级 — 越小越先执行。
     *
     * @return 优先级（默认 100）
     */
    default int priority() { return 100; }
}
