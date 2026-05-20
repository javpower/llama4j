package com.llama4j.tools;

import com.llama4j.tools.annotation.Tool;
import com.llama4j.tools.annotation.ToolParam;
import com.llama4j.exception.ToolNotFoundException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工具注册中心 — 注解驱动的工具发现与执行引擎
 *
 * <p>管理工具定义和对应的执行处理器。支持三种注册方式：</p>
 * <ol>
 *   <li><strong>注解扫描</strong> — 传入包含 {@link Tool} 注解方法的对象，
 *       自动发现并注册所有工具方法</li>
 *   <li><strong>编程式注册</strong> — 注册 {@link ToolDefinition} 和处理函数</li>
 *   <li><strong>手动定义</strong> — 分别注册定义和处理器</li>
 * </ol>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * ToolRegistry registry = new ToolRegistry();
 *
 * // 方式1：注解扫描注册
 * registry.scanAndRegister(new WeatherTools());
 *
 * // 方式2：编程式注册
 * registry.register(
 *     new ToolDefinition("calculator", "执行算术运算", List.of(...)),
 *     args -> String.valueOf(evaluate(args))
 * );
 *
 * // 执行工具调用
 * ToolResult result = registry.execute(ToolCall.of("get_weather", "{\"city\":\"北京\"}"));
 * }</pre>
 *
 * <h2>线程安全</h2>
 * <p>使用 {@link ConcurrentHashMap} 存储工具定义和处理器，
 * 支持并发注册和查询。工具执行本身是线程安全的（每次调用
 * 独立执行），但被调用的 Java 方法需要自行保证线程安全。</p>
 */
public final class ToolRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(ToolRegistry.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** 工具定义注册表 — 名称 → 定义 */
    private final ConcurrentHashMap<String, ToolDefinition> definitions = new ConcurrentHashMap<>();

    /** 工具处理器注册表 — 名称 → 执行函数 */
    private final ConcurrentHashMap<String, ToolHandler> handlers = new ConcurrentHashMap<>();

    /** 注解扫描的目标对象引用 — 用于反射调用 */
    private final ConcurrentHashMap<String, AnnotatedMethod> annotatedMethods = new ConcurrentHashMap<>();

    /**
     * 函数式接口 — 工具执行处理器
     */
    @FunctionalInterface
    public interface ToolHandler {
        String execute(JsonNode arguments);
    }

    /**
     * 注解方法引用 — 封装反射调用所需的信息
     */
    private record AnnotatedMethod(Object instance, Method method) {}

    /* ──────────────────────────────────────────
     *  注解扫描注册
     *  ────────────────────────────────────────── */

    /**
     * 扫描对象中所有带 {@link Tool} 注解的方法并注册。
     *
     * <p>扫描流程：</p>
     * <ol>
     *   <li>遍历对象的所有公开方法</li>
     *   <li>检查是否有 @Tool 注解</li>
     *   <li>从注解提取工具名称和描述</li>
     *   <li>从方法参数的 @ToolParam 注解提取参数定义</li>
     *   <li>创建 ToolDefinition 并注册</li>
     * </ol>
     *
     * @param target 包含 @Tool 注解方法的对象
     */
    public void scanAndRegister(Object target) {
        Objects.requireNonNull(target, "目标对象不能为 null");
        Class<?> clazz = target.getClass();

        for (Method method : clazz.getDeclaredMethods()) {
            Tool toolAnnotation = method.getAnnotation(Tool.class);
            if (toolAnnotation == null) continue;

            // 提取工具名称（注解指定 或 方法名）
            String toolName = toolAnnotation.name().isBlank()
                ? method.getName() : toolAnnotation.name();

            // 构建参数定义列表
            List<ToolParameter> params = buildParameterList(method);

            // 创建并注册工具定义
            ToolDefinition definition = new ToolDefinition(
                toolName, toolAnnotation.description(), params);
            definitions.put(toolName, definition);

            // 保存方法引用用于反射调用
            method.setAccessible(true);
            annotatedMethods.put(toolName, new AnnotatedMethod(target, method));

            LOG.info("已注册工具: {} ({} 个参数)", toolName, params.size());
        }
    }

    /* ──────────────────────────────────────────
     *  编程式注册
     *  ────────────────────────────────────────── */

    /**
     * 注册工具定义和对应的处理器。
     *
     * @param definition 工具定义
     * @param handler    工具执行处理器
     */
    public void register(ToolDefinition definition, ToolHandler handler) {
        Objects.requireNonNull(definition, "工具定义不能为 null");
        Objects.requireNonNull(handler, "处理器不能为 null");
        definitions.put(definition.name(), definition);
        handlers.put(definition.name(), handler);
        LOG.info("已注册工具: {}", definition.name());
    }

    /* ──────────────────────────────────────────
     *  工具执行
     *  ────────────────────────────────────────── */

    /**
     * 执行工具调用。
     *
     * <p>执行流程：</p>
     * <ol>
     *   <li>根据 toolName 查找注册的工具</li>
     *   <li>解析 JSON 参数</li>
     *   <li>调用对应的处理器或反射调用注解方法</li>
     *   <li>返回执行结果</li>
     * </ol>
     *
     * @param call 工具调用请求
     * @return 工具执行结果
     */
    public ToolResult execute(ToolCall call) {
        Objects.requireNonNull(call, "工具调用不能为 null");
        String toolName = call.toolName();

        // 查找工具
        if (!definitions.containsKey(toolName)) {
            throw new ToolNotFoundException(toolName);
        }

        try {
            // 解析参数
            JsonNode args = OBJECT_MAPPER.readTree(call.arguments());

            String result;

            // 优先使用编程式注册的处理器
            ToolHandler handler = handlers.get(toolName);
            if (handler != null) {
                result = handler.execute(args);
            } else {
                // 回退到注解方法的反射调用
                result = invokeAnnotatedMethod(toolName, args);
            }

            return ToolResult.success(call.id(), result);

        } catch (Exception e) {
            LOG.error("工具执行失败: {} - {}", toolName, e.getMessage());
            return ToolResult.failure(call.id(), "执行错误: " + e.getMessage());
        }
    }

    /* ──────────────────────────────────────────
     *  查询方法
     *  ────────────────────────────────────────── */

    /** 获取所有已注册的工具定义 */
    public Collection<ToolDefinition> getDefinitions() {
        return Collections.unmodifiableCollection(definitions.values());
    }

    /** 根据名称获取工具定义 */
    public Optional<ToolDefinition> getDefinition(String name) {
        return Optional.ofNullable(definitions.get(name));
    }

    /** 获取已注册工具数量 */
    public int size() {
        return definitions.size();
    }

    /** 取消注册指定工具 */
    public void unregister(String name) {
        definitions.remove(name);
        handlers.remove(name);
        annotatedMethods.remove(name);
    }

    /* ──────────────────────────────────────────
     *  内部辅助方法
     *  ────────────────────────────────────────── */

    /** 从方法参数的 @ToolParam 注解构建参数定义列表 */
    private List<ToolParameter> buildParameterList(Method method) {
        List<ToolParameter> params = new ArrayList<>();
        Parameter[] methodParams = method.getParameters();

        for (Parameter mp : methodParams) {
            ToolParam paramAnnotation = mp.getAnnotation(ToolParam.class);

            String paramName = mp.getName();
            String description = paramAnnotation != null ? paramAnnotation.description() : "";
            String type = paramAnnotation != null ? paramAnnotation.type() : "string";
            boolean required = paramAnnotation == null || paramAnnotation.required();
            List<String> enumValues = paramAnnotation != null && paramAnnotation.enumValues().length > 0
                ? List.of(paramAnnotation.enumValues()) : List.of();

            params.add(new ToolParameter(paramName, description, type, required, enumValues));
        }

        return params;
    }

    /** 通过反射调用注解方法 */
    private String invokeAnnotatedMethod(String toolName, JsonNode args) throws Exception {
        AnnotatedMethod am = annotatedMethods.get(toolName);
        if (am == null) {
            throw new ToolNotFoundException(toolName);
        }

        Method method = am.method();
        Object[] methodArgs = new Object[method.getParameterCount()];
        Parameter[] parameters = method.getParameters();

        for (int i = 0; i < parameters.length; i++) {
            String paramName = parameters[i].getName();
            JsonNode argValue = args.has(paramName) ? args.get(paramName) : null;

            if (argValue != null && !argValue.isNull()) {
                methodArgs[i] = convertArgument(argValue, parameters[i].getType());
            } else {
                methodArgs[i] = getDefaultValue(parameters[i].getType());
            }
        }

        Object returnValue = method.invoke(am.instance(), methodArgs);
        return returnValue != null ? returnValue.toString() : "null";
    }

    /** 将 JSON 值转换为目标 Java 类型 */
    private static Object convertArgument(JsonNode value, Class<?> targetType) {
        if (targetType == String.class)   return value.asText();
        if (targetType == int.class)      return value.asInt();
        if (targetType == Integer.class)  return value.asInt();
        if (targetType == long.class)     return value.asLong();
        if (targetType == Long.class)     return value.asLong();
        if (targetType == double.class)   return value.asDouble();
        if (targetType == Double.class)   return value.asDouble();
        if (targetType == boolean.class)  return value.asBoolean();
        if (targetType == Boolean.class)  return value.asBoolean();
        return value.asText(); // 默认转字符串
    }

    /** 获取基本类型的默认值 */
    private static Object getDefaultValue(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == double.class) return 0.0;
        if (type == float.class) return 0.0f;
        return null;
    }
}
