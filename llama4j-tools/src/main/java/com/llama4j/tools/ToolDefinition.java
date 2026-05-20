package com.llama4j.tools;

import java.util.List;
import java.util.Objects;

/**
 * 可调用工具的不可变定义
 *
 * <p>包含工具的元数据（名称、描述）和参数 schema。这是从
 * {@link com.llama4j.tools.annotation.Tool} 注解构建的运行时表示，
 * 用于 LLM 提示生成和工具调用执行。</p>
 *
 * <h2>在函数调用流程中的角色</h2>
 * <pre>
 * ToolDefinition → 转换为 OpenAI schema → 嵌入 LLM 提示
 *                                          ↓
 *                              LLM 生成 ToolCall → 执行 → ToolResult
 * </pre>
 *
 * @param name        工具的唯一名称
 * @param description 工具功能描述（给 LLM 看）
 * @param parameters  参数定义列表
 */
public record ToolDefinition(
    String name,
    String description,
    List<ToolParameter> parameters
) {

    public ToolDefinition {
        Objects.requireNonNull(name, "工具名称不能为 null");
        Objects.requireNonNull(description, "工具描述不能为 null");
        if (name.isBlank()) {
            throw new IllegalArgumentException("工具名称不能为空白");
        }
        parameters = List.copyOf(Objects.requireNonNullElse(parameters, List.of()));
    }

    /**
     * 转换为 OpenAI 兼容的 JSON Schema 片段。
     *
     * <p>生成的 schema 遵循 OpenAI Function Calling 协议格式，
     * 可直接嵌入到 chat completion 请求的 tools 字段中。</p>
     *
     * @return 表示函数定义的 Map
     */
    public java.util.Map<String, Object> toOpenAISchema() {
        java.util.Map<String, Object> function = new java.util.LinkedHashMap<>();
        function.put("name", name);
        function.put("description", description);

        if (!parameters.isEmpty()) {
            java.util.Map<String, Object> params = new java.util.LinkedHashMap<>();
            params.put("type", "object");

            java.util.Map<String, Object> properties = new java.util.LinkedHashMap<>();
            List<String> required = new java.util.ArrayList<>();

            for (ToolParameter param : parameters) {
                java.util.Map<String, Object> prop = new java.util.LinkedHashMap<>();
                prop.put("type", param.type());
                prop.put("description", param.description());
                if (!param.enumValues().isEmpty()) {
                    prop.put("enum", param.enumValues());
                }
                properties.put(param.name(), prop);
                if (param.required()) {
                    required.add(param.name());
                }
            }

            params.put("properties", properties);
            if (!required.isEmpty()) {
                params.put("required", required);
            }
            function.put("parameters", params);
        }

        java.util.Map<String, Object> schema = new java.util.LinkedHashMap<>();
        schema.put("type", "function");
        schema.put("function", function);
        return schema;
    }
}
