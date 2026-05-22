package com.llama4j.tools;

import java.util.Collection;
import java.util.stream.Collectors;

/**
 * Markdown 风格的工具提示策略 — 默认实现
 *
 * <p>将工具描述组织为清晰的 Markdown 文本，包含工具列表、参数说明、
 * 调用格式和示例。适合大多数开源模型。</p>
 */
public class MarkdownPromptStrategy implements PromptStrategy {

    @Override
    public String buildToolPrompt(Collection<ToolDefinition> tools) {
        if (tools.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        sb.append("\n\n# 可用工具\n\n");
        sb.append("你可以使用以下工具来辅助回答问题。当你需要使用工具时，");
        sb.append("请严格按指定JSON格式输出工具调用，不要输出其他内容。\n\n");

        int index = 1;
        for (ToolDefinition tool : tools) {
            sb.append("## ").append(index++).append(". ").append(tool.name()).append("\n");
            sb.append("功能：").append(tool.description()).append("\n");
            if (!tool.parameters().isEmpty()) {
                sb.append("参数：\n");
                for (ToolParameter param : tool.parameters()) {
                    sb.append("  - ").append(param.name());
                    sb.append(" (").append(param.type());
                    if (param.required()) sb.append(", 必需");
                    else sb.append(", 可选");
                    sb.append("): ").append(param.description());
                    if (!param.enumValues().isEmpty()) {
                        sb.append("。可选值: ").append(String.join(", ", param.enumValues()));
                    }
                    sb.append("\n");
                }
            }
            sb.append("\n");
        }

        sb.append("# 调用格式\n\n");
        sb.append("需要使用工具时，只输出以下JSON，不要包含任何其他文字：\n");
        sb.append("```json\n");
        sb.append("[{\"name\": \"工具名称\", \"arguments\": {\"参数名\": \"参数值\"}}]\n");
        sb.append("```\n\n");

        sb.append("# 调用示例\n\n");
        for (ToolDefinition tool : tools) {
            sb.append("使用 ").append(tool.name()).append(" 时，输出：\n");
            sb.append("```json\n");
            sb.append("[{\"name\": \"").append(tool.name()).append("\", \"arguments\": {");
            if (!tool.parameters().isEmpty()) {
                String args = tool.parameters().stream()
                    .map(p -> "\"" + p.name() + "\": " + exampleValue(p))
                    .collect(Collectors.joining(", "));
                sb.append(args);
            }
            sb.append("}}]\n```\n\n");
        }

        sb.append("# 工作流程（严格遵守）\n");
        sb.append("1. 分析用户问题，判断是否需要使用工具\n");
        sb.append("2. 如果需要工具，输出JSON数组格式调用（可同时调用多个工具）\n");
        sb.append("3. 收到工具返回结果后，必须用自然语言总结回答用户，不要再输出JSON\n");
        sb.append("4. 绝对不要重复调用同一工具\n");
        sb.append("- 如果对话历史中已有该工具的结果，直接基于结果回答\n");
        sb.append("- 不需要工具时，直接用自然语言回答\n");

        return sb.toString();
    }

    private String exampleValue(ToolParameter param) {
        if (!param.enumValues().isEmpty()) {
            return "\"" + param.enumValues().get(0) + "\"";
        }
        return switch (param.type()) {
            case "integer", "number" -> "42";
            case "boolean" -> "true";
            default -> "\"" + param.description().replaceAll("[，。、；：].*$", "").trim() + "\"";
        };
    }
}
