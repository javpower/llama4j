package com.llama4j.native_;

/**
 * 序列化的 KV 缓存会话状态 — 不可变容器
 *
 * <p>封装了 KV 缓存的序列化字节数据。会话状态可以在对话过程中保存，
 * 并在稍后恢复，从而避免重新处理已有的提示词 token。这对于长对话
 * 场景特别有用——当上下文窗口接近满时，可以保存状态、清理缓存，
 * 然后在需要时恢复。</p>
 *
 * <h2>使用场景</h2>
 * <ul>
 *   <li>长对话的断点续传：保存当前对话状态，下次继续</li>
 *   <li>上下文窗口管理：当 KV 缓存接近 n_ctx 时，保存→清理→恢复</li>
 *   <li>多用户会话：为每个用户保存独立的会话状态</li>
 * </ul>
 *
 * <h2>注意事项</h2>
 * <p>会话状态与模型强绑定——使用不同模型或不同 n_ctx 参数加载的
 * 上下文无法恢复其他模型保存的会话状态。尝试这样做会导致未定义行为。</p>
 *
 * @param data 序列化的 KV 缓存字节数据
 */
public record SessionState(byte[] data) {

    /**
     * 紧凑构造器 — 参数校验
     *
     * @param data 序列化的 KV 缓存字节数据
     */
    public SessionState {
        if (data == null) {
            throw new IllegalArgumentException("会话数据不能为 null");
        }
    }

    /**
     * 返回会话数据的防御性副本。
     *
     * <p>由于 record 是不可变的，而数组是可变的，因此必须返回副本
     * 以防止外部代码修改内部状态。这是不可变对象持有可变字段时的
     * 标准防御性拷贝模式。</p>
     *
     * @return 序列化会话状态的副本
     */
    public byte[] data() {
        return data.clone();
    }

    /** @return 会话状态的大小（字节） */
    public int size() {
        return data.length;
    }
}
