# 编译原生动态库教程

llama4j 通过 JNI 调用 llama.cpp，需要为每个平台预编译原生动态库。本文档介绍如何在 macOS、Linux、Windows 上编译。

---

## 前置条件

| 平台 | 编译器 | 必需工具 | GPU SDK（可选） |
|------|--------|----------|----------------|
| macOS | Clang (Xcode Command Line Tools) | CMake 3.16+, JDK 17 | Metal（系统自带） |
| Linux | GCC 10+ | CMake 3.16+, JDK 17 | CUDA Toolkit 12.x / Vulkan SDK |
| Windows | MSVC (Visual Studio 2022) | CMake 3.16+, JDK 17 | CUDA Toolkit 12.x |

---

## GPU 后端对比

| GPU 后端 | 平台 | 硬件要求 | CMake 选项 |
|----------|------|----------|-----------|
| **Metal** | macOS | Apple Silicon / Intel Mac | `-DGGML_METAL=ON` |
| **CUDA** | Linux, Windows | NVIDIA GPU | `-DGGML_CUDA=ON` |
| **Vulkan** | Linux, Windows | 任何 Vulkan 兼容 GPU | `-DGGML_VULKAN=ON` |
| CPU-only | 全平台 | 无 | （不添加任何 GPU 选项） |

> 即使编译了 GPU 后端，也可以通过设置 `nGpuLayers=0` 回退到纯 CPU 推理。

---

## 快速编译（使用脚本）

```bash
# macOS (Apple Silicon, Metal GPU)
./scripts/build-native.sh --classifier macos-aarch64 --gpu metal

# macOS (Intel, Metal GPU)
./scripts/build-native.sh --classifier macos-x86_64 --gpu metal

# Linux (CUDA)
./scripts/build-native.sh --classifier linux-x86_64 --gpu cuda

# Linux (Vulkan - 无 NVIDIA GPU 的选择)
./scripts/build-native.sh --classifier linux-x86_64 --gpu vulkan

# Linux (纯 CPU)
./scripts/build-native.sh --classifier linux-x86_64 --gpu cpu
```

Windows (PowerShell):
```powershell
# CUDA
.\scripts\build-native.ps1 -Classifier windows-x86_64 -Gpu cuda

# CPU-only
.\scripts\build-native.ps1 -Classifier windows-x86_64 -Gpu cpu
```

编译产物会自动放置到 `llama4j-native/src/main/resources/native/{classifier}/`。

---

## 手动编译步骤

### 1. 克隆 llama.cpp

```bash
git clone --depth 1 https://github.com/ggerganov/llama.cpp.git /tmp/llama.cpp
```

### 2. 编译 llama.cpp

#### macOS (Metal + Accelerate BLAS)

```bash
cd /tmp/llama.cpp
cmake -B build \
  -DCMAKE_BUILD_TYPE=Release \
  -DBUILD_SHARED_LIBS=ON \
  -DGGML_METAL=ON \
  -DGGML_METAL_EMBED_LIBRARY=ON \
  -DGGML_BLAS=ON \
  -DGGML_BLAS_VENDOR=Apple \
  -DLLAMA_BUILD_TESTS=OFF \
  -DLLAMA_BUILD_EXAMPLES=OFF \
  -DLLAMA_BUILD_TOOLS=OFF \
  -DLLAMA_BUILD_SERVER=OFF
cmake --build build --config Release -j$(sysctl -n hw.ncpu)
```

#### Linux (CUDA)

```bash
cd /tmp/llama.cpp
cmake -B build \
  -DCMAKE_BUILD_TYPE=Release \
  -DBUILD_SHARED_LIBS=ON \
  -DGGML_CUDA=ON \
  -DLLAMA_BUILD_TESTS=OFF \
  -DLLAMA_BUILD_EXAMPLES=OFF \
  -DLLAMA_BUILD_TOOLS=OFF \
  -DLLAMA_BUILD_SERVER=OFF
cmake --build build --config Release -j$(nproc)
```

#### Linux (Vulkan)

```bash
# 先安装 Vulkan SDK
# Ubuntu: sudo apt install libvulkan-dev vulkan-tools
# 或从 https://vulkan.lunarg.com/ 下载 SDK

cd /tmp/llama.cpp
cmake -B build \
  -DCMAKE_BUILD_TYPE=Release \
  -DBUILD_SHARED_LIBS=ON \
  -DGGML_VULKAN=ON \
  -DLLAMA_BUILD_TESTS=OFF \
  -DLLAMA_BUILD_EXAMPLES=OFF \
  -DLLAMA_BUILD_TOOLS=OFF \
  -DLLAMA_BUILD_SERVER=OFF
cmake --build build --config Release -j$(nproc)
```

#### Linux (CPU-only)

```bash
cd /tmp/llama.cpp
cmake -B build \
  -DCMAKE_BUILD_TYPE=Release \
  -DBUILD_SHARED_LIBS=ON \
  -DLLAMA_BUILD_TESTS=OFF \
  -DLLAMA_BUILD_EXAMPLES=OFF \
  -DLLAMA_BUILD_TOOLS=OFF \
  -DLLAMA_BUILD_SERVER=OFF
cmake --build build --config Release -j$(nproc)
```

#### Windows (CUDA)

```powershell
# 安装 CUDA Toolkit: https://developer.nvidia.com/cuda-downloads
# 安装 Visual Studio 2022 with C++ development tools

cd C:\llama.cpp
cmake -B build `
  -DCMAKE_BUILD_TYPE=Release `
  -DBUILD_SHARED_LIBS=ON `
  -DGGML_CUDA=ON `
  -DLLAMA_BUILD_TESTS=OFF `
  -DLLAMA_BUILD_EXAMPLES=OFF `
  -DLLAMA_BUILD_TOOLS=OFF `
  -DLLAMA_BUILD_SERVER=OFF
cmake --build build --config Release --parallel
```

### 3. 编译 JNI 桥接库

#### macOS

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17)
OUTPUT_DIR="llama4j-native/src/main/resources/native/macos-aarch64"
mkdir -p "$OUTPUT_DIR"

# 复制 llama.cpp 库（只保留非版本化文件名）
cp /tmp/llama.cpp/build/bin/libggml*.dylib "$OUTPUT_DIR/"
cp /tmp/llama.cpp/build/bin/libllama*.dylib "$OUTPUT_DIR/"
cp /tmp/llama.cpp/build/bin/libmtmd*.dylib "$OUTPUT_DIR/"
cd "$OUTPUT_DIR" && rm -f *.0.*.dylib *.0.dylib && cd -

# 编译 JNI 桥接
clang++ -shared -fPIC -O2 \
  -I"$JAVA_HOME/include" \
  -I"$JAVA_HOME/include/darwin" \
  -I/tmp/llama.cpp/include \
  -I/tmp/llama.cpp/ggml/include \
  -o "$OUTPUT_DIR/libllama4j.dylib" \
  llama4j-native/src/main/c++/llama4j.cpp \
  -L/tmp/llama.cpp/build/bin \
  -lllama -lllama-common -lmtmd \
  -lggml -lggml-base -lggml-cpu -lggml-metal \
  -framework Metal -framework Foundation -framework MetalKit \
  -install_name @rpath/libllama4j.dylib
```

#### Linux

```bash
OUTPUT_DIR="llama4j-native/src/main/resources/native/linux-x86_64"
mkdir -p "$OUTPUT_DIR"

# 复制 llama.cpp 库
cp /tmp/llama.cpp/build/bin/libggml*.so* "$OUTPUT_DIR/"
cp /tmp/llama.cpp/build/bin/libllama*.so* "$OUTPUT_DIR/"
cp /tmp/llama.cpp/build/bin/libmtmd*.so* "$OUTPUT_DIR/"
cd "$OUTPUT_DIR" && rm -f *.so.* && cd-

# CUDA 版本
g++ -shared -fPIC -O2 \
  -I"$JAVA_HOME/include" \
  -I"$JAVA_HOME/include/linux" \
  -I/tmp/llama.cpp/include \
  -I/tmp/llama.cpp/ggml/include \
  -o "$OUTPUT_DIR/libllama4j.so" \
  llama4j-native/src/main/c++/llama4j.cpp \
  -L/tmp/llama.cpp/build/bin \
  -lllama -lllama-common -lmtmd \
  -lggml -lggml-base -lggml-cpu -lggml-cuda \
  -lcublas -lcudart -lculibos \
  -Wl,-rpath,'$ORIGIN'

# Vulkan 版本（把 -lggml-cuda -lcublas -lcudart -lculibos 替换为 -lggml-vulkan -lvulkan）
# CPU-only 版本（去掉所有 GPU 链接库）
```

#### Windows

```powershell
$OUTPUT_DIR = "llama4j-native\src\main\resources\native\windows-x86_64"
New-Item -ItemType Directory -Force -Path $OUTPUT_DIR

# 复制 llama.cpp DLL
Copy-Item C:\llama.cpp\build\bin\Release\ggml*.dll $OUTPUT_DIR
Copy-Item C:\llama.cpp\build\bin\Release\llama*.dll $OUTPUT_DIR
Copy-Item C:\llama.cpp\build\bin\Release\mtmd*.dll $OUTPUT_DIR

# 使用 CMake 编译 JNI 桥接
cmake -B llama4j-native\src\main\c++\build `
  -S llama4j-native\src\main\c++ `
  -DLLAMA_CPP_ROOT=C:\llama.cpp
cmake --build llama4j-native\src\main\c++\build --config Release
Copy-Item llama4j-native\src\main\c++\build\Release\llama4j.dll $OUTPUT_DIR
```

### 4. 构建 Java 项目

```bash
mvn clean install -DskipTests -Dmaven.javadoc.skip=true
```

---

## 编译产物结构

编译完成后，每个平台的资源目录应包含以下文件：

### macOS (`native/macos-aarch64/` 或 `native/macos-x86_64/`)
```
libggml-base.dylib     # GGML 基础库
libggml.dylib          # GGML 核心库
libggml-cpu.dylib      # GGML CPU 后端
libggml-metal.dylib    # GGML Metal GPU 后端
libllama-common.dylib  # llama.cpp 公共库
libllama.dylib         # llama.cpp 核心库
libmtmd.dylib          # 多模态支持库
libllama4j.dylib       # JNI 桥接库（我们编译的）
```

### Linux (`native/linux-x86_64/`)
```
libggml-base.so        # GGML 基础库
libggml.so             # GGML 核心库
libggml-cpu.so         # GGML CPU 后端
libggml-cuda.so        # GGML CUDA GPU 后端（CUDA 构建）
libllama-common.so     # llama.cpp 公共库
libllama.so            # llama.cpp 核心库
libmtmd.so             # 多模态支持库
libllama4j.so          # JNI 桥接库
```

### Windows (`native/windows-x86_64/`)
```
ggml-base.dll          # GGML 基础库
ggml.dll               # GGML 核心库
ggml-cpu.dll           # GGML CPU 后端
ggml-cuda.dll          # GGML CUDA GPU 后端（CUDA 构建）
llama-common.dll       # llama.cpp 公共库
llama.dll              # llama.cpp 核心库
mtmd.dll               # 多模态支持库
llama4j.dll            # JNI 桥接库
```

---

## CI/CD 自动构建

项目配置了 GitHub Actions 工作流（`.github/workflows/build-native.yml`），推送到 `main` 分支或创建 PR 时自动构建以下平台：

| Runner | 平台 | GPU 后端 |
|--------|------|----------|
| `macos-14` | macOS aarch64 (Apple Silicon) | Metal |
| `macos-13` | macOS x86_64 (Intel) | Metal |
| `ubuntu-22.04` | Linux x86_64 | CUDA |
| `windows-2022` | Windows x86_64 | CUDA |

> **GPU 说明**: macOS runner 自带 Apple Silicon，Metal 可正常编译。Linux/Windows runner 通过安装 CUDA Toolkit **编译**（不需要实际 GPU 硬件）。运行时如果机器没有 GPU，设置 `nGpuLayers=0` 即可使用 CPU 推理。

---

## 常见问题

### 编译时报 `llama_free_model is deprecated`
llama.cpp API 版本更新了。用 `llama_model_free` 替代 `llama_free_model`，用 `llama_vocab_is_eog` 替代 `llama_token_is_eog`。详见 `llama4j.cpp` 源码。

### Linux 加载时 `libggml-cuda.so: cannot open shared object file`
确保编译时使用了 `-Wl,-rpath,'$ORIGIN'` 链接选项，让动态库从同目录加载依赖。

### Windows 编译报 `Cannot find JNI headers`
设置 `JAVA_HOME` 环境变量指向 JDK 17 安装目录（如 `C:\Program Files\Eclipse Adoptium\jdk-17.0.19-hotspot`）。

### macOS `library not loaded` 错误
确保 `.dylib` 文件的 `install_name` 设置正确（`@rpath/libllama4j.dylib`），所有依赖库在同一目录。

### 如何只编译 CPU 版本（不需要 GPU）？
构建 llama.cpp 时不添加任何 GPU 相关的 CMake 选项即可。生成的库不包含 GPU 后端，体积更小，但推理速度较慢。
