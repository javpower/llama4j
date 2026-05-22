package com.llama4j.chat.content;

/**
 * 工具执行结果内容块
 *
 * @param id      对应的 ToolUseBlock 的 ID
 * @param name    工具名称
 * @param content 执行结果文本
 * @param success 是否执行成功
 */
public record ToolResultBlock(String id, String name, String content, boolean success) implements ContentBlock {
    public ToolResultBlock {
        if (content == null) content = "";
    }

    public static ToolResultBlock success(String id, String name, String content) {
        return new ToolResultBlock(id, name, content, true);
    }

    public static ToolResultBlock failure(String id, String name, String error) {
        return new ToolResultBlock(id, name, error, false);
    }
}
