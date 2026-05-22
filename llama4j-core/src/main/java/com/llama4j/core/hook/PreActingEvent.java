package com.llama4j.core.hook;

import java.util.List;
import java.util.Map;

/**
 * 工具执行前事件 — 可修改工具调用参数
 *
 * <p>pendingCalls 为 [{@code "name": ..., "arguments": ...}] 列表。</p>
 */
public final class PreActingEvent extends HookEvent {

    private List<Map<String, String>> pendingCalls;

    public PreActingEvent(int round, List<Map<String, String>> pendingCalls) {
        super(round);
        this.pendingCalls = pendingCalls;
    }

    public List<Map<String, String>> pendingCalls() { return pendingCalls; }
    public void setPendingCalls(List<Map<String, String>> calls) { this.pendingCalls = calls; }
}
