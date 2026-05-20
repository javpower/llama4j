package com.llama4j.chat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Message record 单元测试
 *
 * <p>覆盖工厂方法、record 相等性和 null 校验。</p>
 */
class MessageTest {

    @Nested
    @DisplayName("工厂方法")
    class FactoryMethods {

        @Test
        @DisplayName("Message.user() 创建 USER 角色消息")
        void testUserFactory() {
            Message msg = Message.user("hello");
            assertEquals(Role.USER, msg.role());
            assertEquals("hello", msg.content());
        }

        @Test
        @DisplayName("Message.assistant() 创建 ASSISTANT 角色消息")
        void testAssistantFactory() {
            Message msg = Message.assistant("response");
            assertEquals(Role.ASSISTANT, msg.role());
            assertEquals("response", msg.content());
        }

        @Test
        @DisplayName("Message.system() 创建 SYSTEM 角色消息")
        void testSystemFactory() {
            Message msg = Message.system("instruction");
            assertEquals(Role.SYSTEM, msg.role());
            assertEquals("instruction", msg.content());
        }

        @Test
        @DisplayName("Message.tool() 创建 TOOL 角色消息")
        void testToolFactory() {
            Message msg = Message.tool("tool_result");
            assertEquals(Role.TOOL, msg.role());
            assertEquals("tool_result", msg.content());
        }
    }

    @Nested
    @DisplayName("Record 相等性")
    class RecordEquality {

        @Test
        @DisplayName("相同 role 和 content 的消息应相等")
        void testEqualMessages() {
            Message a = Message.user("test");
            Message b = new Message(Role.USER, "test");
            assertEquals(a, b, "相同内容应相等");
            assertEquals(a.hashCode(), b.hashCode(), "hashCode 应一致");
        }

        @Test
        @DisplayName("不同角色的消息应不相等")
        void testDifferentRoles() {
            Message user = Message.user("same content");
            Message assistant = Message.assistant("same content");
            assertNotEquals(user, assistant, "不同角色应不相等");
        }

        @Test
        @DisplayName("不同内容的消息应不相等")
        void testDifferentContent() {
            Message a = Message.user("alpha");
            Message b = Message.user("beta");
            assertNotEquals(a, b, "不同内容应不相等");
        }
    }

    @Nested
    @DisplayName("Null 校验")
    class NullValidation {

        @Test
        @DisplayName("role 为 null 时应抛出 NullPointerException")
        void testNullRole() {
            assertThrows(NullPointerException.class, () -> new Message(null, "content"));
        }

        @Test
        @DisplayName("content 为 null 时应抛出 NullPointerException")
        void testNullContent() {
            assertThrows(NullPointerException.class, () -> new Message(Role.USER, null));
        }
    }
}
