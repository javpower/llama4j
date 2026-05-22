package com.llama4j.chat.content;

/**
 * 纯文本内容块
 *
 * @param text 文本内容
 */
public record TextBlock(String text) implements ContentBlock {
    public TextBlock {
        if (text == null) throw new IllegalArgumentException("text 不能为 null");
    }
}
