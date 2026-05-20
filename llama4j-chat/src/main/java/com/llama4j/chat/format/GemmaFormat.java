package com.llama4j.chat.format;

import com.llama4j.chat.ChatFormat;
import com.llama4j.chat.Message;
import com.llama4j.chat.Role;

import java.util.List;

/**
 * Gemma 对话格式
 *
 * <p>Google Gemma 系列模型使用的对话格式。与 ChatML 类似，
 * 但使用不同的标记名称，且助手角色使用 "model" 而非 "assistant"。</p>
 *
 * <h2>格式结构</h2>
 * <pre>
 * &lt;start_of_turn&gt;user
 * {用户消息}&lt;end_of_turn&gt;
 * &lt;start_of_turn&gt;model
 * {模型回复}&lt;end_of_turn&gt;
 * </pre>
 *
 * <h2>适用模型</h2>
 * <ul>
 *   <li>Google Gemma 2 2B / 9B / 27B</li>
 *   <li>CodeGemma</li>
 *   <li>RecurrentGemma</li>
 * </ul>
 *
 * <p>注意：Gemma 使用 "model" 而非 "assistant" 作为 AI 角色标签，
 * 这是 Google 的命名约定，在渲染时需要特殊处理。</p>
 */
public final class GemmaFormat implements ChatFormat {

    private static final String START_OF_TURN = "<start_of_turn>";
    private static final String END_OF_TURN   = "<end_of_turn>";

    @Override
    public String name() {
        return "gemma";
    }

    @Override
    public String render(List<Message> messages) {
        StringBuilder sb = new StringBuilder();

        for (Message msg : messages) {
            // Gemma 使用 "model" 而非 "assistant"
            String roleLabel = (msg.role() == Role.ASSISTANT) ? "model" : msg.role().value();
            sb.append(START_OF_TURN).append(roleLabel).append("\n");
            sb.append(msg.content()).append(END_OF_TURN).append("\n");
        }

        // 模型回复起始标记
        sb.append(START_OF_TURN).append("model\n");
        return sb.toString();
    }

    @Override
    public boolean matches(String template) {
        return template.contains("<start_of_turn>") || template.contains("start_of_turn");
    }
}
