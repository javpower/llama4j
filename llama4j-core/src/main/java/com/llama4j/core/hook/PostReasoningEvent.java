package com.llama4j.core.hook;

import com.llama4j.core.ChatResponse;

/**
 * 推理后事件 — 只读通知
 */
public final class PostReasoningEvent extends HookEvent {

    private final ChatResponse response;

    public PostReasoningEvent(int round, ChatResponse response) {
        super(round);
        this.response = response;
    }

    public ChatResponse response() { return response; }
}
