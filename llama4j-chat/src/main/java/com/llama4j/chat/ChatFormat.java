package com.llama4j.chat;

import java.util.List;

/**
 * 对话格式化策略接口
 *
 * <p>每个模型家族（Llama 3、ChatML、Gemma 等）使用不同的对话格式，
 * 包含独特的特殊标记和结构约定。本接口将这些差异封装在统一的 API 之后，
 * 实现策略模式（Strategy Pattern）。</p>
 *
 * <h2>实现要求</h2>
 * <ul>
 *   <li>实现类必须是无状态的（stateless）</li>
 *   <li>实现类必须是线程安全的</li>
 *   <li>{@link #render} 的输出应包含所有必要的特殊标记</li>
 *   <li>输出应以助手回复的前缀结尾，引导模型开始生成</li>
 * </ul>
 *
 * <h2>格式示例</h2>
 * <pre>
 * Llama 3:  &lt;|begin_of_text|&gt;&lt;|start_header_id|&gt;user&lt;|end_header_id|&gt;...
 * ChatML:   &lt;|im_start|&gt;user\n...&lt;|im_end|&gt;\n&lt;|im_start|&gt;assistant\n
 * Gemma:    &lt;start_of_turn&gt;user\n...&lt;end_of_turn&gt;\n&lt;start_of_turn&gt;model\n
 * </pre>
 */
public interface ChatFormat {

    /**
     * 获取格式的唯一标识符。
     *
     * @return 格式名称（如 "llama3"、"chatml"）
     */
    String name();

    /**
     * 将消息列表渲染为单一的提示词字符串。
     *
     * <p>输出应包含所有必要的特殊标记和结构标记，
     * 并以引导模型开始生成助手回复的前缀结尾。</p>
     *
     * @param messages 要渲染的对话消息列表
     * @return 格式化后的提示词字符串，可直接送入分词器
     */
    String render(List<Message> messages);

    /**
     * 判断此格式是否能处理给定的 chat template 字符串。
     *
     * <p>此方法被 {@link ChatTemplateEngine} 用于自动检测格式。
     * 实现应检查模板中是否包含能标识此格式的特征标记。</p>
     *
     * @param template GGUF 元数据中的原始 chat template 字符串
     * @return 如果此格式能识别该模板，返回 true
     */
    boolean matches(String template);
}
