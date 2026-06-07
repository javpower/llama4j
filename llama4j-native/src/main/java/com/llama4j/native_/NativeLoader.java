package com.llama4j.native_;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 平台感知的原生库加载器
 *
 * <p>负责从 classpath 中提取 JNI 原生库并加载到 JVM 中。
 * 支持 Linux、macOS、Windows 三大操作系统，以及 x86_64 和 aarch64 两种架构。</p>
 *
 * <h2>加载策略</h2>
 * <ol>
 *   <li>检测当前操作系统和 CPU 架构</li>
 *   <li>构造预期的库文件名（如 libllama4j.so、libllama4j.dylib、llama4j.dll）</li>
 *   <li>从 classpath 的 native/{classifier}/ 目录下提取库到临时目录</li>
 *   <li>通过 {@link System#load} 加载提取出的库文件</li>
 * </ol>
 *
 * <h2>为什么不用 System.loadLibrary？</h2>
 * <p>System.loadLibrary 要求库文件在 java.library.path 指定的目录中，
 * 这对部署不友好。我们的方式是将库打包在 JAR 中，运行时自动提取，
 * 实现真正的"零配置"体验。</p>
 *
 * <h2>线程安全</h2>
 * <p>使用 AtomicBoolean 确保库只被加载一次，即使多个线程同时触发加载。</p>
 */
final class NativeLoader {

    private static final Logger LOG = LoggerFactory.getLogger(NativeLoader.class);

    /** 原子标志，确保库只加载一次 */
    private static final AtomicBoolean LOADED = new AtomicBoolean(false);

    private NativeLoader() {} // 工具类，禁止实例化

    /**
     * 加载指定名称的原生库。此方法是幂等的——重复调用不会重复加载。
     *
     * @param libraryName 原生库的基础名称（如 "llama4j"）
     * @throws UnsatisfiedLinkError 如果库无法找到或加载
     */
    static void load(String libraryName) {
        if (LOADED.compareAndSet(false, true)) {
            try {
                Platform platform = detectPlatform();
                Path tempDir = Files.createTempDirectory("llama4j-");
                tempDir.toFile().deleteOnExit();

                // 加载依赖库（顺序很重要）
                String[] deps = getDependenciesForPlatform(platform);

                for (String dep : deps) {
                    extractAndLoad(platform, tempDir, dep);
                }

                // 加载主库
                String libraryFileName = getLibraryFileName(platform, libraryName);
                String resourcePath = "/native/" + platform.classifier() + "/" + libraryFileName;

                LOG.info("正在加载原生库: {} (平台: {})", libraryFileName, platform);
                Path tempFile = extractToTempFile(resourcePath, libraryFileName, tempDir);
                System.load(tempFile.toString());

                LOG.info("原生库加载成功: {}", tempFile);
            } catch (Exception e) {
                LOADED.set(false); // 加载失败，重置标志以允许重试
                throw new UnsatisfiedLinkError(
                    "无法加载 llama4j 原生库: " + e.getMessage());
            }
        }
    }

    /**
     * 从 classpath 提取原生库到临时文件。
     *
     * <p>原生库被打包在 JAR 文件中，无法直接被 System.load 加载。
     * 必须先提取到文件系统，然后通过绝对路径加载。</p>
     *
     * @param resourcePath   classpath 中的资源路径
     * @param libraryFileName 库文件名
     * @return 提取后的临时文件路径
     */
    private static Path extractToTempFile(String resourcePath, String libraryFileName, Path tempDir) throws IOException {
        try (InputStream is = NativeLoader.class.getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IOException("在 classpath 中未找到原生库: " + resourcePath);
            }

            Path tempFile = tempDir.resolve(libraryFileName);
            Files.copy(is, tempFile, StandardCopyOption.REPLACE_EXISTING);
            tempFile.toFile().deleteOnExit();

            return tempFile;
        }
    }

    private static void extractAndLoad(Platform platform, Path tempDir, String libraryName) throws IOException {
        String fileName = getLibraryFileName(platform, libraryName);
        String resourcePath = "/native/" + platform.classifier() + "/" + fileName;

        try (InputStream is = NativeLoader.class.getResourceAsStream(resourcePath)) {
            if (is == null) {
                LOG.debug("跳过可选依赖库: {}", fileName);
                return;
            }

            Path tempFile = tempDir.resolve(fileName);
            Files.copy(is, tempFile, StandardCopyOption.REPLACE_EXISTING);
            tempFile.toFile().deleteOnExit();

            System.load(tempFile.toString());
            LOG.debug("依赖库加载成功: {}", fileName);
        }
    }

    private static String[] getDependenciesForPlatform(Platform platform) {
        return switch (platform.os()) {
            case MACOS -> new String[]{
                "ggml-base", "ggml-cpu", "ggml-blas", "ggml-metal", "ggml",
                "llama", "llama-common", "mtmd"
            };
            case LINUX -> new String[]{
                "ggml-base", "ggml", "ggml-cpu",
                "ggml-cuda", "ggml-vulkan",
                "llama-common", "llama", "mtmd"
            };
            case WINDOWS -> new String[]{
                "ggml-base", "ggml", "ggml-cpu",
                "ggml-cuda",
                "llama-common", "llama", "mtmd"
            };
        };
    }

    /**
     * 根据平台构造原生库文件名。
     *
     * <p>不同操作系统使用不同的库文件命名约定：</p>
     * <ul>
     *   <li>Linux:   lib{name}.so</li>
     *   <li>macOS:  lib{name}.dylib</li>
     *   <li>Windows: {name}.dll</li>
     * </ul>
     */
    private static String getLibraryFileName(Platform platform, String libraryName) {
        return switch (platform.os()) {
            case LINUX   -> "lib" + libraryName + ".so";
            case MACOS   -> "lib" + libraryName + ".dylib";
            case WINDOWS -> libraryName + ".dll";
        };
    }

    /**
     * 自动检测当前平台（操作系统 + CPU 架构）。
     *
     * <p>通过读取系统属性来识别运行环境。如果检测到不支持的平台，
     * 会抛出 UnsupportedOperationException。</p>
     */
    private static Platform detectPlatform() {
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String osArch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);

        // 检测操作系统
        OS os;
        if (osName.contains("linux"))       os = OS.LINUX;
        else if (osName.contains("mac"))     os = OS.MACOS;
        else if (osName.contains("win"))     os = OS.WINDOWS;
        else throw new UnsupportedOperationException("不支持的操作系统: " + osName);

        // 检测 CPU 架构
        Arch arch;
        if (osArch.matches("amd64|x86_64"))      arch = Arch.X86_64;
        else if (osArch.matches("aarch64|arm64")) arch = Arch.AARCH64;
        else throw new UnsupportedOperationException("不支持的 CPU 架构: " + osArch);

        Platform platform = new Platform(os, arch);
        LOG.debug("检测到平台: {}", platform);
        return platform;
    }

    /* ──────────────────────────────────────────
     *  内部类型定义
     *  ────────────────────────────────────────── */

    /** 操作系统枚举 */
    enum OS { LINUX, MACOS, WINDOWS }

    /** CPU 架构枚举 */
    enum Arch { X86_64, AARCH64 }

    /**
     * 平台描述 — 操作系统 + CPU 架构的组合
     *
     * @param os   操作系统
     * @param arch CPU 架构
     */
    record Platform(OS os, Arch arch) {
        /** 生成 Maven 风格的 classifier（如 linux-x86_64） */
        String classifier() {
            return os.name().toLowerCase(Locale.ROOT) + "-" + arch.name().toLowerCase(Locale.ROOT);
        }

        @Override
        public String toString() {
            return os + "/" + arch;
        }
    }
}
