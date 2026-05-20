package com.llama4j.tools.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 工具方法标记注解 — 将方法标记为 LLM 可调用的工具
 *
 * <p>被此注解标记的方法会被 {@link com.llama4j.tools.ToolRegistry} 自动发现
 * 并注册。LLM 在生成过程中可以通过结构化的工具调用输出来调用这些方法。</p>
 *
 * <h2>工作原理</h2>
 * <pre>
 * Java 方法 + @Tool 注解
 *     ↓ ToolRegistry.scanAndRegister()
 * ToolDefinition（工具定义，包含名称、描述、参数）
 *     ↓ 传递给 LLM
 * LLM 生成工具调用请求（JSON 格式）
 *     ↓ ToolRegistry.execute()
 * 反射调用原始 Java 方法
 *     ↓
 * ToolResult（工具执行结果）
 * </pre>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * &#64;Tool(name = "get_weather", description = "获取指定城市的当前天气信息")
 * public String getWeather(
 *     &#64;ToolParam(description = "城市名称", type = "string") String city
 * ) {
 *     return weatherService.getCurrentWeather(city);
 * }
 * }</pre>
 *
 * @see ToolParam
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Tool {

    /**
     * 工具的唯一名称。LLM 通过此名称引用工具。
     * 如果不指定，默认使用方法名。
     */
    String name() default "";

    /**
     * 工具功能的人类可读描述。
     * 此描述会提供给 LLM，帮助它判断何时使用该工具。
     * 描述应当清晰、具体，说明工具的用途和适用场景。
     */
    String description();
}
