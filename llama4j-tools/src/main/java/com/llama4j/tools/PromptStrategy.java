package com.llama4j.tools;

import java.util.Collection;

/**
 * 工具提示生成策略 — 控制如何将工具描述注入到系统提示中
 *
 * <p>不同的模型对提示格式的偏好不同。通过此接口可以切换不同的
 * 提示策略，使工具调用更可靠。</p>
 */
public interface PromptStrategy {

    /**
     * 根据注册的工具定义生成系统提示文本。
     *
     * @param tools 已注册的工具定义
     * @return 要注入到系统消息中的提示文本；无工具时返回空字符串
     */
    String buildToolPrompt(Collection<ToolDefinition> tools);
}
