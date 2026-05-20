package com.llama4j.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class ExceptionTest {

    @Nested
    @DisplayName("Llama4jException")
    class Llama4jExceptionTests {

        @Test
        void testErrorCode() {
            Llama4jException ex = new Llama4jException("TEST_CODE", "msg");
            assertEquals("TEST_CODE", ex.getErrorCode());
        }

        @Test
        void testMessage() {
            Llama4jException ex = new Llama4jException("CODE", "something went wrong");
            assertEquals("something went wrong", ex.getMessage());
        }

        @Test
        void testCause() {
            IOException cause = new IOException("disk full");
            Llama4jException ex = new Llama4jException("CODE", "msg", cause);
            assertSame(cause, ex.getCause());
        }

        @Test
        void testCauseOnlyConstructor() {
            RuntimeException cause = new RuntimeException("root");
            Llama4jException ex = new Llama4jException("CODE", cause);
            assertSame(cause, ex.getCause());
            assertEquals("CODE", ex.getErrorCode());
        }
    }

    @Nested
    @DisplayName("ModelNotFoundException")
    class ModelNotFoundExceptionTests {

        @Test
        void testErrorCode() {
            assertEquals("MODEL_NOT_FOUND", ModelNotFoundException.CODE);
        }

        @Test
        void testMessage() {
            ModelNotFoundException ex = new ModelNotFoundException("/path/to/model.gguf");
            assertTrue(ex.getMessage().contains("/path/to/model.gguf"));
            assertEquals("/path/to/model.gguf", ex.getModelPath());
        }

        @Test
        void testCause() {
            IOException cause = new IOException("not found");
            ModelNotFoundException ex = new ModelNotFoundException("/bad/path", cause);
            assertSame(cause, ex.getCause());
            assertEquals("MODEL_NOT_FOUND", ex.getErrorCode());
            assertEquals("/bad/path", ex.getModelPath());
        }
    }

    @Nested
    @DisplayName("InferenceException")
    class InferenceExceptionTests {

        @Test
        void testErrorCode() {
            assertEquals("INFERENCE_ERROR", InferenceException.CODE);
        }

        @Test
        void testMessage() {
            InferenceException ex = new InferenceException("generation failed");
            assertEquals("generation failed", ex.getMessage());
            assertEquals("INFERENCE_ERROR", ex.getErrorCode());
        }

        @Test
        void testCause() {
            NullPointerException cause = new NullPointerException();
            InferenceException ex = new InferenceException("oops", cause);
            assertSame(cause, ex.getCause());
        }
    }

    @Nested
    @DisplayName("ToolNotFoundException")
    class ToolNotFoundExceptionTests {

        @Test
        void testErrorCode() {
            assertEquals("TOOL_NOT_FOUND", ToolNotFoundException.CODE);
        }

        @Test
        void testMessage() {
            ToolNotFoundException ex = new ToolNotFoundException("search_web");
            assertTrue(ex.getMessage().contains("search_web"));
            assertEquals("search_web", ex.getToolName());
            assertEquals("TOOL_NOT_FOUND", ex.getErrorCode());
        }
    }

    @Test
    @DisplayName("All exceptions inherit from Llama4jException")
    void testExceptionHierarchy() {
        assertTrue(Llama4jException.class.isAssignableFrom(ModelNotFoundException.class));
        assertTrue(Llama4jException.class.isAssignableFrom(InferenceException.class));
        assertTrue(Llama4jException.class.isAssignableFrom(ToolNotFoundException.class));
    }
}
