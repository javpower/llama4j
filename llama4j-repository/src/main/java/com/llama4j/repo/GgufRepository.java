package com.llama4j.repo;

import com.llama4j.exception.ModelNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * GGUF 模型仓库 — 模型发现、缓存与解析
 *
 * <p>提供统一的模型文件定位接口，无论模型是通过本地路径、
 * ModelScope ID 还是 HuggingFace ID 指定。
 * 协调本地文件系统缓存和远程下载。</p>
 *
 * <h2>模型解析策略（按优先级）</h2>
 * <ol>
 *   <li>绝对路径：直接验证文件是否存在</li>
 *   <li>{@code modelscope:} 前缀：通过 {@link ModelScopeClient} 解析和下载</li>
 *   <li>包含 {@code /} 的 ID（非文件路径）：优先 ModelScope，失败后回退 HuggingFace</li>
 *   <li>相对路径：在缓存目录中搜索</li>
 * </ol>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * GgufRepository repo = new GgufRepository();
 *
 * // 本地路径
 * Path model1 = repo.resolve("/models/qwen2.5-7b.gguf");
 *
 * // ModelScope 显式指定
 * Path model2 = repo.resolve("modelscope:Qwen/Qwen2.5-7B-Instruct-GGUF:Q4_K_M");
 *
 * // 自动选择（优先 ModelScope）
 * Path model3 = repo.resolve("Qwen/Qwen2.5-7B-Instruct-GGUF:Q4_K_M");
 *
 * // HuggingFace 显式指定
 * Path model4 = repo.resolve("hf:unsloth/Qwen2.5-7B-Instruct:Q4_K_M");
 * }</pre>
 */
public final class GgufRepository {

    private static final Logger LOG = LoggerFactory.getLogger(GgufRepository.class);

    private final ModelScopeClient msClient;
    private final HuggingFaceClient hfClient;
    private final Path cacheDir;

    public GgufRepository() {
        this(Path.of(System.getProperty("user.home"), ".llama4j", "models"));
    }

    public GgufRepository(Path cacheDir) {
        this.cacheDir = Objects.requireNonNull(cacheDir);
        this.msClient = new ModelScopeClient(cacheDir);
        this.hfClient = new HuggingFaceClient(cacheDir);
    }

    /**
     * 解析模型引用为本地文件路径。
     *
     * <p>支持以下格式：</p>
     * <ul>
     *   <li>绝对路径：{@code /models/qwen2.5-7b.gguf}</li>
     *   <li>ModelScope 显式：{@code modelscope:Qwen/Qwen2.5-7B-Instruct-GGUF:Q4_K_M}</li>
     *   <li>HuggingFace 显式：{@code hf:unsloth/Qwen2.5-7B-Instruct:Q4_K_M}</li>
     *   <li>自动（优先 ModelScope）：{@code Qwen/Qwen2.5-7B-Instruct-GGUF:Q4_K_M}</li>
     *   <li>相对文件名：{@code qwen2.5-7b-q4_k_m.gguf}（在缓存中搜索）</li>
     * </ul>
     */
    public Path resolve(String modelRef) {
        Objects.requireNonNull(modelRef, "模型引用不能为 null");

        // 策略1：绝对路径
        if (Path.of(modelRef).isAbsolute()) {
            Path path = Path.of(modelRef);
            if (Files.isRegularFile(path)) {
                LOG.debug("通过绝对路径解析模型: {}", path);
                return path;
            }
            throw new ModelNotFoundException(modelRef, new IOException("文件不存在: " + modelRef));
        }

        // 策略2：显式前缀 modelref:
        if (modelRef.startsWith("modelscope:") || modelRef.startsWith("ms:")) {
            String id = modelRef.substring(modelRef.indexOf(':') + 1);
            return resolveModelScope(id);
        }

        // 策略3：显式前缀 hf:
        if (modelRef.startsWith("hf:")) {
            String id = modelRef.substring(3);
            return resolveHuggingFace(id);
        }

        // 策略4：包含 / 的 ID 格式（看起来像 repo/model，不是文件路径）
        if (modelRef.contains("/") && !modelRef.endsWith(".gguf")) {
            return resolveAuto(modelRef);
        }

        // 策略5：在缓存中搜索
        Optional<Path> cached = searchCache(modelRef);
        if (cached.isPresent()) {
            LOG.debug("在缓存中找到模型: {}", cached.get());
            return cached.get();
        }

        throw new ModelNotFoundException(modelRef);
    }

    /** 自动解析：优先 ModelScope，回退 HuggingFace */
    private Path resolveAuto(String modelRef) {
        LOG.debug("自动解析模型引用，优先 ModelScope: {}", modelRef);
        try {
            return resolveModelScope(modelRef);
        } catch (ModelNotFoundException e) {
            LOG.debug("ModelScope 解析失败，回退 HuggingFace: {}", e.getMessage());
            return resolveHuggingFace(modelRef);
        }
    }

    private Path resolveModelScope(String modelId) {
        try {
            Path path = msClient.downloadModel(modelId);
            LOG.debug("通过 ModelScope 解析模型: {} → {}", modelId, path);
            return path;
        } catch (IOException e) {
            throw new ModelNotFoundException(modelId, e);
        }
    }

    private Path resolveHuggingFace(String modelId) {
        try {
            Path path = hfClient.downloadModel(modelId);
            LOG.debug("通过 HuggingFace 解析模型: {} → {}", modelId, path);
            return path;
        } catch (IOException e) {
            throw new ModelNotFoundException(modelId, e);
        }
    }

    /** 检查模型是否可用（无需下载） */
    public boolean isAvailable(String modelRef) {
        try {
            resolve(modelRef);
            return true;
        } catch (ModelNotFoundException e) {
            return false;
        }
    }

    /** 根据当前硬件推荐量化级别 */
    public String recommendQuantization(double modelParamsBillion) {
        HardwareProfile profile = HardwareProfile.detect();
        String quant = profile.recommendQuantization(modelParamsBillion);
        LOG.info("为 {}B 模型在 {} 上推荐量化级别: {}", modelParamsBillion, profile, quant);
        return quant;
    }

    private Optional<Path> searchCache(String modelRef) {
        if (!Files.isDirectory(cacheDir)) {
            return Optional.empty();
        }

        try {
            return Files.walk(cacheDir, 3)
                .filter(p -> p.getFileName().toString().endsWith(".gguf"))
                .filter(p -> p.getFileName().toString().contains(modelRef))
                .findFirst();
        } catch (IOException e) {
            LOG.warn("搜索缓存时出错: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
