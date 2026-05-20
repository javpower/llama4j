package com.llama4j.core;

import com.llama4j.chat.Message;
import com.llama4j.native_.LlamaContext;

import java.util.List;
import java.util.Objects;

/**
 * 对话模板工具 — 便捷的模板渲染方法
 *
 * <p>提供从 {@link LlamaContext#applyChatTemplate} 到 {@link Message} 列表的
 * 便捷桥接，避免手动构建 String 数组。</p>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * try (LlamaContext ctx = new LlamaContext(modelPath, ModelParams.DEFAULT)) {
 *     List<Message> messages = List.of(
 *         Message.system("你是一个有帮助的AI助手。"),
 *         Message.user("你好！")
 *     );
 *     String prompt = ChatTemplateUtil.applyTemplate(ctx, messages, true);
 * }
 * }</pre>
 */
public final class ChatTemplateUtil {

    private ChatTemplateUtil() {}

    /**
     * 使用模型内嵌的对话模板渲染消息列表。
     *
     * @param context       模型上下文
     * @param messages      消息列表
     * @param addAssistant 是否在末尾添加助手前缀
     * @return 渲染后的提示词字符串
     */
    public static String applyTemplate(LlamaContext context, List<Message> messages, boolean addAssistant) {
        Objects.requireNonNull(context, "LlamaContext 不能为 null");
        Objects.requireNonNull(messages, "消息列表不能为 null");
        String[] roles = messages.stream().map(m -> m.role().value()).toArray(String[]::new);
        String[] contents = messages.stream().map(Message::content).toArray(String[]::new);
        return context.applyChatTemplate(roles, contents, addAssistant);
    }
}
