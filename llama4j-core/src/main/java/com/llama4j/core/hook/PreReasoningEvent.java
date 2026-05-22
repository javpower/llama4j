package com.llama4j.core.hook;

import com.llama4j.chat.Message;

import java.util.List;

/**
 * 推理前事件 — 可修改消息列表
 */
public final class PreReasoningEvent extends HookEvent {

    private List<Message> messages;

    public PreReasoningEvent(int round, List<Message> messages) {
        super(round);
        this.messages = messages;
    }

    public List<Message> messages() { return messages; }
    public void setMessages(List<Message> messages) { this.messages = messages; }
}
