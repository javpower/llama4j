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
 * <p>提供统一的模型文件定位接口，无论模型是通过本地路径还是
 * HuggingFace ID 指定。协调本地文件系统缓存和远程下载。</p>
 *
 * <h2>模型解析策略</h2>
 * <ol>
 *   <li>如果路径看起来像 HuggingFace ID（包含 "/" 和可选的 ":"），
 *       通过 {@link HuggingFaceClient} 解析和下载</li>
 *   <li>如果是绝对本地路径，验证文件是否存在</li>
 *   <li>如果是相对路径，在缓存目录中搜索</li>
 * </ol>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * GgufRepository repo = new GgufRepository();
 *
 * // 本地路径
 * Path model1 = repo.resolve("/models/qwen2.5-7b.gguf");
 *
 * // HuggingFace ID（自动下载）
 * Path model2 = repo.resolve("unsloth/Qwen2.5-7B-Instruct:Q4_K_M");
 *
 * // 检查可用性
 * if (repo.isAvailable("unsloth/Qwen2.5-7B-Instruct:Q4_K_M")) {
 *     // 模型已缓存
 * }
 * }</pre>
 */
public final class GgufRepository {

    private static final Logger LOG = LoggerFactory.getLogger(GgufRepository.class);

    private final HuggingFaceClient hfClient;
    private final Path cacheDir;

    /** 创建仓库，使用默认缓存目录 */
    public GgufRepository() {
        this(Path.of(System.getProperty("user.home"), ".llama4j", "models"));
    }

    /** 创建仓库，指定缓存目录 */
    public GgufRepository(Path cacheDir) {
        this.cacheDir = Objects.requireNonNull(cacheDir);
        this.hfClient = new HuggingFaceClient(cacheDir);
    }

    /**
     * 解析模型引用为本地文件路径。
     *
     * <p>支持三种引用格式：</p>
     * <ul>
     *   <li>绝对路径：{@code /models/qwen2.5-7b.gguf}</li>
     *   <li>HuggingFace ID：{@code unsloth/Qwen2.5-7B-Instruct:Q4_K_M}</li>
     *   <li>相对文件名：{@code qwen2.5-7b-q4_k_m.gguf}（在缓存中搜索）</li>
     * </ul>
     *
     * @param modelRef 模型引用
     * @return 模型的本地文件路径
     * @throws ModelNotFoundException 如果模型无法找到
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

        // 策略2：HuggingFace ID（包含组织名/）
        if (modelRef.contains("/") && !modelRef.endsWith(".gguf")) {
            try {
                Path path = hfClient.downloadModel(modelRef);
                LOG.debug("通过 HuggingFace ID 解析模型: {} → {}", modelRef, path);
                return path;
            } catch (IOException e) {
                throw new ModelNotFoundException(modelRef, e);
            }
        }

        // 策略3：在缓存中搜索
        Optional<Path> cached = searchCache(modelRef);
        if (cached.isPresent()) {
            LOG.debug("在缓存中找到模型: {}", cached.get());
            return cached.get();
        }

        throw new ModelNotFoundException(modelRef);
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

    /** 在缓存目录中搜索匹配的 GGUF 文件 */
    private Optional<Path> searchCache(String modelRef) {
        if (!Files.isDirectory(cacheDir)) {
            return Optional.empty();
        }

        try {
            return Files.walk(cacheDir, 2)
                .filter(p -> p.getFileName().toString().endsWith(".gguf"))
                .filter(p -> p.getFileName().toString().contains(modelRef))
                .findFirst();
        } catch (IOException e) {
            LOG.warn("搜索缓存时出错: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
