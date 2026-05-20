package com.llama4j.native_;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GenerateParamsTest {

    @Test
    void testBuilderRequired() {
        GenerateParams params = GenerateParams.builder("hello").build();

        assertEquals("hello", params.prompt());
    }

    @Test
    void testBuilderDefaults() {
        GenerateParams params = GenerateParams.builder("test").build();

        assertEquals(2048, params.maxTokens());
        assertEquals(0.7f, params.temperature(), 0.001f);
        assertEquals(40, params.topK());
        assertEquals(0.9f, params.topP(), 0.001f);
        assertEquals(1.1f, params.repeatPenalty(), 0.001f);
        assertEquals(-1L, params.seed());
    }

    @Test
    void testBuilderCustomValues() {
        GenerateParams params = GenerateParams.builder("prompt")
            .maxTokens(512)
            .temperature(1.2f)
            .topK(80)
            .topP(0.95f)
            .repeatPenalty(1.3f)
            .seed(42L)
            .build();

        assertEquals("prompt", params.prompt());
        assertEquals(512, params.maxTokens());
        assertEquals(1.2f, params.temperature(), 0.001f);
        assertEquals(80, params.topK());
        assertEquals(0.95f, params.topP(), 0.001f);
        assertEquals(1.3f, params.repeatPenalty(), 0.001f);
        assertEquals(42L, params.seed());
    }

    @Test
    void testNullPrompt() {
        assertThrows(IllegalArgumentException.class, () ->
            GenerateParams.builder(null).build());
    }

    @Test
    void testBlankPrompt() {
        assertThrows(IllegalArgumentException.class, () ->
            GenerateParams.builder("").build());
        assertThrows(IllegalArgumentException.class, () ->
            GenerateParams.builder("   ").build());
    }

    @Test
    void testInvalidMaxTokens() {
        assertThrows(IllegalArgumentException.class, () ->
            GenerateParams.builder("test").maxTokens(0).build());
        assertThrows(IllegalArgumentException.class, () ->
            GenerateParams.builder("test").maxTokens(-10).build());
    }

    @Test
    void testNegativeTemperature() {
        assertThrows(IllegalArgumentException.class, () ->
            GenerateParams.builder("test").temperature(-0.1f).build());
    }

    @Test
    void testInvalidTopP() {
        assertThrows(IllegalArgumentException.class, () ->
            GenerateParams.builder("test").topP(-0.1f).build());
        assertThrows(IllegalArgumentException.class, () ->
            GenerateParams.builder("test").topP(1.5f).build());
    }

    @Test
    void testInvalidRepeatPenalty() {
        assertThrows(IllegalArgumentException.class, () ->
            GenerateParams.builder("test").repeatPenalty(0.9f).build());
        assertThrows(IllegalArgumentException.class, () ->
            GenerateParams.builder("test").repeatPenalty(0.5f).build());
    }
}
