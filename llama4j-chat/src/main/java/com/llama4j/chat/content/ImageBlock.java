package com.llama4j.chat.content;

import java.util.Objects;

/**
 * 图片内容块 — 用于多模态消息中的图片数据
 *
 * @param data      图片二进制数据（PNG/JPG/WebP），与 url 二选一
 * @param mediaType MIME 类型（如 "image/png"）
 * @param url       图片 URL（http/https 或 data URI），与 data 二选一
 */
public record ImageBlock(byte[] data, String mediaType, String url) implements ContentBlock {

    public ImageBlock {
        if (data == null && url == null) {
            throw new IllegalArgumentException("data 和 url 不能同时为 null");
        }
        if (mediaType == null) mediaType = "image/png";
    }

    public static ImageBlock fromBytes(byte[] data, String mediaType) {
        Objects.requireNonNull(data, "图片数据不能为 null");
        return new ImageBlock(data, mediaType != null ? mediaType : "image/png", null);
    }

    public static ImageBlock fromUrl(String url) {
        Objects.requireNonNull(url, "图片 URL 不能为 null");
        String mediaType = url.contains("image/jpeg") || url.endsWith(".jpg") || url.endsWith(".jpeg")
            ? "image/jpeg" : "image/png";
        return new ImageBlock(null, mediaType, url);
    }
}
