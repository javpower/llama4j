package com.llama4j.tools;

import java.util.List;
import java.util.Objects;

/**
 * 工具参数的不可变定义
 *
 * <p>描述工具方法的一个参数，包括名称、类型、描述和约束。
 * 这些信息会被转换为 JSON Schema，供 LLM 理解如何调用工具。</p>
 *
 * @param name        参数名称
 * @param description 参数描述（给 LLM 看）
 * @param type        JSON Schema 类型（如 "string"、"integer"、"boolean"）
 * @param required    是否为必填参数
 * @param enumValues  允许值的枚举列表（可选）
 */
public record ToolParameter(
    String name,
    String description,
    String type,
    boolean required,
    List<String> enumValues
) {

    public ToolParameter {
        Objects.requireNonNull(name, "参数名称不能为 null");
        if (name.isBlank()) {
            throw new IllegalArgumentException("参数名称不能为空白");
        }
        enumValues = List.copyOf(Objects.requireNonNullElse(enumValues, List.of()));
    }

    /** 便捷构造器：创建一个简单的必填字符串参数 */
    public ToolParameter(String name, String description) {
        this(name, description, "string", true, List.of());
    }
}
