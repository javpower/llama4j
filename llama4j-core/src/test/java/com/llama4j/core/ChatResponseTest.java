package com.llama4j.core;

import com.llama4j.chat.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChatResponseTest {

    @Nested
    @DisplayName("ChatResponse.of (simple)")
    class SimpleFactoryTests {

        @Test
        @DisplayName("should create response with content and token counts")
        void shouldCreateSimpleResponse() {
            ChatResponse response = ChatResponse.of("Hello world", 10, 5);

            assertEquals("Hello world", response.content());
            assertEquals(10, response.promptTokens());
            assertEquals(5, response.completionTokens());
            assertEquals(15, response.totalTokens());
            assertEquals(0.0, response.tokensPerSecond());
            assertEquals(0, response.latencyMs());
        }

        @Test
        @DisplayName("should set message role to ASSISTANT")
        void shouldSetAssistantRole() {
            ChatResponse response = ChatResponse.of("Hi", 5, 2);

            assertNotNull(response.message());
            assertEquals(Role.ASSISTANT, response.message().role());
            assertEquals("Hi", response.message().content());
        }
    }

    @Nested
    @DisplayName("ChatResponse.of (with metrics)")
    class FullFactoryTests {

        @Test
        @DisplayName("should create response with performance metrics")
        void shouldCreateResponseWithMetrics() {
            ChatResponse response = ChatResponse.of("Generated text", 20, 10, 150.5, 3200L);

            assertEquals("Generated text", response.content());
            assertEquals(20, response.promptTokens());
            assertEquals(10, response.completionTokens());
            assertEquals(30, response.totalTokens());
            assertEquals(150.5, response.tokensPerSecond());
            assertEquals(3200L, response.latencyMs());
        }

        @Test
        @DisplayName("message content should match response content")
        void messageContentShouldMatchResponseContent() {
            ChatResponse response = ChatResponse.of("Test", 1, 1, 50.0, 100L);

            assertEquals(response.content(), response.message().content());
        }
    }

    @Nested
    @DisplayName("compact constructor validation")
    class ValidationTests {

        @Test
        @DisplayName("should throw NPE when content is null")
        void shouldThrowOnNullContent() {
            assertThrows(NullPointerException.class,
                    () -> new ChatResponse(null,
                            new com.llama4j.chat.Message(Role.ASSISTANT, "msg"),
                            1, 1, 2, 0.0, 0));
        }

        @Test
        @DisplayName("should throw NPE when message is null")
        void shouldThrowOnNullMessage() {
            assertThrows(NullPointerException.class,
                    () -> new ChatResponse("content", null,
                            1, 1, 2, 0.0, 0));
        }
    }
}
