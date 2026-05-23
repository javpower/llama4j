package com.llama4j.native_;

import java.util.Objects;

/**
 * 图片数据 — 不可变的图片二进制数据载体
 *
 * @param data      图片二进制数据（PNG/JPG/WebP 等格式）
 * @param mediaType MIME 类型（如 "image/png"、"image/jpeg"）
 */
public record ImageData(byte[] data, String mediaType) {

    public ImageData {
        Objects.requireNonNull(data, "图片数据不能为 null");
        Objects.requireNonNull(mediaType, "mediaType 不能为 null");
    }

    public static ImageData png(byte[] data) {
        return new ImageData(data, "image/png");
    }

    public static ImageData jpeg(byte[] data) {
        return new ImageData(data, "image/jpeg");
    }

    public static ImageData webp(byte[] data) {
        return new ImageData(data, "image/webp");
    }
}
