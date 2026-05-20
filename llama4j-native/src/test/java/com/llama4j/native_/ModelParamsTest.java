package com.llama4j.native_;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ModelParamsTest {

    @Test
    void testDefaultValues() {
        ModelParams defaults = ModelParams.DEFAULT;

        assertEquals(4096, defaults.nCtx());
        assertEquals(-1, defaults.nGpuLayers());
        assertTrue(defaults.nThreads() > 0, "nThreads should default to available processors");
    }

    @Test
    void testBuilderCustomValues() {
        ModelParams params = ModelParams.builder()
            .nCtx(8192)
            .nGpuLayers(4)
            .nThreads(8)
            .build();

        assertEquals(8192, params.nCtx());
        assertEquals(4, params.nGpuLayers());
        assertEquals(8, params.nThreads());
    }

    @Test
    void testInvalidNCtx() {
        assertThrows(IllegalArgumentException.class, () ->
            ModelParams.builder().nCtx(0).build());
        assertThrows(IllegalArgumentException.class, () ->
            ModelParams.builder().nCtx(-100).build());
    }

    @Test
    void testInvalidNGpuLayers() {
        assertThrows(IllegalArgumentException.class, () ->
            ModelParams.builder().nGpuLayers(-2).build());
        assertThrows(IllegalArgumentException.class, () ->
            ModelParams.builder().nGpuLayers(-10).build());
    }

    @Test
    void testInvalidNThreads() {
        assertThrows(IllegalArgumentException.class, () ->
            ModelParams.builder().nThreads(0).build());
        assertThrows(IllegalArgumentException.class, () ->
            ModelParams.builder().nThreads(-1).build());
    }

    @Test
    void testRecordEquality() {
        ModelParams a = ModelParams.builder().nCtx(4096).nGpuLayers(0).nThreads(4).build();
        ModelParams b = ModelParams.builder().nCtx(4096).nGpuLayers(0).nThreads(4).build();

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }
}
