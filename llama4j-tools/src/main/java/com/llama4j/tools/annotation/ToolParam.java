package com.llama4j.tools.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 工具参数描述注解 — 为 LLM 提供参数元数据
 *
 * <p>此注解为工具方法的参数提供描述信息，LLM 利用这些信息
 * 理解应该传递什么值。它直接映射到 OpenAI 函数调用 schema
 * 中的 "parameters" 部分。</p>
 *
 * <h2>参数说明</h2>
 * <ul>
 *   <li>{@code description} — 参数的用途说明，帮助 LLM 理解应传什么值</li>
 *   <li>{@code required} — 是否必须提供。如果为 true，LLM 必须为此参数赋值</li>
 *   <li>{@code type} — JSON Schema 类型（如 "string"、"integer"、"boolean"）</li>
 *   <li>{@code enumValues} — 允许值的枚举列表，限制参数取值范围</li>
 * </ul>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * &#64;Tool(name = "search", description = "搜索网页")
 * public String search(
 *     &#64;ToolParam(description = "搜索关键词", type = "string") String query,
 *     &#64;ToolParam(description = "结果数量", type = "integer", required = false) int count
 * ) {
 *     return searchService.search(query, count);
 * }
 * }</pre>
 *
 * @see Tool
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface ToolParam {

    /** 参数的人类可读描述 */
    String description() default "";

    /** 是否为必填参数（默认 true） */
    boolean required() default true;

    /** JSON Schema 类型（默认 "string"） */
    String type() default "string";

    /** 允许值的枚举列表 */
    String[] enumValues() default {};
}
