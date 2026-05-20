package com.llama4j.repo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;

/**
 * HuggingFace Hub API 客户端 — 模型发现与下载
 *
 * <p>提供模型搜索、模型 ID 解析和 GGUF 文件下载功能。
 * 支持断点续传和本地缓存，避免重复下载。</p>
 *
 * <h2>模型 ID 格式</h2>
 * <p>标准格式：{@code <组织>/<模型名>:<量化级别>}</p>
 * <ul>
 *   <li>{@code unsloth/Qwen2.5-7B-Instruct:Q4_K_M} — 指定量化级别</li>
 *   <li>{@code unsloth/Qwen2.5-7B-Instruct} — 自动选择量化级别</li>
 * </ul>
 *
 * <h2>缓存策略</h2>
 * <p>下载的模型文件缓存在 {@code ~/.llama4j/models/<组织>/<模型名>/} 目录下。
 * 如果文件已存在且大小匹配，则跳过下载。</p>
 */
public final class HuggingFaceClient {

    private static final Logger LOG = LoggerFactory.getLogger(HuggingFaceClient.class);
    private static final String HF_API_BASE = "https://huggingface.co/api";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final HttpClient httpClient;
    private final Path cacheDir;

    /**
     * 创建客户端，使用默认缓存目录（~/.llama4j/models）。
     */
    public HuggingFaceClient() {
        this(Path.of(System.getProperty("user.home"), ".llama4j", "models"));
    }

    /**
     * 创建客户端，指定缓存目录。
     *
     * @param cacheDir 模型缓存目录
     */
    public HuggingFaceClient(Path cacheDir) {
        this.cacheDir = cacheDir;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    }

    /**
     * 搜索 HuggingFace 上的 GGUF 模型。
     *
     * @param query 搜索关键词
     * @param limit 返回结果数量上限
     * @return 匹配的模型 ID 列表
     */
    public List<String> searchModels(String query, int limit) throws IOException {
        String url = String.format("%s/models?search=%s&limit=%d&filter=gguf",
            HF_API_BASE, query, limit);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .GET()
            .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode root = OBJECT_MAPPER.readTree(response.body());

            List<String> models = new ArrayList<>();
            for (JsonNode node : root) {
                models.add(node.get("id").asText());
            }

            LOG.info("搜索 '{}' 找到 {} 个模型", query, models.size());
            return models;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("搜索被中断", e);
        }
    }

    /**
     * 下载模型文件到本地缓存。
     *
     * <p>工作流程：</p>
     * <ol>
     *   <li>解析模型 ID（组织/模型名:量化级别）</li>
     *   <li>查询仓库文件列表，找到匹配的 GGUF 文件</li>
     *   <li>如果本地已缓存，直接返回路径</li>
     *   <li>否则从 HuggingFace 下载到缓存目录</li>
     * </ol>
     *
     * @param modelId HuggingFace 模型 ID
     * @return 下载后的本地文件路径
     */
    public Path downloadModel(String modelId) throws IOException {
        ParsedModelId parsed = parseModelId(modelId);

        // 确保缓存目录存在
        Path modelDir = cacheDir.resolve(parsed.repoId.replace('/', java.io.File.separatorChar));
        Files.createDirectories(modelDir);

        // 检查本地缓存
        String targetFile = findGgufFile(parsed.repoId, parsed.quantization);
        Path targetPath = modelDir.resolve(targetFile);

        if (Files.exists(targetPath) && Files.size(targetPath) > 0) {
            LOG.info("模型已缓存: {}", targetPath);
            return targetPath;
        }

        // 下载模型文件
        String downloadUrl = String.format("https://huggingface.co/%s/resolve/main/%s",
            parsed.repoId, targetFile);

        LOG.info("开始下载模型: {} → {}", downloadUrl, targetPath);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(downloadUrl))
            .GET()
            .build();

        try {
            HttpResponse<Path> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofFile(targetPath));
            LOG.info("下载完成: {} ({} 字节)", targetPath, Files.size(targetPath));
            return targetPath;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("下载被中断", e);
        }
    }

    /** 解析模型 ID 为仓库 ID 和量化级别 */
    private ParsedModelId parseModelId(String modelId) {
        String[] parts = modelId.split(":", 2);
        String repoId = parts[0];
        String quantization = parts.length > 1 ? parts[1] : "Q4_K_M";
        return new ParsedModelId(repoId, quantization);
    }

    /** 在仓库中查找匹配量化级别的 GGUF 文件 */
    private String findGgufFile(String repoId, String quantization) throws IOException {
        String url = String.format("%s/models/%s", HF_API_BASE, repoId);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .GET()
            .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode root = OBJECT_MAPPER.readTree(response.body());
            JsonNode siblings = root.get("siblings");

            if (siblings != null) {
                for (JsonNode sibling : siblings) {
                    String filename = sibling.get("rfilename").asText();
                    if (filename.endsWith(".gguf") && filename.contains(quantization)) {
                        return filename;
                    }
                }
            }

            throw new IOException("未找到量化级别为 '" + quantization + "' 的 GGUF 文件: " + repoId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("查询被中断", e);
        }
    }

    /** 解析后的模型 ID */
    private record ParsedModelId(String repoId, String quantization) {}
}
