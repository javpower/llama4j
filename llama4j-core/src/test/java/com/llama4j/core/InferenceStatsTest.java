package com.llama4j.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InferenceStatsTest {

    private InferenceStats stats;

    @BeforeEach
    void setUp() {
        stats = new InferenceStats();
    }

    @Nested
    @DisplayName("initial values")
    class InitialValuesTests {

        @Test
        @DisplayName("new stats should have zero inference count")
        void shouldHaveZeroCount() {
            assertEquals(0, stats.getTotalInferences());
        }

        @Test
        @DisplayName("new stats should have zero prompt tokens")
        void shouldHaveZeroPromptTokens() {
            assertEquals(0, stats.getTotalPromptTokens());
        }

        @Test
        @DisplayName("new stats should have zero completion tokens")
        void shouldHaveZeroCompletionTokens() {
            assertEquals(0, stats.getTotalCompletionTokens());
        }

        @Test
        @DisplayName("new stats should have zero total latency")
        void shouldHaveZeroLatency() {
            assertEquals(0.0, stats.getTotalLatencyMs());
        }

        @Test
        @DisplayName("new stats should have zero average latency")
        void shouldHaveZeroAvgLatency() {
            assertEquals(0.0, stats.getAvgLatencyMs());
        }

        @Test
        @DisplayName("new stats should have zero tokens per second")
        void shouldHaveZeroTokensPerSecond() {
            assertEquals(0.0, stats.getTokensPerSecond());
        }
    }

    @Nested
    @DisplayName("recordInference")
    class RecordInferenceTests {

        @Test
        @DisplayName("should increment inference count")
        void shouldIncrementCount() {
            stats.recordInference(10, 20, 100.0, 200L);
            stats.recordInference(5, 10, 80.0, 125L);

            assertEquals(2, stats.getTotalInferences());
        }

        @Test
        @DisplayName("should accumulate token counts")
        void shouldAccumulateTokens() {
            stats.recordInference(10, 20, 100.0, 200L);
            stats.recordInference(5, 15, 80.0, 125L);

            assertEquals(15, stats.getTotalPromptTokens());
            assertEquals(35, stats.getTotalCompletionTokens());
        }

        @Test
        @DisplayName("should compute correct average latency")
        void shouldComputeAvgLatency() {
            stats.recordInference(10, 20, 100.0, 200L);
            stats.recordInference(5, 15, 80.0, 300L);

            double avg = stats.getAvgLatencyMs();
            assertEquals(250.0, avg, 0.001);
        }

        @Test
        @DisplayName("should track last tokens per second")
        void shouldTrackLastTokensPerSecond() {
            stats.recordInference(10, 20, 100.0, 200L);
            assertEquals(100.0, stats.getTokensPerSecond());

            stats.recordInference(10, 20, 200.0, 100L);
            assertEquals(200.0, stats.getTokensPerSecond());
        }

        @Test
        @DisplayName("should accumulate total latency")
        void shouldAccumulateTotalLatency() {
            stats.recordInference(10, 20, 100.0, 200L);
            stats.recordInference(10, 20, 100.0, 300L);

            assertEquals(500.0, stats.getTotalLatencyMs(), 0.001);
        }
    }

    @Nested
    @DisplayName("updateKvCacheUsage")
    class KvCacheTests {

        @Test
        @DisplayName("should update KV cache usage")
        void shouldUpdateKvCacheUsage() {
            stats.updateKvCacheUsage(0.75);
            assertEquals(0.75, stats.getKvCacheUsage(), 0.001);
        }
    }

    @Nested
    @DisplayName("toString")
    class ToStringTests {

        @Test
        @DisplayName("should include key metrics in toString")
        void shouldIncludeMetrics() {
            stats.recordInference(10, 20, 100.0, 200L);
            String str = stats.toString();

            assertTrue(str.contains("1"));  // inference count
            assertNotNull(str);
        }
    }
}
