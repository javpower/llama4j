package com.llama4j.chat;

import com.llama4j.chat.content.ContentBlock;
import com.llama4j.chat.content.TextBlock;
import com.llama4j.chat.content.ToolUseBlock;
import com.llama4j.chat.content.ToolResultBlock;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 单条聊天消息 — 不可变对象
 *
 * <p>每条消息由 {@link Role} 和内容组成。支持纯文本和 {@link ContentBlock} 列表两种表示。</p>
 *
 * @param role    消息角色
 * @param content 文本内容
 * @param blocks  多态内容块列表
 */
public record Message(Role role, String content, List<ContentBlock> blocks) {

    public Message {
        Objects.requireNonNull(role, "role 不能为 null");
        if (content == null) content = "";
        blocks = blocks != null ? Collections.unmodifiableList(blocks) : List.of();
    }

    /** 纯文本消息 */
    public Message(Role role, String content) {
        this(role, content, content != null ? List.of(new TextBlock(content)) : List.of());
    }

    /** 多态内容消息 */
    public static Message of(Role role, List<ContentBlock> blocks) {
        Objects.requireNonNull(blocks, "blocks 不能为 null");
        String text = blocks.stream()
            .filter(b -> b instanceof TextBlock)
            .map(b -> ((TextBlock) b).text())
            .collect(Collectors.joining("\n"));
        return new Message(role, text, blocks);
    }

    public static Message system(String content) { return new Message(Role.SYSTEM, content); }
    public static Message user(String content) { return new Message(Role.USER, content); }
    public static Message assistant(String content) { return new Message(Role.ASSISTANT, content); }
    public static Message tool(String content) { return new Message(Role.TOOL, content); }

    public boolean hasToolUse() {
        return blocks.stream().anyMatch(b -> b instanceof ToolUseBlock);
    }

    public boolean hasToolResult() {
        return blocks.stream().anyMatch(b -> b instanceof ToolResultBlock);
    }
}
