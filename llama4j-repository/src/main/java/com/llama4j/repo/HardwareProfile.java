package com.llama4j.repo;

/**
 * 硬件配置档案 — 自适应量化推荐
 *
 * <p>检测系统硬件能力（RAM、GPU VRAM、CPU 特性），并根据模型参数量
 * 推荐最优的量化级别。确保模型能在当前硬件上流畅运行。</p>
 *
 * <h2>量化推荐逻辑</h2>
 * <table>
 * <caption>量化推荐</caption>
 *   <tr><th>可用内存</th><th>7B 模型</th><th>13B 模型</th><th>70B 模型</th></tr>
 *   <tr><td>8GB</td><td>Q4_K_M</td><td>Q2_K</td><td>不推荐</td></tr>
 *   <tr><td>16GB</td><td>Q5_K_M</td><td>Q4_K_M</td><td>Q2_K</td></tr>
 *   <tr><td>32GB</td><td>Q6_K</td><td>Q5_K_M</td><td>Q4_K_M</td></tr>
 *   <tr><td>64GB+</td><td>Q8_0</td><td>Q6_K</td><td>Q5_K_M</td></tr>
 * </table>
 *
 * @param totalRamGB 系统总内存（GB）
 * @param gpuVramGB  GPU 显存（GB），0 表示无 GPU
 * @param hasAvx2    CPU 是否支持 AVX2 指令集
 * @param hasCuda    是否有 CUDA 支持
 * @param hasMetal   是否有 Apple Metal 支持
 * @param cpuCores   CPU 核心数
 */
public record HardwareProfile(
    double totalRamGB,
    double gpuVramGB,
    boolean hasAvx2,
    boolean hasCuda,
    boolean hasMetal,
    int cpuCores
) {

    /**
     * 自动检测当前硬件配置。
     *
     * @return 检测到的硬件配置
     */
    public static HardwareProfile detect() {
        double totalRamGB = detectTotalRam();
        double gpuVramGB = detectGpuVram();
        boolean hasAvx2 = detectAvx2();
        boolean hasCuda = detectCuda();
        boolean hasMetal = detectMetal();
        int cpuCores = Runtime.getRuntime().availableProcessors();

        return new HardwareProfile(totalRamGB, gpuVramGB, hasAvx2, hasCuda, hasMetal, cpuCores);
    }

    /**
     * 根据模型参数量推荐最佳量化级别。
     *
     * <p>推荐逻辑基于经验值：每个参数在 Q4 量化下约需 0.6 字节内存，
     * Q5 约 0.75 字节，Q8 约 1.1 字节。加上 KV 缓存和运行时开销，
     * 实际所需内存约为模型大小的 1.2-1.5 倍。</p>
     *
     * @param modelParamsBillion 模型参数量（十亿），如 7.0 表示 7B
     * @return 推荐的量化后缀（如 "Q4_K_M"）
     */
    public String recommendQuantization(double modelParamsBillion) {
        // 计算可用总内存（系统 + GPU）
        double availableGB = totalRamGB + gpuVramGB;

        // 不同量化级别的每参数内存占用（字节）
        double q2Bytes = 0.35;  // Q2_K — 最小体积，质量较低
        double q4Bytes = 0.60;  // Q4_K_M — 平衡体积与质量
        double q5Bytes = 0.75;  // Q5_K_M — 较高质量
        double q6Bytes = 0.90;  // Q6_K — 高质量
        double q8Bytes = 1.10;  // Q8_0 — 接近原始质量

        // 预留 30% 内存给 KV 缓存和系统开销
        double usableGB = availableGB * 0.7;
        double modelSizeGB = modelParamsBillion * 1e9 * q4Bytes / (1024 * 1024 * 1024);

        // 从最高质量开始尝试，找到能装下的最佳量化
        if (modelParamsBillion * 1e9 * q8Bytes / (1024.0 * 1024 * 1024) <= usableGB) {
            return "Q8_0";
        } else if (modelParamsBillion * 1e9 * q6Bytes / (1024.0 * 1024 * 1024) <= usableGB) {
            return "Q6_K";
        } else if (modelParamsBillion * 1e9 * q5Bytes / (1024.0 * 1024 * 1024) <= usableGB) {
            return "Q5_K_M";
        } else if (modelParamsBillion * 1e9 * q4Bytes / (1024.0 * 1024 * 1024) <= usableGB) {
            return "Q4_K_M";
        } else if (modelParamsBillion * 1e9 * q2Bytes / (1024.0 * 1024 * 1024) <= usableGB) {
            return "Q2_K";
        } else {
            return "Q2_K"; // 即使装不下也返回最小量化
        }
    }

    /* ──────────────────────────────────────────
     *  硬件检测辅助方法
     *  ────────────────────────────────────────── */

    /** 检测系统总内存 */
    private static double detectTotalRam() {
        try {
            com.sun.management.OperatingSystemMXBean osBean =
                (com.sun.management.OperatingSystemMXBean)
                    java.lang.management.ManagementFactory.getOperatingSystemMXBean();
            return osBean.getTotalMemorySize() / (1024.0 * 1024.0 * 1024.0);
        } catch (Exception e) {
            return 16.0; // 默认 16GB
        }
    }

    /** 检测 GPU 显存（需要原生代码或 CUDA 运行时） */
    private static double detectGpuVram() {
        return 0.0; // 默认无 GPU，实际检测在原生层完成
    }

    /** 检测 CPU 是否支持 AVX2 */
    private static boolean detectAvx2() {
        try {
            if (System.getProperty("os.name").toLowerCase().contains("linux")) {
                String cpuinfo = java.nio.file.Files.readString(java.nio.file.Path.of("/proc/cpuinfo"));
                return cpuinfo.contains("avx2");
            }
        } catch (Exception ignored) {}
        return false;
    }

    /** 检测 CUDA 是否可用 */
    private static boolean detectCuda() {
        try {
            ProcessBuilder pb = new ProcessBuilder("nvidia-smi");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            return p.waitFor() == 0;
        } catch (Exception ignored) {
            return false;
        }
    }

    /** 检测 Apple Metal 是否可用 */
    private static boolean detectMetal() {
        String osName = System.getProperty("os.name", "").toLowerCase();
        return osName.contains("mac") && System.getProperty("os.arch", "").toLowerCase().contains("aarch64");
    }

    @Override
    public String toString() {
        return String.format("HardwareProfile{内存=%.1fGB, GPU=%.1fGB, AVX2=%s, CUDA=%s, Metal=%s, 核心数=%d}",
            totalRamGB, gpuVramGB, hasAvx2, hasCuda, hasMetal, cpuCores);
    }
}
