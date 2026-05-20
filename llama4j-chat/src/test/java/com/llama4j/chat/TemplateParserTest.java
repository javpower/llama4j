package com.llama4j.chat;

import com.llama4j.chat.jinja.TemplateParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TemplateParser (Jinja2 子集) 单元测试
 *
 * <p>覆盖变量插值、循环、条件、嵌套属性访问、过滤器和表达式求值。</p>
 */
class TemplateParserTest {

    // ── 变量插值 ──

    @Nested
    @DisplayName("变量插值")
    class VariableInterpolation {

        @Test
        @DisplayName("{{ variable }} 应被替换为上下文中的值")
        void testSimpleVariable() {
            String template = "Hello, {{ name }}!";
            Map<String, Object> context = Map.of("name", "World");

            String result = TemplateParser.render(template, context);
            assertEquals("Hello, World!", result);
        }

        @Test
        @DisplayName("多个变量同时插值")
        void testMultipleVariables() {
            String template = "{{ a }} and {{ b }}";
            Map<String, Object> context = Map.of("a", "alpha", "b", "beta");

            String result = TemplateParser.render(template, context);
            assertEquals("alpha and beta", result);
        }

        @Test
        @DisplayName("不存在的变量应渲染为空字符串")
        void testMissingVariable() {
            String template = "Value: {{ missing }}";
            String result = TemplateParser.render(template, Map.of());
            assertEquals("Value: ", result);
        }
    }

    // ── for 循环 ──

    @Nested
    @DisplayName("for 循环")
    class ForLoop {

        @Test
        @DisplayName("遍历列表并渲染每个元素")
        void testBasicForLoop() {
            String template = "{% for item in items %}{{ item }} {% endfor %}";
            Map<String, Object> context = Map.of("items", List.of("a", "b", "c"));

            String result = TemplateParser.render(template, context);
            assertEquals("a b c ", result);
        }

        @Test
        @DisplayName("循环中可访问嵌套属性")
        void testForLoopWithNestedAccess() {
            String template = "{% for msg in messages %}[{{ msg.role }}] {{ msg.content }}\n{% endfor %}";
            List<Map<String, String>> messages = List.of(
                Map.of("role", "user", "content", "Hi"),
                Map.of("role", "assistant", "content", "Hello")
            );
            Map<String, Object> context = Map.of("messages", messages);

            String result = TemplateParser.render(template, context);
            assertEquals("[user] Hi\n[assistant] Hello\n", result);
        }
    }

    // ── 条件 if/else ──

    @Nested
    @DisplayName("条件判断 if/else")
    class ConditionIfElse {

        @Test
        @DisplayName("条件为真时渲染 then 分支")
        void testIfTrue() {
            String template = "{% if active %}YES{% endif %}";
            Map<String, Object> context = Map.of("active", true);

            String result = TemplateParser.render(template, context);
            assertEquals("YES", result);
        }

        @Test
        @DisplayName("条件为假时渲染 else 分支")
        void testIfFalseElse() {
            String template = "{% if active %}YES{% else %}NO{% endif %}";
            Map<String, Object> context = Map.of("active", false);

            String result = TemplateParser.render(template, context);
            assertEquals("NO", result);
        }

        @Test
        @DisplayName("== 比较运算符")
        void testEqualsComparison() {
            String template = "{% if role == 'admin' %}ADMIN{% else %}GUEST{% endif %}";
            Map<String, Object> context = Map.of("role", "admin");

            assertEquals("ADMIN", TemplateParser.render(template, context));

            Map<String, Object> context2 = Map.of("role", "user");
            assertEquals("GUEST", TemplateParser.render(template, context2));
        }

        @Test
        @DisplayName("!= 比较运算符")
        void testNotEqualsComparison() {
            String template = "{% if status != 'off' %}ON{% else %}OFF{% endif %}";
            Map<String, Object> context = Map.of("status", "on");

            assertEquals("ON", TemplateParser.render(template, context));
        }

        @Test
        @DisplayName("not 取反")
        void testNotOperator() {
            String template = "{% if not disabled %}ENABLED{% endif %}";
            Map<String, Object> context = Map.of("disabled", false);

            assertEquals("ENABLED", TemplateParser.render(template, context));
        }
    }

    // ── 嵌套属性访问 ──

    @Nested
    @DisplayName("嵌套属性访问")
    class NestedAccess {

        @Test
        @DisplayName("点号访问 Map 中的属性")
        void testDotAccess() {
            String template = "{{ message.role }}: {{ message.content }}";
            Map<String, Object> context = Map.of(
                "message", Map.of("role", "user", "content", "Hello")
            );

            String result = TemplateParser.render(template, context);
            assertEquals("user: Hello", result);
        }
    }

    // ── 过滤器 ──

    @Nested
    @DisplayName("过滤器")
    class Filters {

        @Test
        @DisplayName("lower 过滤器将文本转为小写")
        void testFilterLower() {
            String template = "{{ value | lower }}";
            Map<String, Object> context = Map.of("value", "HELLO");

            assertEquals("hello", TemplateParser.render(template, context));
        }

        @Test
        @DisplayName("upper 过滤器将文本转为大写")
        void testFilterUpper() {
            String template = "{{ value | upper }}";
            Map<String, Object> context = Map.of("value", "hello");

            assertEquals("HELLO", TemplateParser.render(template, context));
        }

        @Test
        @DisplayName("trim 过滤器去除两端空白")
        void testFilterTrim() {
            String template = "{{ value | trim }}";
            Map<String, Object> context = Map.of("value", "  spaced  ");

            assertEquals("spaced", TemplateParser.render(template, context));
        }

        @Test
        @DisplayName("length 过滤器返回字符串长度")
        void testFilterLength() {
            String template = "{{ value | length }}";
            Map<String, Object> context = Map.of("value", "abc");

            assertEquals("3", TemplateParser.render(template, context));
        }
    }

    // ── 字符串拼接 ──

    @Nested
    @DisplayName("字符串拼接")
    class StringConcatenation {

        @Test
        @DisplayName("{{ a + b }} 拼接两个变量")
        void testConcatenation() {
            String template = "{{ a + b }}";
            Map<String, Object> context = Map.of("a", "hello", "b", " world");

            String result = TemplateParser.render(template, context);
            assertEquals("hello world", result);
        }
    }

    // ── 布尔字面量 ──

    @Nested
    @DisplayName("布尔字面量")
    class BooleanLiterals {

        @Test
        @DisplayName("true 字面量求值为布尔真")
        void testTrueLiteral() {
            String template = "{% if true %}YES{% endif %}";
            assertEquals("YES", TemplateParser.render(template, Map.of()));
        }

        @Test
        @DisplayName("false 字面量求值为布尔假")
        void testFalseLiteral() {
            String template = "{% if false %}YES{% else %}NO{% endif %}";
            assertEquals("NO", TemplateParser.render(template, Map.of()));
        }

        @Test
        @DisplayName("布尔变量可直接输出为字符串")
        void testBooleanOutput() {
            String template = "{{ true }}";
            assertEquals("true", TemplateParser.render(template, Map.of()));

            String template2 = "{{ false }}";
            assertEquals("false", TemplateParser.render(template2, Map.of()));
        }
    }

    // ── set 赋值 ──

    @Nested
    @DisplayName("set 赋值")
    class SetAssignment {

        @Test
        @DisplayName("{% set var = value %} 赋值后可在后续模板中使用")
        void testSetVariable() {
            String template = "{% set greeting = 'hello' %}{{ greeting }}";
            String result = TemplateParser.render(template, Map.of());
            assertEquals("hello", result);
        }
    }

    // ── 综合模板 ──

    @Nested
    @DisplayName("复杂综合模板")
    class ComplexTemplate {

        @Test
        @DisplayName("组合 for + if + 嵌套属性 + 过滤器的综合渲染")
        void testComplexTemplate() {
            String template = "{% for msg in messages %}"
                + "{% if msg.role == 'user' %}"
                + "[USER] {{ msg.content | upper }}\n"
                + "{% else %}"
                + "[BOT] {{ msg.content }}\n"
                + "{% endif %}"
                + "{% endfor %}";

            List<Map<String, String>> messages = List.of(
                Map.of("role", "user", "content", "hi there"),
                Map.of("role", "assistant", "content", "hello!"),
                Map.of("role", "user", "content", "bye")
            );
            Map<String, Object> context = Map.of("messages", messages);

            String result = TemplateParser.render(template, context);
            assertEquals("[USER] HI THERE\n[BOT] hello!\n[USER] BYE\n", result);
        }
    }

    // ── 字符串字面量 ──

    @Nested
    @DisplayName("字符串字面量")
    class StringLiterals {

        @Test
        @DisplayName("单引号字符串字面量")
        void testSingleQuotedString() {
            String template = "{{ 'literal' }}";
            assertEquals("literal", TemplateParser.render(template, Map.of()));
        }

        @Test
        @DisplayName("双引号字符串字面量")
        void testDoubleQuotedString() {
            String template = "{{ \"quoted\" }}";
            assertEquals("quoted", TemplateParser.render(template, Map.of()));
        }
    }
}
