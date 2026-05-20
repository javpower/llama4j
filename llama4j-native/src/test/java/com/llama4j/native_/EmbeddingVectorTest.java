package com.llama4j.native_;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EmbeddingVectorTest {

    @Nested
    @DisplayName("Construction")
    class ConstructionTests {

        @Test
        @DisplayName("should create embedding vector with text and vector")
        void shouldCreateEmbeddingVector() {
            float[] vec = {0.1f, 0.2f, 0.3f};
            EmbeddingVector ev = new EmbeddingVector("hello", vec);

            assertEquals("hello", ev.text());
            assertEquals(3, ev.dimension());
        }

        @Test
        @DisplayName("should make defensive copy of vector")
        void shouldMakeDefensiveCopy() {
            float[] vec = {1.0f, 2.0f, 3.0f};
            EmbeddingVector ev = new EmbeddingVector("test", vec);

            vec[0] = 999f; // mutate original
            assertArrayEquals(new float[]{1.0f, 2.0f, 3.0f}, ev.vector());
        }

        @Test
        @DisplayName("vector() should return a copy")
        void vectorAccessorShouldReturnCopy() {
            float[] vec = {1.0f, 2.0f};
            EmbeddingVector ev = new EmbeddingVector("test", vec);

            float[] got = ev.vector();
            got[0] = 999f;
            assertArrayEquals(new float[]{1.0f, 2.0f}, ev.vector());
        }

        @Test
        @DisplayName("should reject null text")
        void shouldRejectNullText() {
            assertThrows(NullPointerException.class,
                () -> new EmbeddingVector(null, new float[]{1.0f}));
        }

        @Test
        @DisplayName("should reject null vector")
        void shouldRejectNullVector() {
            assertThrows(IllegalArgumentException.class,
                () -> new EmbeddingVector("test", null));
        }

        @Test
        @DisplayName("should reject empty vector")
        void shouldRejectEmptyVector() {
            assertThrows(IllegalArgumentException.class,
                () -> new EmbeddingVector("test", new float[0]));
        }
    }

    @Nested
    @DisplayName("Similarity")
    class SimilarityTests {

        @Test
        @DisplayName("identical vectors should have cosine similarity of 1.0")
        void identicalVectorsCosine() {
            float[] v = {1.0f, 0.0f, 0.0f};
            EmbeddingVector e1 = new EmbeddingVector("a", v);
            EmbeddingVector e2 = new EmbeddingVector("b", v);

            assertEquals(1.0, e1.cosineSimilarity(e2), 0.0001);
        }

        @Test
        @DisplayName("opposite vectors should have cosine similarity of -1.0")
        void oppositeVectorsCosine() {
            EmbeddingVector e1 = new EmbeddingVector("a", new float[]{1.0f, 0.0f});
            EmbeddingVector e2 = new EmbeddingVector("b", new float[]{-1.0f, 0.0f});

            assertEquals(-1.0, e1.cosineSimilarity(e2), 0.0001);
        }

        @Test
        @DisplayName("orthogonal vectors should have cosine similarity of 0.0")
        void orthogonalVectorsCosine() {
            EmbeddingVector e1 = new EmbeddingVector("a", new float[]{1.0f, 0.0f});
            EmbeddingVector e2 = new EmbeddingVector("b", new float[]{0.0f, 1.0f});

            assertEquals(0.0, e1.cosineSimilarity(e2), 0.0001);
        }

        @Test
        @DisplayName("dot product should compute correctly")
        void dotProduct() {
            EmbeddingVector e1 = new EmbeddingVector("a", new float[]{1.0f, 2.0f, 3.0f});
            EmbeddingVector e2 = new EmbeddingVector("b", new float[]{4.0f, 5.0f, 6.0f});

            assertEquals(32.0, e1.dotProduct(e2), 0.0001); // 1*4 + 2*5 + 3*6
        }

        @Test
        @DisplayName("euclidean distance should compute correctly")
        void euclideanDistance() {
            EmbeddingVector e1 = new EmbeddingVector("a", new float[]{0.0f, 0.0f});
            EmbeddingVector e2 = new EmbeddingVector("b", new float[]{3.0f, 4.0f});

            assertEquals(5.0, e1.euclideanDistance(e2), 0.0001);
        }

        @Test
        @DisplayName("static cosineSimilarity should work on raw arrays")
        void staticCosineSimilarity() {
            float[] a = {1.0f, 1.0f};
            float[] b = {1.0f, 1.0f};

            assertEquals(1.0, EmbeddingVector.cosineSimilarity(a, b), 0.0001);
        }

        @Test
        @DisplayName("static cosineSimilarity should reject mismatched lengths")
        void staticCosineRejectsMismatchedLengths() {
            assertThrows(IllegalArgumentException.class,
                () -> EmbeddingVector.cosineSimilarity(new float[]{1.0f}, new float[]{1.0f, 2.0f}));
        }
    }
}
