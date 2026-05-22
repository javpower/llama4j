package com.llama4j.core.hook;

/**
 * Hook 事件基类 — Agent 生命周期中产生的所有事件
 *
 * <p>事件分为两类：</p>
 * <ul>
 *   <li><strong>可修改事件</strong>（Pre*）：可以修改消息内容、参数等</li>
 *   <li><strong>通知事件</strong>（Post*、Error）：只读，用于日志、指标等</li>
 * </ul>
 *
 * <p>非 sealed — 允许子模块（如 tools）定义自己的事件类型。</p>
 */
public abstract class HookEvent {

    private final int round;
    private final long timestamp;

    protected HookEvent(int round) {
        this.round = round;
        this.timestamp = System.currentTimeMillis();
    }

    public int round() { return round; }
    public long timestamp() { return timestamp; }
}
