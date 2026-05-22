package com.llama4j.core;

import java.util.*;

/**
 * 供应商无关的工具定义 — 与具体 LLM API 解耦
 *
 * <p>以 JSON Schema 格式描述工具参数。各供应商的 Formatter
 * 将此 schema 转换为自己所需的格式。</p>
 */
public record ToolSchema(
    String name,
    String description,
    Map<String, Object> parameters
) {
    public ToolSchema {
        Objects.requireNonNull(name, "name 不能为 null");
        Objects.requireNonNull(description, "description 不能为 null");
        parameters = parameters != null
            ? Collections.unmodifiableMap(new LinkedHashMap<>(parameters))
            : Map.of();
    }

    /**
     * 从基本字段构造。
     */
    public static ToolSchema of(String name, String description, Map<String, Object> parameters) {
        return new ToolSchema(name, description, parameters);
    }

    /**
     * 构造无参数的工具 schema。
     */
    public static ToolSchema of(String name, String description) {
        return new ToolSchema(name, description, Map.of());
    }

    /**
     * 转为 OpenAI 兼容的 function schema。
     */
    public Map<String, Object> toOpenAISchema() {
        Map<String, Object> function = new LinkedHashMap<>();
        function.put("name", name);
        function.put("description", description);
        if (!parameters.isEmpty()) {
            function.put("parameters", parameters);
        }

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "function");
        schema.put("function", function);
        return schema;
    }
}
