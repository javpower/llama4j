package com.llama4j.core;

/**
 * 流式聊天补全监听器接口
 *
 * <p>在流式推理模式下，模型逐 token 生成响应。此接口定义了三个回调方法，
 * 分别处理 token 到达、生成完成和错误发生三种事件。</p>
 *
 * <h2>回调时序</h2>
 * <pre>
 * onToken("你") → onToken("好") → onToken("！") → ... → onComplete(response)
 *                                                          ↑
 *                                              如果出错：onError(exception)
 * </pre>
 *
 * <h2>实现注意事项</h2>
 * <ul>
 *   <li>回调在推理线程上执行，不应执行耗时操作</li>
 *   <li>UI 更新应切换到 UI 线程（如 SwingUtilities.invokeLater）</li>
 *   <li>onToken 可能收到不完整的 UTF-8 字符，需要缓冲拼接</li>
 *   <li>onComplete 和 onError 互斥，只会调用其中一个</li>
 * </ul>
 */
public interface ChatStreamListener {

    /**
     * 每生成一个 token 时调用。
     *
     * @param token 生成的文本片段
     */
    void onToken(String token);

    /**
     * 生成完成时调用。
     *
     * @param response 包含完整文本和性能指标的响应对象
     */
    void onComplete(ChatResponse response);

    /**
     * 生成过程中发生错误时调用。
     *
     * @param error 导致失败的异常
     */
    void onError(Throwable error);
}
