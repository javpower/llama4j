package com.llama4j.chat.content;

/**
 * 多态消息内容块的密封接口
 *
 * <p>一条消息可以包含多种类型的内容：纯文本、工具调用请求、工具执行结果、思考过程等。
 * 使用 sealed interface 确保类型穷举，配合 pattern matching 使用。</p>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * switch (block) {
 *     case TextBlock t      -> processText(t.text());
 *     case ToolUseBlock t   -> executeTool(t.name(), t.arguments());
 *     case ToolResultBlock t -> handleResult(t.content());
 *     case ThinkingBlock t  -> logThinking(t.thinking());
 * }
 * }</pre>
 */
public sealed interface ContentBlock
    permits TextBlock, ToolUseBlock, ToolResultBlock, ThinkingBlock, ImageBlock {
}
