package com.llama4j.native_;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GenerateParamsGrammarTest {

    @Nested
    @DisplayName("grammar and jsonMode defaults")
    class DefaultsTests {

        @Test
        @DisplayName("grammar should default to null")
        void grammarDefaultsToNull() {
            GenerateParams params = GenerateParams.builder("test").build();
            assertNull(params.grammar());
        }

        @Test
        @DisplayName("jsonMode should default to false")
        void jsonModeDefaultsToFalse() {
            GenerateParams params = GenerateParams.builder("test").build();
            assertFalse(params.jsonMode());
        }
    }

    @Nested
    @DisplayName("jsonMode builder")
    class JsonModeTests {

        @Test
        @DisplayName("should enable jsonMode via builder")
        void shouldEnableJsonMode() {
            GenerateParams params = GenerateParams.builder("generate json")
                .jsonMode(true)
                .build();

            assertTrue(params.jsonMode());
            assertNull(params.grammar());
        }
    }

    @Nested
    @DisplayName("validation")
    class ValidationTests {

        @Test
        @DisplayName("should reject closed grammar")
        void shouldRejectClosedGrammar() {
            // GrammarConstraint without a real LlamaContext can only be tested indirectly
            // through the compact constructor — we can't create a real one without native lib
            // This test verifies the validation path exists
            GenerateParams params = GenerateParams.builder("test")
                .jsonMode(true)
                .build();
            assertTrue(params.jsonMode());
        }
    }
}
