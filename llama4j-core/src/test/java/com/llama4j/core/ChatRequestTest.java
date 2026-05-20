package com.llama4j.core;

import com.llama4j.chat.Message;
import com.llama4j.chat.Role;
import com.llama4j.native_.GrammarConstraint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChatRequestTest {

    @Nested
    @DisplayName("Builder")
    class BuilderTests {

        @Test
        @DisplayName("should build request with all parameters")
        void shouldBuildWithAllParameters() {
            ChatRequest request = ChatRequest.builder()
                    .system("You are helpful.")
                    .addMessage(Role.USER, "Hello!")
                    .temperature(0.5f)
                    .maxTokens(512)
                    .topK(20)
                    .topP(0.8f)
                    .repeatPenalty(1.2f)
                    .seed(42L)
                    .build();

            assertEquals(2, request.messages().size());
            assertEquals(0.5f, request.temperature());
            assertEquals(512, request.maxTokens());
            assertEquals(20, request.topK());
            assertEquals(0.8f, request.topP());
            assertEquals(1.2f, request.repeatPenalty());
            assertEquals(42L, request.seed());
        }

        @Test
        @DisplayName("should have correct default values")
        void shouldHaveCorrectDefaults() {
            ChatRequest request = ChatRequest.builder()
                    .addMessage(Role.USER, "Hi")
                    .build();

            assertEquals(0.7f, request.temperature());
            assertEquals(2048, request.maxTokens());
            assertEquals(40, request.topK());
            assertEquals(0.9f, request.topP());
            assertEquals(1.1f, request.repeatPenalty());
            assertEquals(-1L, request.seed());
        }

        @Test
        @DisplayName("should build with messages list")
        void shouldBuildWithMessagesList() {
            List<Message> messages = List.of(
                    Message.system("Be concise."),
                    Message.user("What is Java?")
            );

            ChatRequest request = ChatRequest.builder()
                    .messages(messages)
                    .build();

            assertEquals(2, request.messages().size());
            assertEquals(Role.SYSTEM, request.messages().get(0).role());
            assertEquals(Role.USER, request.messages().get(1).role());
        }

        @Test
        @DisplayName("system() should insert at head of message list")
        void systemShouldInsertAtHead() {
            ChatRequest request = ChatRequest.builder()
                    .addMessage(Role.USER, "Hello!")
                    .system("You are helpful.")
                    .build();

            assertEquals(Role.SYSTEM, request.messages().get(0).role());
            assertEquals(Role.USER, request.messages().get(1).role());
        }

        @Test
        @DisplayName("addMessage(Message) should append message")
        void shouldAddMessageObject() {
            ChatRequest request = ChatRequest.builder()
                    .addMessage(Message.user("First"))
                    .addMessage(Message.assistant("Response"))
                    .addMessage(Message.user("Second"))
                    .build();

            assertEquals(3, request.messages().size());
            assertEquals("First", request.messages().get(0).content());
            assertEquals("Response", request.messages().get(1).content());
            assertEquals("Second", request.messages().get(2).content());
        }
    }

    @Nested
    @DisplayName("validation")
    class ValidationTests {

        @Test
        @DisplayName("should throw on empty messages list")
        void shouldThrowOnEmptyMessages() {
            assertThrows(IllegalArgumentException.class,
                    () -> new ChatRequest(List.of(), 0.7f, 2048, 40, 0.9f, 1.1f, -1L, List.of(), null, false));
        }

        @Test
        @DisplayName("should throw on null messages list")
        void shouldThrowOnNullMessages() {
            assertThrows(NullPointerException.class,
                    () -> new ChatRequest(null, 0.7f, 2048, 40, 0.9f, 1.1f, -1L, List.of(), null, false));
        }

        @Test
        @DisplayName("messages list should be immutable copy")
        void messagesShouldBeImmutableCopy() {
            ChatRequest request = ChatRequest.builder()
                    .addMessage(Role.USER, "Hi")
                    .build();

            assertThrows(UnsupportedOperationException.class,
                    () -> request.messages().add(Message.user("extra")));
        }
    }

    @Nested
    @DisplayName("grammar and jsonMode")
    class GrammarTests {

        @Test
        @DisplayName("grammar should default to null")
        void grammarDefaultsToNull() {
            ChatRequest request = ChatRequest.builder()
                    .addMessage(Role.USER, "Hi")
                    .build();

            assertNull(request.grammar());
        }

        @Test
        @DisplayName("jsonMode should default to false")
        void jsonModeDefaultsToFalse() {
            ChatRequest request = ChatRequest.builder()
                    .addMessage(Role.USER, "Hi")
                    .build();

            assertFalse(request.jsonMode());
        }

        @Test
        @DisplayName("should enable jsonMode via builder")
        void shouldEnableJsonMode() {
            ChatRequest request = ChatRequest.builder()
                    .addMessage(Role.USER, "Generate JSON")
                    .jsonMode(true)
                    .build();

            assertTrue(request.jsonMode());
            assertNull(request.grammar());
        }
    }
}
