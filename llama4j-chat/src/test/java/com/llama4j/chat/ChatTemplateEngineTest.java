package com.llama4j.chat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ChatTemplateEngine 单元测试
 *
 * <p>覆盖模板自动检测、Jinja2 渲染、格式匹配和兜底逻辑。</p>
 */
class ChatTemplateEngineTest {

    private ChatTemplateEngine engine;

    @BeforeEach
    void setUp() {
        engine = new ChatTemplateEngine();
    }

    // ── 消息列表复用 ──

    private List<Message> sampleMessages() {
        return List.of(
            Message.system("You are a helpful assistant."),
            Message.user("What is 1+1?"),
            Message.assistant("1+1 equals 2.")
        );
    }

    // ── 测试用例 ──

    @Nested
    @DisplayName("ChatML 模板渲染")
    class ChatMLTemplate {

        @Test
        @DisplayName("包含 im_start 标记的模板应匹配 ChatML 格式")
        void testRenderWithChatMLTemplate() {
            // 模拟 Qwen 模型的 chat template
            String chatmlTemplate = "{% for message in messages %}<|im_start|>{{ message.role }}\n{{ message.content }}<|im_end|>\n{% endfor %}<|im_start|>assistant\n";
            List<Message> messages = sampleMessages();

            String result = engine.renderConversation(chatmlTemplate, messages);

            // ChatML 格式被匹配，应使用 ChatMLFormat.render()
            assertTrue(result.contains("<|im_start|>"), "应包含 im_start 标记");
            assertTrue(result.contains("<|im_end|>"), "应包含 im_end 标记");
            assertTrue(result.contains("You are a helpful assistant."), "应包含系统消息内容");
            assertTrue(result.contains("What is 1+1?"), "应包含用户消息内容");
            assertTrue(result.endsWith("<|im_start|>assistant\n"), "应以 assistant 前缀结尾");
        }
    }

    @Nested
    @DisplayName("Llama 3 模板渲染")
    class Llama3Template {

        @Test
        @DisplayName("包含 start_header_id 的模板应匹配 Llama3 格式")
        void testRenderWithLlama3Template() {
            String llama3Template = "<|begin_of_text|>{% for message in messages %}<|start_header_id|>{{ message.role }}<|end_header_id|>\n\n{{ message.content }}<|eot_id|>{% endfor %}";
            List<Message> messages = sampleMessages();

            String result = engine.renderConversation(llama3Template, messages);

            assertTrue(result.contains("<|begin_of_text|>"), "应包含 begin_of_text 标记");
            assertTrue(result.contains("<|start_header_id|>"), "应包含 start_header_id 标记");
            assertTrue(result.contains("<|eot_id|>"), "应包含 eot_id 标记");
            assertTrue(result.contains("What is 1+1?"), "应包含用户消息");
        }
    }

    @Nested
    @DisplayName("空/Null 模板兜底")
    class EmptyTemplate {

        @Test
        @DisplayName("null 模板应使用 DefaultFormat 兜底")
        void testRenderWithNullTemplate() {
            List<Message> messages = sampleMessages();
            String result = engine.renderConversation(null, messages);

            // DefaultFormat 使用 [Role] content 格式
            assertTrue(result.contains("[System]"), "应包含 [System] 标签");
            assertTrue(result.contains("[User]"), "应包含 [User] 标签");
            assertTrue(result.contains("[Assistant]"), "应包含 [Assistant] 标签");
            assertTrue(result.endsWith("[Assistant]: "), "应以 [Assistant]: 结尾");
        }

        @Test
        @DisplayName("空字符串模板应使用 DefaultFormat 兜底")
        void testRenderWithEmptyStringTemplate() {
            List<Message> messages = sampleMessages();
            String result = engine.renderConversation("", messages);

            assertTrue(result.contains("[User]"), "应使用 DefaultFormat 渲染");
        }

        @Test
        @DisplayName("纯空白模板应使用 DefaultFormat 兜底")
        void testRenderWithBlankTemplate() {
            List<Message> messages = sampleMessages();
            String result = engine.renderConversation("   \n\t  ", messages);

            assertTrue(result.contains("[User]"), "应使用 DefaultFormat 渲染");
        }
    }

    @Nested
    @DisplayName("Jinja2 模板渲染")
    class Jinja2Template {

        @Test
        @DisplayName("包含 Jinja2 语法的未知模板应走 TemplateParser 渲染")
        void testRenderWithJinja2Template() {
            // 一个不匹配任何已知格式、但包含 Jinja2 语法的模板
            String jinjaTemplate = "{% for message in messages %}[{{ message.role }}]: {{ message.content }}\n{% endfor %}[assistant]:";
            List<Message> messages = List.of(
                Message.user("Hello"),
                Message.assistant("Hi there")
            );

            String result = engine.renderConversation(jinjaTemplate, messages);

            // Jinja2 渲染器应解析 for 循环和变量插值
            assertTrue(result.contains("[user]: Hello"), "应渲染用户消息");
            assertTrue(result.contains("[assistant]: Hi there"), "应渲染助手消息");
            assertTrue(result.endsWith("[assistant]:"), "应以 assistant 前缀结尾");
        }
    }

    @Nested
    @DisplayName("getSupportedFormats")
    class SupportedFormats {

        @Test
        @DisplayName("应返回非空的格式名称列表")
        void testGetSupportedFormats() {
            List<String> formats = engine.getSupportedFormats();

            assertFalse(formats.isEmpty(), "格式列表不应为空");
            // 验证已知格式都在列表中
            assertTrue(formats.contains("llama3"), "应包含 llama3 格式");
            assertTrue(formats.contains("chatml"), "应包含 chatml 格式");
            assertTrue(formats.contains("alpaca"), "应包含 alpaca 格式");
            assertTrue(formats.contains("default"), "应包含 default 兜底格式");
        }
    }
}
