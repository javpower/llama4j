package com.llama4j.chat.content;

import java.util.UUID;

/**
 * 工具调用请求内容块 — 模型请求调用某个工具
 *
 * @param id        工具调用的唯一标识
 * @param name      工具名称
 * @param arguments JSON 格式的参数字符串
 */
public record ToolUseBlock(String id, String name, String arguments) implements ContentBlock {
    public ToolUseBlock {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name 不能为空");
        if (arguments == null) arguments = "{}";
        if (id == null || id.isBlank()) id = "call_" + UUID.randomUUID().toString().substring(0, 8);
    }

    public static ToolUseBlock of(String name, String arguments) {
        return new ToolUseBlock(null, name, arguments);
    }
}
