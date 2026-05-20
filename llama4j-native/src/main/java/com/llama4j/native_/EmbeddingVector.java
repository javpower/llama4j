package com.llama4j.native_;

import java.util.Objects;

/**
 * 文本嵌入向量 — 不可变容器
 *
 * <p>封装了文本经过模型编码后的嵌入向量，提供相似度计算等常用操作。
 * 嵌入向量是将文本映射到高维空间中的浮点数组，语义相近的文本
 * 在向量空间中的距离也较近。</p>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * try (LlamaContext ctx = new LlamaContext(modelPath, ModelParams.DEFAULT)) {
 *     EmbeddingVector v1 = new EmbeddingVector("猫", ctx.embed("猫"));
 *     EmbeddingVector v2 = new EmbeddingVector("狗", ctx.embed("狗"));
 *     double similarity = v1.cosineSimilarity(v2);
 * }
 * }</pre>
 *
 * @param text   源文本
 * @param vector 嵌入向量浮点数组（防御性拷贝）
 */
public record EmbeddingVector(String text, float[] vector) {

    public EmbeddingVector {
        Objects.requireNonNull(text, "文本不能为 null");
        if (vector == null || vector.length == 0) {
            throw new IllegalArgumentException("嵌入向量不能为 null 或空");
        }
        vector = vector.clone();
    }

    /** 返回嵌入向量的防御性副本 */
    @Override
    public float[] vector() {
        return vector.clone();
    }

    /** @return 向量维度 */
    public int dimension() {
        return vector.length;
    }

    /**
     * 计算与另一个嵌入向量的余弦相似度。
     *
     * <p>余弦相似度衡量两个向量方向的接近程度，范围 [-1, 1]。
     * 1 表示完全相同，0 表示无关，-1 表示完全相反。</p>
     */
    public double cosineSimilarity(EmbeddingVector other) {
        Objects.requireNonNull(other, "目标向量不能为 null");
        return cosineSimilarity(this.vector, other.vector);
    }

    /** 计算与另一个嵌入向量的点积 */
    public double dotProduct(EmbeddingVector other) {
        Objects.requireNonNull(other, "目标向量不能为 null");
        double sum = 0.0;
        for (int i = 0; i < vector.length; i++) {
            sum += vector[i] * other.vector[i];
        }
        return sum;
    }

    /** 计算与另一个嵌入向量的欧氏距离（L2 距离） */
    public double euclideanDistance(EmbeddingVector other) {
        Objects.requireNonNull(other, "目标向量不能为 null");
        double sum = 0.0;
        for (int i = 0; i < vector.length; i++) {
            double diff = vector[i] - other.vector[i];
            sum += diff * diff;
        }
        return Math.sqrt(sum);
    }

    /**
     * 计算两个浮点数组的余弦相似度。
     *
     * @param a 向量 a
     * @param b 向量 b
     * @return 余弦相似度 [-1, 1]
     */
    public static double cosineSimilarity(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length || a.length == 0) {
            throw new IllegalArgumentException("向量不能为 null 且长度必须相同且非零");
        }
        double dot = 0.0, normA = 0.0, normB = 0.0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        double denom = Math.sqrt(normA) * Math.sqrt(normB);
        return denom == 0.0 ? 0.0 : dot / denom;
    }
}
