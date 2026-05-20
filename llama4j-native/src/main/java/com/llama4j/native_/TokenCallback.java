package com.llama4j.native_;

/**
 * 流式生成回调接口
 *
 * <p>在流式推理模式下，模型每生成一个 token 就会调用此接口的
 * {@link #onToken} 方法，将文本片段实时传递给调用方。这对于
 * 聊天界面等需要逐字显示的场景至关重要——用户无需等待完整
 * 响应生成完毕，即可看到逐步输出的文字。</p>
 *
 * <h2>工作原理</h2>
 * <p>原生层的 generateStream 函数在生成循环中，每采样出一个 token，
 * 就通过 JNI 回调 Java 端的 TokenCallback.onToken() 方法。
 * 回调在推理线程上执行，因此实现应当：</p>
 * <ul>
 *   <li>快速返回，避免阻塞推理循环</li>
 *   <li>线程安全（如果涉及 UI 更新，应切换到 UI 线程）</li>
 *   <li>不抛出未检查异常</li>
 * </ul>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * context.generateStream(params, token -> {
 *     // 将 token 追加到文本区域
 *     textArea.appendText(token);
 *     // 刷新显示
 *     textArea.repaint();
 * });
 * }</pre>
 *
 * <h2>关于多字节字符</h2>
 * <p>单个 token 可能只对应一个 UTF-8 字符的一部分（尤其是中文），
 * 因此回调中的 token 字符串可能是不完整的。调用方需要自行
 * 缓冲和拼接，或在最终输出时进行 UTF-8 修正。</p>
 */
@FunctionalInterface
public interface TokenCallback {

    /**
     * 每生成一个 token 时被调用。
     *
     * @param token 生成的文本片段（可能是多字节字符的一部分）
     */
    void onToken(String token);
}
