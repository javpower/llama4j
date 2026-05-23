package com.llama4j.chat;

import com.llama4j.chat.content.ContentBlock;
import com.llama4j.chat.content.ImageBlock;
import com.llama4j.chat.content.TextBlock;
import com.llama4j.chat.content.ToolUseBlock;
import com.llama4j.chat.content.ToolResultBlock;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 单条聊天消息 — 不可变对象
 */
public record Message(Role role, String content, List<ContentBlock> blocks,
                      String toolCallId, List<Map<String, Object>> toolCalls) {

    public Message {
        Objects.requireNonNull(role, "role 不能为 null");
        if (content == null) content = "";
        blocks = blocks != null ? Collections.unmodifiableList(blocks) : List.of();
        toolCalls = toolCalls != null ? Collections.unmodifiableList(toolCalls) : List.of();
    }

    /** 纯文本消息 */
    public Message(Role role, String content) {
        this(role, content, content != null ? List.of(new TextBlock(content)) : List.of(), null, null);
    }

    /** 带 toolCallId 的消息 */
    public Message(Role role, String content, String toolCallId) {
        this(role, content, content != null ? List.of(new TextBlock(content)) : List.of(), toolCallId, null);
    }

    /** 带 toolCalls 的 assistant 消息 */
    public Message(Role role, String content, List<ContentBlock> blocks, List<Map<String, Object>> toolCalls) {
        this(role, content, blocks, null, toolCalls);
    }

    /** 多态内容消息 */
    public static Message of(Role role, List<ContentBlock> blocks) {
        Objects.requireNonNull(blocks, "blocks 不能为 null");
        String text = blocks.stream()
            .filter(b -> b instanceof TextBlock)
            .map(b -> ((TextBlock) b).text())
            .collect(Collectors.joining("\n"));
        return new Message(role, text, blocks, null, null);
    }

    public static Message system(String content) { return new Message(Role.SYSTEM, content); }
    public static Message user(String content) { return new Message(Role.USER, content); }
    public static Message assistant(String content) { return new Message(Role.ASSISTANT, content); }
    public static Message tool(String content) { return new Message(Role.TOOL, content); }
    public static Message toolResult(String content, String toolCallId) {
        return new Message(Role.TOOL, content, toolCallId);
    }

    /** 创建带 tool_calls 的 assistant 消息 */
    public static Message assistantWithToolCalls(String content, List<Map<String, Object>> toolCalls) {
        return new Message(Role.ASSISTANT, content,
            content != null ? List.of(new TextBlock(content)) : List.of(), null, toolCalls);
    }

    public boolean hasToolUse() {
        return blocks.stream().anyMatch(b -> b instanceof ToolUseBlock);
    }

    public boolean hasToolResult() {
        return blocks.stream().anyMatch(b -> b instanceof ToolResultBlock);
    }

    public boolean hasImages() {
        return blocks.stream().anyMatch(b -> b instanceof ImageBlock);
    }

    public List<ImageBlock> imageBlocks() {
        return blocks.stream()
            .filter(b -> b instanceof ImageBlock)
            .map(b -> (ImageBlock) b)
            .toList();
    }

    public static Message multimodal(Role role, String text, List<ImageBlock> images) {
        List<ContentBlock> blocks = new java.util.ArrayList<>();
        if (text != null && !text.isBlank()) blocks.add(new TextBlock(text));
        if (images != null) blocks.addAll(images);
        return of(role, blocks);
    }
}
