package com.llama4j.chat.content;

/**
 * 模型思考过程内容块 — 用于支持 thinking/reasoning 的模型
 *
 * @param thinking 思考过程文本
 */
public record ThinkingBlock(String thinking) implements ContentBlock {
    public ThinkingBlock {
        if (thinking == null) thinking = "";
    }
}
