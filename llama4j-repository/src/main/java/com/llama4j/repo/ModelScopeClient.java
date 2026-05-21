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
 * ModelScope（魔搭）API 客户端 — 模型发现与下载
 *
 * <p>ModelScope 是国内主要的模型托管平台，访问速度优于 HuggingFace。
 * 支持模型文件列表查询和 GGUF 文件下载。</p>
 *
 * <h2>模型 ID 格式</h2>
 * <p>标准格式：{@code <组织>/<模型名>:<量化级别>}</p>
 * <ul>
 *   <li>{@code Qwen/Qwen2.5-7B-Instruct-GGUF:Q4_K_M} — 指定量化级别</li>
 *   <li>{@code Qwen/Qwen2.5-7B-Instruct-GGUF} — 自动选择量化级别</li>
 * </ul>
 *
 * <h2>API 参考</h2>
 * <ul>
 *   <li>模型信息：{@code GET /api/v1/models/{repoId}}</li>
 *   <li>文件列表：{@code GET /api/v1/models/{repoId}/repo/files?Revision=master}</li>
 *   <li>文件下载：{@code GET /models/{repoId}/resolve/master/{filename}}</li>
 * </ul>
 */
public final class ModelScopeClient {

    private static final Logger LOG = LoggerFactory.getLogger(ModelScopeClient.class);
    private static final String MS_API_BASE = "https://modelscope.cn/api/v1";
    private static final String MS_DOWNLOAD_BASE = "https://modelscope.cn/models";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final HttpClient httpClient;
    private final Path cacheDir;

    public ModelScopeClient() {
        this(Path.of(System.getProperty("user.home"), ".llama4j", "models"));
    }

    public ModelScopeClient(Path cacheDir) {
        this.cacheDir = cacheDir;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    }

    /**
     * 下载模型文件到本地缓存。
     *
     * <p>工作流程：</p>
     * <ol>
     *   <li>解析模型 ID（组织/模型名:量化级别）</li>
     *   <li>查询仓库文件列表，找到匹配的 GGUF 文件</li>
     *   <li>如果本地已缓存，直接返回路径</li>
     *   <li>否则从 ModelScope 下载到缓存目录</li>
     * </ol>
     *
     * @param modelId ModelScope 模型 ID
     * @return 下载后的本地文件路径
     */
    public Path downloadModel(String modelId) throws IOException {
        ParsedModelId parsed = parseModelId(modelId);

        Path modelDir = cacheDir.resolve("modelscope").resolve(parsed.repoId.replace('/', java.io.File.separatorChar));
        Files.createDirectories(modelDir);

        String targetFile = findGgufFile(parsed.repoId, parsed.quantization);
        Path targetPath = modelDir.resolve(targetFile);

        if (Files.exists(targetPath) && Files.size(targetPath) > 0) {
            LOG.info("ModelScope 模型已缓存: {}", targetPath);
            return targetPath;
        }

        String downloadUrl = String.format("%s/%s/resolve/master/%s",
            MS_DOWNLOAD_BASE, parsed.repoId, targetFile);

        LOG.info("开始从 ModelScope 下载模型: {} → {}", downloadUrl, targetPath);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(downloadUrl))
            .GET()
            .build();

        try {
            HttpResponse<Path> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofFile(targetPath));
            LOG.info("ModelScope 下载完成: {} ({} 字节)", targetPath, Files.size(targetPath));
            return targetPath;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("下载被中断", e);
        }
    }

    /**
     * 检查模型是否已在本地缓存。
     *
     * @param modelId ModelScope 模型 ID
     * @return 是否已缓存
     */
    public boolean isCached(String modelId) {
        try {
            ParsedModelId parsed = parseModelId(modelId);
            Path modelDir = cacheDir.resolve("modelscope").resolve(parsed.repoId.replace('/', java.io.File.separatorChar));
            if (!Files.isDirectory(modelDir)) return false;

            String targetFile = findGgufFile(parsed.repoId, parsed.quantization);
            Path targetPath = modelDir.resolve(targetFile);
            return Files.exists(targetPath) && Files.size(targetPath) > 0;
        } catch (IOException e) {
            return false;
        }
    }

    private ParsedModelId parseModelId(String modelId) {
        String[] parts = modelId.split(":", 2);
        String repoId = parts[0];
        String quantization = parts.length > 1 ? parts[1] : "Q4_K_M";
        return new ParsedModelId(repoId, quantization);
    }

    private String findGgufFile(String repoId, String quantization) throws IOException {
        String url = String.format("%s/models/%s/repo/files?Revision=master", MS_API_BASE, repoId);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .GET()
            .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode root = OBJECT_MAPPER.readTree(response.body());
            JsonNode files = root.at("/Data/Files");

            if (files != null && files.isArray()) {
                String lowerQuant = quantization.toLowerCase();
                for (JsonNode file : files) {
                    String name = file.get("Name").asText();
                    if (name.endsWith(".gguf") && name.toLowerCase().contains(lowerQuant)) {
                        LOG.debug("ModelScope 找到匹配文件: {}", name);
                        return name;
                    }
                }
            }

            throw new IOException("ModelScope 未找到量化级别为 '" + quantization + "' 的 GGUF 文件: " + repoId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("查询被中断", e);
        }
    }

    private record ParsedModelId(String repoId, String quantization) {}
}
