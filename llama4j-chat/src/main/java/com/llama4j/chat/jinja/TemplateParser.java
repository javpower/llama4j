package com.llama4j.chat.jinja;

import java.util.*;

/**
 * Jinja2 模板最小子集解析器 — 专为 GGUF chat template 设计
 *
 * <p>许多 GGUF 模型在元数据中嵌入了 Jinja2 格式的对话模板。
 * 本解析器实现了 Jinja2 的一个足够大的子集，能够渲染主流模型
 * 中最常见的模板模式。</p>
 *
 * <h2>支持的 Jinja2 特性</h2>
 * <ul>
 *   <li>{@code {% for item in list %} ... {% endfor %}} — 列表迭代</li>
 *   <li>{@code {% if condition %} ... {% elif %} ... {% else %} ... {% endif %}} — 条件分支</li>
 *   <li>{@code {{ variable }}} — 变量插值</li>
 *   <li>{@code {{ variable | filter }}} — 基础过滤器（lower、trim、length）</li>
 *   <li>{@code {% set var = value %}} — 变量赋值</li>
 *   <li>点号访问嵌套属性：{@code message.role}</li>
 * </ul>
 *
 * <h2>不支持的高级特性</h2>
 * <ul>
 *   <li>宏定义（macro）、模板继承（extends）、包含（include）</li>
 *   <li>复杂表达式（算术运算、函数调用）</li>
 *   <li>自定义过滤器或扩展</li>
 * </ul>
 *
 * <h2>设计说明</h2>
 * <p>本解析器采用递归下降（recursive descent）方式实现，
 * 将模板字符串解析为节点树（AST），然后对 AST 进行求值渲染。
 * 这种两阶段设计使得解析和渲染可以独立测试和优化。</p>
 */
public final class TemplateParser {

    private TemplateParser() {} // 工具类，禁止实例化

    /**
     * 使用给定的上下文变量渲染 Jinja2 模板。
     *
     * @param template Jinja2 模板字符串
     * @param context  变量上下文（如 "messages" 列表、"bos_token"、"eos_token"）
     * @return 渲染后的字符串
     */
    public static String render(String template, Map<String, Object> context) {
        List<Node> nodes = parse(template);
        Map<String, Object> mutableContext = new HashMap<>(context);
        StringBuilder output = new StringBuilder();
        for (Node node : nodes) {
            output.append(node.evaluate(mutableContext));
        }
        return output.toString();
    }

    /* ──────────────────────────────────────────
     *  AST 节点定义
     *  ────────────────────────────────────────── */

    /** AST 节点基接口 */
    private interface Node {
        String evaluate(Map<String, Object> context);
    }

    /** 纯文本节点 — 直接输出字面量 */
    private record TextNode(String text) implements Node {
        @Override
        public String evaluate(Map<String, Object> context) {
            return text;
        }
    }

    /** 变量插值节点 — 如 {{ message.role }} */
    private record VariableNode(String expression) implements Node {
        @Override
        public String evaluate(Map<String, Object> context) {
            Object value = resolveExpression(expression, context);
            return value != null ? value.toString() : "";
        }
    }

    /** for 循环节点 */
    private record ForNode(String varName, String iterableExpr, List<Node> body) implements Node {
        @Override
        @SuppressWarnings("unchecked")
        public String evaluate(Map<String, Object> context) {
            Object iterable = resolveExpression(iterableExpr, context);
            if (!(iterable instanceof List)) return "";

            StringBuilder sb = new StringBuilder();
            for (Object item : (List<Object>) iterable) {
                Map<String, Object> scopedContext = new HashMap<>(context);
                scopedContext.put(varName, item);
                for (Node child : body) {
                    sb.append(child.evaluate(scopedContext));
                }
            }
            return sb.toString();
        }
    }

    /** if 条件节点 */
    private record IfNode(String condition, List<Node> thenBody,
                          List<Node> elseBody) implements Node {
        @Override
        public String evaluate(Map<String, Object> context) {
            boolean result = evaluateCondition(condition, context);
            List<Node> branch = result ? thenBody : elseBody;
            StringBuilder sb = new StringBuilder();
            for (Node node : branch) {
                sb.append(node.evaluate(context));
            }
            return sb.toString();
        }
    }

    /** set 赋值节点 */
    private record SetNode(String varName, String valueExpr) implements Node {
        @Override
        public String evaluate(Map<String, Object> context) {
            Object value = resolveExpression(valueExpr, context);
            context.put(varName, value);
            return "";
        }
    }

    /* ──────────────────────────────────────────
     *  表达式求值
     *  ────────────────────────────────────────── */

    /**
     * 解析点号表达式（如 "message.role"）。
     *
     * <p>支持嵌套属性访问和 Map/List 索引：</p>
     * <ul>
     *   <li>{@code message} → 从 context 中获取 "message"</li>
     *   <li>{@code message.role} → 先获取 message，再获取其 "role" 属性</li>
     *   <li>{@code bos_token + eos_token} → 字符串拼接</li>
     * </ul>
     */
    private static Object resolveExpression(String expr, Map<String, Object> context) {
        String trimmed = expr.trim();

        // 处理过滤器：{{ value | filter }}
        if (trimmed.contains("|")) {
            String[] parts = trimmed.split("\\|", 2);
            Object value = resolveExpression(parts[0].trim(), context);
            String filter = parts[1].trim();
            return applyFilter(value, filter);
        }

        // 处理字符串拼接：{{ a + b }}
        if (trimmed.contains("+")) {
            String[] parts = trimmed.split("\\+", 2);
            Object left = resolveExpression(parts[0].trim(), context);
            Object right = resolveExpression(parts[1].trim(), context);
            return (left != null ? left.toString() : "") + (right != null ? right.toString() : "");
        }

        // 处理字符串字面量
        if (trimmed.startsWith("'") && trimmed.endsWith("'")) {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        if (trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            return trimmed.substring(1, trimmed.length() - 1);
        }

        // 处理布尔字面量
        if ("true".equals(trimmed)) return true;
        if ("false".equals(trimmed)) return false;

        // 处理点号属性访问
        String[] segments = trimmed.split("\\.");
        Object current = context;
        for (String segment : segments) {
            if (current == null) return null;
            if (current instanceof Map) {
                current = ((Map<String, Object>) current).get(segment);
            } else {
                return null; // 不支持非 Map 对象的属性访问
            }
        }
        return current;
    }

    /** 应用 Jinja2 过滤器 */
    private static Object applyFilter(Object value, String filter) {
        return switch (filter) {
            case "lower"  -> value != null ? value.toString().toLowerCase() : "";
            case "upper"  -> value != null ? value.toString().toUpperCase() : "";
            case "trim"   -> value != null ? value.toString().trim() : "";
            case "length" -> value != null ? value.toString().length() : 0;
            case "string" -> value != null ? value.toString() : "";
            default       -> value;
        };
    }

    /** 评估条件表达式 */
    private static boolean evaluateCondition(String condition, Map<String, Object> context) {
        String trimmed = condition.trim();

        // 处理 == 比较
        if (trimmed.contains("==")) {
            String[] parts = trimmed.split("==", 2);
            Object left = resolveExpression(parts[0].trim(), context);
            Object right = resolveExpression(parts[1].trim(), context);
            return Objects.equals(left, right);
        }

        // 处理 != 比较
        if (trimmed.contains("!=")) {
            String[] parts = trimmed.split("!=", 2);
            Object left = resolveExpression(parts[0].trim(), context);
            Object right = resolveExpression(parts[1].trim(), context);
            return !Objects.equals(left, right);
        }

        // 处理 not 取反
        if (trimmed.startsWith("not ")) {
            Object value = resolveExpression(trimmed.substring(4), context);
            return !isTruthy(value);
        }

        // 默认：真值测试
        Object value = resolveExpression(trimmed, context);
        return isTruthy(value);
    }

    /** Jinja2 真值判断规则 */
    private static boolean isTruthy(Object value) {
        if (value == null) return false;
        if (value instanceof Boolean) return (Boolean) value;
        if (value instanceof String) return !((String) value).isEmpty();
        if (value instanceof Number) return ((Number) value).doubleValue() != 0;
        if (value instanceof Collection) return !((Collection<?>) value).isEmpty();
        return true;
    }

    /* ──────────────────────────────────────────
     *  模板解析器
     *  ────────────────────────────────────────── */

    /**
     * 将模板字符串解析为 AST 节点列表。
     */
    private static List<Node> parse(String template) {
        Tokenizer tokenizer = new Tokenizer(template);
        return parseNodes(tokenizer);
    }

    private static List<Node> parseNodes(Tokenizer tokenizer) {
        List<Node> nodes = new ArrayList<>();
        while (tokenizer.hasNext()) {
            String token = tokenizer.peek();
            if (token == null) break;

            if (token.startsWith("{{") && token.endsWith("}}")) {
                tokenizer.next();
                String expr = token.substring(2, token.length() - 2);
                nodes.add(new VariableNode(expr));
            } else if (token.startsWith("{%") && token.endsWith("%}")) {
                String content = token.substring(2, token.length() - 2).trim();
                String keyword = content.split("\\s+")[0];

                if ("for".equals(keyword)) {
                    tokenizer.next();
                    nodes.add(parseFor(content, tokenizer));
                } else if ("if".equals(keyword)) {
                    tokenizer.next();
                    nodes.add(parseIf(content, tokenizer));
                } else if ("set".equals(keyword)) {
                    tokenizer.next();
                    nodes.add(parseSet(content));
                } else if ("else".equals(keyword) || "elif".equals(keyword)
                        || "endif".equals(keyword) || "endfor".equals(keyword)) {
                    // 控制流边界 — 不消费，交由调用方处理
                    break;
                } else {
                    tokenizer.next();
                    nodes.add(new TextNode(token));
                }
            } else {
                tokenizer.next();
                nodes.add(new TextNode(token));
            }
        }
        return nodes;
    }

    /** 解析 for 循环块 */
    private static ForNode parseFor(String content, Tokenizer tokenizer) {
        // 格式: for var in iterable
        String[] parts = content.split("\\s+");
        String varName = parts[1];
        String iterableExpr = content.substring(content.indexOf(" in ") + 4).trim();
        List<Node> body = parseBlock(tokenizer, "endfor");
        // 消费 endfor
        if (tokenizer.hasNext()) {
            String token = tokenizer.peek();
            if (token != null && token.startsWith("{%") && token.endsWith("%}")) {
                String tagContent = token.substring(2, token.length() - 2).trim();
                if ("endfor".equals(tagContent)) {
                    tokenizer.next();
                }
            }
        }
        return new ForNode(varName, iterableExpr, body);
    }

    /** 解析 if 条件块 */
    private static IfNode parseIf(String content, Tokenizer tokenizer) {
        String condition = content.substring(3).trim(); // 去掉 "if "
        List<Node> thenBody = new ArrayList<>();
        List<Node> elseBody = new ArrayList<>();

        // 解析 then 分支
        thenBody.addAll(parseNodes(tokenizer));

        // 处理 else / elif / endif
        while (tokenizer.hasNext()) {
            String token = tokenizer.peek();
            if (token == null) break;

            if (token.startsWith("{%") && token.endsWith("%}")) {
                String tagContent = token.substring(2, token.length() - 2).trim();
                if ("else".equals(tagContent)) {
                    tokenizer.next(); // 消费 else
                    elseBody.addAll(parseNodes(tokenizer));
                } else if (tagContent.startsWith("elif")) {
                    tokenizer.next(); // 消费 elif
                    String elifCondition = tagContent.substring(5).trim();
                    // 递归处理剩余的 elif/else/endif
                    IfNode elifNode = parseIf("if " + elifCondition, tokenizer);
                    elseBody.add(elifNode);
                } else if ("endif".equals(tagContent)) {
                    tokenizer.next(); // 消费 endif
                    break;
                } else {
                    break;
                }
            } else {
                break;
            }
        }

        return new IfNode(condition, thenBody, elseBody);
    }

    /** 解析 set 赋值 */
    private static SetNode parseSet(String content) {
        // 格式: set var = expression
        String assignment = content.substring(4).trim(); // 去掉 "set "
        String[] parts = assignment.split("=", 2);
        return new SetNode(parts[0].trim(), parts[1].trim());
    }

    /** 解析直到遇到结束标签或控制流边界 */
    private static List<Node> parseBlock(Tokenizer tokenizer, String endTag) {
        List<Node> nodes = new ArrayList<>();
        while (tokenizer.hasNext()) {
            String token = tokenizer.peek();
            if (token == null) break;

            if (token.startsWith("{%") && token.endsWith("%}")) {
                String tagContent = token.substring(2, token.length() - 2).trim();
                if (endTag.equals(tagContent)
                        || "else".equals(tagContent) || "elif".equals(tagContent)) {
                    // 结束标签或控制流边界 — 不消费，交由调用方处理
                    break;
                }
            }
            List<Node> parsed = parseNodes(tokenizer);
            if (parsed.isEmpty()) break; // parseNodes 在边界处返回空列表
            nodes.addAll(parsed);
        }
        return nodes;
    }

    /* ──────────────────────────────────────────
     *  简单词法分析器
     *  ────────────────────────────────────────── */

    /**
     * 将模板字符串分割为文本、变量插值和控制流标签三种 token。
     */
    private static class Tokenizer {
        private final String template;
        private int pos = 0;

        Tokenizer(String template) {
            this.template = template;
        }

        boolean hasNext() {
            return pos < template.length();
        }

        String peek() {
            int savedPos = pos;
            String result = next();
            pos = savedPos;
            return result;
        }

        String next() {
            if (pos >= template.length()) return null;

            // 检查下一个 token 类型
            int varStart = template.indexOf("{{", pos);
            int tagStart = template.indexOf("{%", pos);

            // 没有更多标签 → 返回剩余文本
            if (varStart < 0 && tagStart < 0) {
                String result = template.substring(pos);
                pos = template.length();
                return result;
            }

            // 找到最近的标签起始位置
            int nextStart;
            String endMarker;
            if (varStart < 0) {
                nextStart = tagStart;
                endMarker = "%}";
            } else if (tagStart < 0) {
                nextStart = varStart;
                endMarker = "}}";
            } else {
                if (varStart <= tagStart) {
                    nextStart = varStart;
                    endMarker = "}}";
                } else {
                    nextStart = tagStart;
                    endMarker = "%}";
                }
            }

            // 如果标签前有文本，先返回文本
            if (nextStart > pos) {
                String result = template.substring(pos, nextStart);
                pos = nextStart;
                return result;
            }

            // 找到标签结束位置
            int endIdx = template.indexOf(endMarker, pos + 2);
            if (endIdx < 0) {
                String result = template.substring(pos);
                pos = template.length();
                return result;
            }

            String result = template.substring(pos, endIdx + endMarker.length());
            pos = endIdx + endMarker.length();
            return result;
        }
    }
}
