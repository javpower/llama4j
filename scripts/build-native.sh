#!/usr/bin/env bash
#
# llama4j 原生库编译脚本 (macOS / Linux)
#
# 用法:
#   ./scripts/build-native.sh --classifier macos-aarch64 --gpu metal
#   ./scripts/build-native.sh --classifier linux-x86_64 --gpu cuda
#   ./scripts/build-native.sh --classifier linux-x86_64 --gpu vulkan
#   ./scripts/build-native.sh --classifier linux-x86_64 --gpu cpu
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
LLAMA_CPP_DIR="${LLAMA_CPP_DIR:-/tmp/llama.cpp}"
LLAMA_CPP_VERSION="${LLAMA_CPP_VERSION:-master}"
CLASSIFIER=""
GPU_BACKEND=""
OUTPUT_DIR=""
JOBS="$(nproc 2>/dev/null || sysctl -n hw.ncpu 2>/dev/null || echo 4)"

usage() {
    echo "用法: $0 --classifier <os-arch> --gpu <backend> [--output-dir <path>] [--jobs <n>]"
    echo ""
    echo "参数:"
    echo "  --classifier   平台标识符: macos-aarch64, macos-x86_64, linux-x86_64, linux-aarch64"
    echo "  --gpu          GPU 后端: metal, cuda, vulkan, cpu"
    echo "  --output-dir   输出目录 (默认: llama4j-native/src/main/resources/native/{classifier})"
    echo "  --jobs         并行编译数 (默认: CPU 核心数)"
    echo ""
    echo "示例:"
    echo "  $0 --classifier macos-aarch64 --gpu metal"
    echo "  $0 --classifier linux-x86_64 --gpu cuda"
    exit 1
}

while [[ $# -gt 0 ]]; do
    case $1 in
        --classifier) CLASSIFIER="$2"; shift 2 ;;
        --gpu) GPU_BACKEND="$2"; shift 2 ;;
        --output-dir) OUTPUT_DIR="$2"; shift 2 ;;
        --jobs) JOBS="$2"; shift 2 ;;
        *) echo "未知参数: $1"; usage ;;
    esac
done

[[ -z "$CLASSIFIER" ]] && echo "错误: 必须指定 --classifier" && usage
[[ -z "$GPU_BACKEND" ]] && echo "错误: 必须指定 --gpu" && usage

if [[ -z "$OUTPUT_DIR" ]]; then
    OUTPUT_DIR="$PROJECT_DIR/llama4j-native/src/main/resources/native/$CLASSIFIER"
fi

echo "=========================================="
echo " llama4j 原生库编译"
echo "=========================================="
echo " 平台:      $CLASSIFIER"
echo " GPU 后端:  $GPU_BACKEND"
echo " 输出目录:  $OUTPUT_DIR"
echo " 并行数:    $JOBS"
echo "=========================================="

# ── Step 1: 克隆/更新 llama.cpp ──
if [[ ! -d "$LLAMA_CPP_DIR/.git" ]]; then
    echo "[1/4] 克隆 llama.cpp..."
    git clone --depth 1 --branch "$LLAMA_CPP_VERSION" \
        https://github.com/ggerganov/llama.cpp.git "$LLAMA_CPP_DIR"
else
    echo "[1/4] 更新 llama.cpp..."
    cd "$LLAMA_CPP_DIR"
    git fetch --depth 1 origin "$LLAMA_CPP_VERSION"
    git reset --hard "origin/$LLAMA_CPP_VERSION"
    cd - > /dev/null
fi

# ── Step 2: 编译 llama.cpp ──
echo "[2/4] 编译 llama.cpp..."

CMAKE_ARGS=(
    -DCMAKE_BUILD_TYPE=Release
    -DBUILD_SHARED_LIBS=ON
    -DLLAMA_BUILD_TESTS=OFF
    -DLLAMA_BUILD_EXAMPLES=OFF
    -DLLAMA_BUILD_TOOLS=OFF
    -DLLAMA_BUILD_SERVER=OFF
    -DLLAMA_BUILD_COMMON=ON
)

case "$GPU_BACKEND" in
    metal)
        CMAKE_ARGS+=(
            -DGGML_METAL=ON
            -DGGML_METAL_EMBED_LIBRARY=ON
            -DGGML_BLAS=ON
            -DGGML_BLAS_VENDOR=Apple
        )
        ;;
    cuda)
        CMAKE_ARGS+=(
            -DGGML_CUDA=ON
        )
        ;;
    vulkan)
        CMAKE_ARGS+=(
            -DGGML_VULKAN=ON
        )
        ;;
    cpu)
        # 无 GPU 后端，纯 CPU
        ;;
    *)
        echo "错误: 不支持的 GPU 后端: $GPU_BACKEND"
        exit 1
        ;;
esac

cmake -B "$LLAMA_CPP_DIR/build" -S "$LLAMA_CPP_DIR" "${CMAKE_ARGS[@]}"
cmake --build "$LLAMA_CPP_DIR/build" --config Release -j"$JOBS"

# ── Step 3: 编译 JNI 桥接 ──
echo "[3/4] 编译 JNI 桥接库..."

mkdir -p "$OUTPUT_DIR"

# 复制 llama.cpp 共享库
if [[ "$CLASSIFIER" == macos-* ]]; then
    cp -L "$LLAMA_CPP_DIR/build/bin/"libggml*.dylib "$OUTPUT_DIR/" 2>/dev/null || true
    cp -L "$LLAMA_CPP_DIR/build/bin/"libllama*.dylib "$OUTPUT_DIR/" 2>/dev/null || true

    # 只保留非版本化的库文件名
    cd "$OUTPUT_DIR"
    for f in *.0.*.dylib *.0.dylib; do
        [[ -f "$f" ]] && rm -f "$f"
    done
    cd - > /dev/null

    JAVA_HOME="${JAVA_HOME:-$(/usr/libexec/java_home -v 17 2>/dev/null || echo "")}"

    clang++ -std=c++17 -shared -fPIC -O2 \
        -I"$JAVA_HOME/include" \
        -I"$JAVA_HOME/include/darwin" \
        -I"$LLAMA_CPP_DIR/include" \
        -I"$LLAMA_CPP_DIR/ggml/include" \
        -o "$OUTPUT_DIR/libllama4j.dylib" \
        "$PROJECT_DIR/llama4j-native/src/main/c++/llama4j.cpp" \
        -L"$LLAMA_CPP_DIR/build/bin" \
        -lllama -lllama-common \
        -lggml -lggml-base -lggml-cpu -lggml-metal \
        -framework Metal -framework Foundation -framework MetalKit \
        -install_name @rpath/libllama4j.dylib

elif [[ "$CLASSIFIER" == linux-* ]]; then
    cp -L "$LLAMA_CPP_DIR/build/bin/"libggml*.so* "$OUTPUT_DIR/" 2>/dev/null || true
    cp -L "$LLAMA_CPP_DIR/build/bin/"libllama*.so* "$OUTPUT_DIR/" 2>/dev/null || true

    # 只保留非版本化的库文件名
    cd "$OUTPUT_DIR"
    for f in *.so.*; do
        [[ -f "$f" ]] && rm -f "$f"
    done
    cd - > /dev/null

    LINK_LIBS="-lllama -lllama-common -lggml -lggml-base -lggml-cpu"

    if [[ "$GPU_BACKEND" == "cuda" ]]; then
        LINK_LIBS="$LINK_LIBS -lggml-cuda -lcublas -lcudart -lculibos"
    elif [[ "$GPU_BACKEND" == "vulkan" ]]; then
        LINK_LIBS="$LINK_LIBS -lggml-vulkan -lvulkan"
    fi

    g++ -std=c++17 -shared -fPIC -O2 \
        -I"$JAVA_HOME/include" \
        -I"$JAVA_HOME/include/linux" \
        -I"$LLAMA_CPP_DIR/include" \
        -I"$LLAMA_CPP_DIR/ggml/include" \
        -o "$OUTPUT_DIR/libllama4j.so" \
        "$PROJECT_DIR/llama4j-native/src/main/c++/llama4j.cpp" \
        -L"$LLAMA_CPP_DIR/build/bin" \
        $LINK_LIBS \
        -Wl,-rpath,'$ORIGIN'
fi

# ── Step 4: 验证 ──
echo "[4/4] 验证编译产物..."
echo ""
echo "输出文件:"
ls -lh "$OUTPUT_DIR/"
echo ""
echo "编译完成！"
