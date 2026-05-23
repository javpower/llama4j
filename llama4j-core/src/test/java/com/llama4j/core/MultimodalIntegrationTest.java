package com.llama4j.core;

import com.llama4j.chat.Role;
import com.llama4j.native_.ImageData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

import static org.junit.jupiter.api.Assertions.*;

/**
 * VLM（Vision-Language Model）集成测试
 *
 * <p>需要本地有 Qwen2-VL-2B GGUF 模型文件才能运行。
 * 模型路径：/Volumes/macEx/models/</p>
 *
 * <p>手动运行：{@code mvn test -Dtest="MultimodalIntegrationTest" -pl llama4j-core}</p>
 */
@Tag("integration")
@DisplayName("VLM 多模态集成测试")
class MultimodalIntegrationTest {

    private static final String MODEL_DIR = "/Volumes/macEx/models";
    private static final String MODEL_PATH = MODEL_DIR + "/Qwen2-VL-2B-Instruct-Q4_K_M.gguf";
    private static final String MMPROJ_PATH = MODEL_DIR + "/mmproj-Qwen2-VL-2B-Instruct-f16.gguf";

    @BeforeAll
    static void checkModelExists() {
        assertTrue(Files.exists(Path.of(MODEL_PATH)),
            "VLM 模型文件不存在: " + MODEL_PATH + "\n请先下载模型。");
        assertTrue(Files.exists(Path.of(MMPROJ_PATH)),
            "mmproj 文件不存在: " + MMPROJ_PATH + "\n请先下载 mmproj。");
    }

    @Test
    @DisplayName("VLM 多模态推理 — 图片描述")
    void testVlmImageDescription() throws Exception {
        // 生成一张最小的 PNG（1x1 红色像素）用于测试
        byte[] imageBytes = createMinimalPng();

        try (LocalModel model = LocalModel.fromFileWithVision(MODEL_PATH, MMPROJ_PATH)) {
            assertNotNull(model);

            ChatResponse response = model.chat(ChatRequest.builder()
                .addMessage(Role.USER, "描述这张图片的内容")
                .temperature(0.1f)
                .maxTokens(256)
                .images(java.util.List.of(ImageData.png(imageBytes)))
                .build());

            assertNotNull(response);
            assertNotNull(response.content());
            assertFalse(response.content().isBlank(),
                "VLM 应该能返回图片描述");
            assertTrue(response.completionTokens() > 0);
            System.out.println("VLM 回复: " + response.content());
            System.out.println("Tokens: prompt=" + response.promptTokens()
                + " completion=" + response.completionTokens()
                + " tps=" + String.format("%.1f", response.tokensPerSecond()));
        }
    }

    @Test
    @DisplayName("VLM 纯文本推理仍然正常")
    void testVlmTextOnlyStillWorks() throws Exception {
        try (LocalModel model = LocalModel.fromFileWithVision(MODEL_PATH, MMPROJ_PATH)) {
            ChatResponse response = model.chat(ChatRequest.builder()
                .addMessage(Role.USER, "1+1等于几？只回答数字")
                .temperature(0.0f)
                .maxTokens(32)
                .build());

            assertNotNull(response);
            assertFalse(response.content().isBlank());
            System.out.println("纯文本回复: " + response.content());
        }
    }

    /** 生成一个最小的有效 PNG 文件（8x8 红色） */
    private static byte[] createMinimalPng() throws Exception {
        BufferedImage img = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                img.setRGB(x, y, Color.RED.getRGB());
            }
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", baos);
        return baos.toByteArray();
    }

}
