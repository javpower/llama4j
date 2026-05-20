package com.llama4j.chat;

import com.llama4j.chat.format.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 各 ChatFormat 实现的单元测试
 *
 * <p>覆盖 render() 输出格式和 matches() 模板检测逻辑。</p>
 */
class ChatFormatTest {

    private List<Message> sampleMessages() {
        return List.of(
            Message.system("Be helpful."),
            Message.user("Hi"),
            Message.assistant("Hello!")
        );
    }

    // ── ChatMLFormat ──

    @Nested
    @DisplayName("ChatMLFormat")
    class ChatMLFormatTest {

        private final ChatMLFormat format = new ChatMLFormat();

        @Test
        @DisplayName("render 应产生 im_start / im_end 标记")
        void testRender() {
            String result = format.render(sampleMessages());

            assertTrue(result.contains("<|im_start|>system\n"), "应有 system 消息头");
            assertTrue(result.contains("<|im_start|>user\n"), "应有 user 消息头");
            assertTrue(result.contains("<|im_start|>assistant\n"), "应有 assistant 消息头（最后一行引导生成）");
            assertTrue(result.contains("<|im_end|>"), "应有 im_end 标记");
            assertTrue(result.contains("Be helpful."), "应包含系统消息内容");
            assertTrue(result.contains("Hi"), "应包含用户消息内容");
            assertTrue(result.contains("Hello!"), "应包含助手消息内容");
            // 结尾应有 assistant 前缀，等待模型补全
            assertTrue(result.endsWith("<|im_start|>assistant\n"), "应以 assistant 前缀结尾");
        }

        @Test
        @DisplayName("matches 检测包含 im_start 的模板")
        void testMatchesTrue() {
            assertTrue(format.matches("<|im_start|>system\n..."));
            assertTrue(format.matches("some im_start stuff"));
        }

        @Test
        @DisplayName("matches 对不相关模板返回 false")
        void testMatchesFalse() {
            assertFalse(format.matches("<|begin_of_text|>"));
            assertFalse(format.matches("### Instruction:"));
        }
    }

    // ── Llama3Format ──

    @Nested
    @DisplayName("Llama3Format")
    class Llama3FormatTest {

        private final Llama3Format format = new Llama3Format();

        @Test
        @DisplayName("render 应产生 begin_of_text / header / eot 标记")
        void testRender() {
            String result = format.render(sampleMessages());

            assertTrue(result.startsWith("<|begin_of_text|>"), "应以 begin_of_text 开头");
            assertTrue(result.contains("<|start_header_id|>system<|end_header_id|>"), "应有 system header");
            assertTrue(result.contains("<|start_header_id|>user<|end_header_id|>"), "应有 user header");
            assertTrue(result.contains("<|start_header_id|>assistant<|end_header_id|>"), "应有 assistant header（引导生成）");
            assertTrue(result.contains("<|eot_id|>"), "应有 eot_id 标记");
            assertTrue(result.contains("Be helpful."), "应包含系统消息");
            assertTrue(result.contains("Hi"), "应包含用户消息");
            assertTrue(result.endsWith("<|start_header_id|>assistant<|end_header_id|>\n\n"), "应以 assistant header 结尾");
        }

        @Test
        @DisplayName("matches 检测 start_header_id 或 begin_of_text")
        void testMatchesTrue() {
            assertTrue(format.matches("<|start_header_id|>system"));
            assertTrue(format.matches("<|begin_of_text|>..."));
        }

        @Test
        @DisplayName("matches 对不相关模板返回 false")
        void testMatchesFalse() {
            assertFalse(format.matches("<|im_start|>"));
            assertFalse(format.matches("### Instruction:"));
        }
    }

    // ── DefaultFormat ──

    @Nested
    @DisplayName("DefaultFormat")
    class DefaultFormatTest {

        private final DefaultFormat format = new DefaultFormat();

        @Test
        @DisplayName("render 使用方括号角色标签")
        void testRender() {
            String result = format.render(sampleMessages());

            assertTrue(result.contains("[System] Be helpful.\n"), "应包含 [System] 标签和内容");
            assertTrue(result.contains("[User] Hi\n"), "应包含 [User] 标签和内容");
            assertTrue(result.contains("[Assistant] Hello!\n"), "应包含 [Assistant] 标签和内容");
            assertTrue(result.endsWith("[Assistant]: "), "应以 [Assistant]: 结尾");
        }

        @Test
        @DisplayName("render 对 TOOL 角色输出 [Tool] 标签")
        void testToolRole() {
            List<Message> msgs = List.of(Message.tool("result data"));
            String result = format.render(msgs);

            assertTrue(result.contains("[Tool] result data"), "应包含 [Tool] 标签");
        }

        @Test
        @DisplayName("matches 永远返回 false（兜底格式不主动匹配）")
        void testMatchesAlwaysFalse() {
            assertFalse(format.matches("anything"));
            assertFalse(format.matches("<|im_start|>"));
        }
    }

    // ── AlpacaFormat ──

    @Nested
    @DisplayName("AlpacaFormat")
    class AlpacaFormatTest {

        private final AlpacaFormat format = new AlpacaFormat();

        @Test
        @DisplayName("render 使用 ### Instruction / ### Response 格式")
        void testRender() {
            List<Message> msgs = List.of(
                Message.user("Write a poem"),
                Message.assistant("Roses are red")
            );
            String result = format.render(msgs);

            // 默认系统提示词
            assertTrue(result.startsWith("Below is an instruction"), "应有默认系统提示词");
            assertTrue(result.contains("### Instruction:\nWrite a poem"), "应有 Instruction 段");
            assertTrue(result.contains("### Response:\nRoses are red"), "应有 Response 段");
            assertTrue(result.endsWith("### Response:\n"), "应以 Response 提示结尾");
        }

        @Test
        @DisplayName("render 使用自定义系统提示词")
        void testCustomSystemPrompt() {
            List<Message> msgs = List.of(
                Message.system("You are a poet."),
                Message.user("Write a haiku")
            );
            String result = format.render(msgs);

            assertTrue(result.startsWith("You are a poet."), "应使用自定义系统提示词");
            assertFalse(result.contains("Below is an instruction"), "不应包含默认系统提示词");
        }

        @Test
        @DisplayName("matches 检测 ### Instruction 或 ### Response")
        void testMatches() {
            assertTrue(format.matches("### Instruction:\nWrite something"));
            assertTrue(format.matches("### Response:\nHere it is"));
            assertFalse(format.matches("<|im_start|>"));
            assertFalse(format.matches("random text"));
        }
    }

    // ── 通用边界测试 ──

    @Nested
    @DisplayName("空消息列表")
    class EmptyMessages {

        @Test
        @DisplayName("空消息列表不应导致异常")
        void testEmptyListNoException() {
            ChatFormat[] formats = {
                new ChatMLFormat(),
                new Llama3Format(),
                new DefaultFormat(),
                new AlpacaFormat()
            };
            for (ChatFormat f : formats) {
                assertDoesNotThrow(() -> f.render(Collections.emptyList()),
                    f.name() + " 应能处理空消息列表");
            }
        }
    }
}
