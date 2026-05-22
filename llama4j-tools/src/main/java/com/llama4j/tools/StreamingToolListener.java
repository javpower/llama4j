package com.llama4j.tools;

import com.llama4j.core.ChatResponse;

/**
 * 流式工具调用监听器 — 用于 {@link ReActAgent#callStream}。
 *
 * <p>回调执行在推理线程上，不应阻塞。</p>
 */
public interface StreamingToolListener {

    /** 最终文本的增量 token（非工具调用轮次的内容） */
    void onContentToken(String token);

    /** 检测到工具调用 */
    void onToolCall(ToolCall toolCall);

    /** 工具执行完成 */
    void onToolResult(ToolResult result);

    /** 全部 ReAct 轮次完成 */
    void onComplete(ChatResponse response);

    /** 出错 */
    void onError(Throwable error);
}
