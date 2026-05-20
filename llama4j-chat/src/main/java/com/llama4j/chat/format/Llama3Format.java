package com.llama4j.chat.format;

import com.llama4j.chat.ChatFormat;
import com.llama4j.chat.Message;

import java.util.List;

/**
 * Llama 3 / 3.1 对话格式
 *
 * <p>Meta Llama 3 系列模型使用的对话格式，采用 header-based 标记系统。
 * 每条消息由 header（角色标识）和 body（内容）组成，以 eot_id 标记结束。</p>
 *
 * <h2>格式结构</h2>
 * <pre>
 * &lt;|begin_of_text|&gt;
 * &lt;|start_header_id|&gt;system&lt;|end_header_id|&gt;
 * {系统消息}&lt;|eot_id|&gt;
 * &lt;|start_header_id|&gt;user&lt;|end_header_id|&gt;
 * {用户消息}&lt;|eot_id|&gt;
 * &lt;|start_header_id|&gt;assistant&lt;|end_header_id|&gt;
 * {助手回复}&lt;|eot_id|&gt;
 * </pre>
 *
 * <h2>适用模型</h2>
 * <ul>
 *   <li>Meta Llama 3 8B / 70B</li>
 *   <li>Meta Llama 3.1 8B / 70B / 405B</li>
 *   <li>基于 Llama 3 架构的微调模型</li>
 * </ul>
 */
public final class Llama3Format implements ChatFormat {

    /* Llama 3 特殊标记定义 */
    private static final String BEGIN_OF_TEXT    = "<|begin_of_text|>";
    private static final String START_HEADER     = "<|start_header_id|>";
    private static final String END_HEADER       = "<|end_header_id|>";
    private static final String EOT              = "<|eot_id|>";

    @Override
    public String name() {
        return "llama3";
    }

    @Override
    public String render(List<Message> messages) {
        StringBuilder sb = new StringBuilder();
        sb.append(BEGIN_OF_TEXT);

        for (Message msg : messages) {
            // 每条消息：header(角色) + 内容 + 结束标记
            sb.append(START_HEADER).append(msg.role().value()).append(END_HEADER).append("\n\n");
            sb.append(msg.content()).append(EOT);
        }

        // 添加助手回复的起始标记，引导模型开始生成
        sb.append(START_HEADER).append("assistant").append(END_HEADER).append("\n\n");
        return sb.toString();
    }

    @Override
    public boolean matches(String template) {
        // 通过 start_header_id 标记识别 Llama 3 格式
        return template.contains("<|start_header_id|>") || template.contains("begin_of_text");
    }
}
