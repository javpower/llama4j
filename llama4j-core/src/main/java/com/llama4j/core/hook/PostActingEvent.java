package com.llama4j.core.hook;

import java.util.List;
import java.util.Map;

/**
 * 工具执行后事件 — 只读通知
 *
 * <p>results 为 [{@code "name": ..., "content": ..., "success": ...}] 列表。</p>
 */
public final class PostActingEvent extends HookEvent {

    private final List<Map<String, Object>> results;

    public PostActingEvent(int round, List<Map<String, Object>> results) {
        super(round);
        this.results = results;
    }

    public List<Map<String, Object>> results() { return results; }
}
