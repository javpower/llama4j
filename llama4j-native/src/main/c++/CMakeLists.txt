cmake_minimum_required(VERSION 3.16)
project(llama4j LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# ── 查找 JNI ──
if(DEFINED JAVA_HOME AND EXISTS "${JAVA_HOME}/include")
    set(JNI_INCLUDE_DIRS "${JAVA_HOME}/include")
    if(WIN32)
        list(APPEND JNI_INCLUDE_DIRS "${JAVA_HOME}/include/win32")
    elseif(APPLE)
        list(APPEND JNI_INCLUDE_DIRS "${JAVA_HOME}/include/darwin")
    else()
        list(APPEND JNI_INCLUDE_DIRS "${JAVA_HOME}/include/linux")
    endif()
    message(STATUS "JNI include dirs (from JAVA_HOME): ${JNI_INCLUDE_DIRS}")
else()
    find_package(JNI REQUIRED)
endif()

# ── 查找 llama.cpp（从 LLAMA_CPP_ROOT 或默认路径）──
if(NOT LLAMA_CPP_ROOT)
    set(LLAMA_CPP_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/../../../../llama.cpp")
    if(NOT EXISTS "${LLAMA_CPP_ROOT}/CMakeLists.txt")
        set(LLAMA_CPP_ROOT "/tmp/llama.cpp")
    endif()
endif()

message(STATUS "使用 llama.cpp 路径: ${LLAMA_CPP_ROOT}")

# 头文件路径
set(LLAMA_INCLUDE_DIR "${LLAMA_CPP_ROOT}/include")
set(GGML_INCLUDE_DIR "${LLAMA_CPP_ROOT}/ggml/include")

# 库文件路径
if(APPLE)
    set(LLAMA_LIB_DIR "${LLAMA_CPP_ROOT}/build/bin")
elseif(UNIX)
    set(LLAMA_LIB_DIR "${LLAMA_CPP_ROOT}/build/bin")
else()
    set(LLAMA_LIB_DIR "${LLAMA_CPP_ROOT}/build/bin/Release")
endif()

# 源文件
set(SOURCES llama4j.cpp)

# 共享库目标
add_library(llama4j SHARED ${SOURCES})

target_include_directories(llama4j PRIVATE
    ${JNI_INCLUDE_DIRS}
    ${LLAMA_INCLUDE_DIR}
    ${GGML_INCLUDE_DIR}
    ${CMAKE_CURRENT_SOURCE_DIR}
)

# 链接 llama.cpp 库
if(APPLE)
    target_link_libraries(llama4j PRIVATE
        ${LLAMA_LIB_DIR}/libllama-common.dylib
        ${LLAMA_LIB_DIR}/libllama.dylib
        ${LLAMA_LIB_DIR}/libggml.dylib
        ${LLAMA_LIB_DIR}/libggml-base.dylib
        ${LLAMA_LIB_DIR}/libggml-cpu.dylib
        ${LLAMA_LIB_DIR}/libggml-metal.dylib
        "-framework Metal"
        "-framework Foundation"
        "-framework MetalKit"
    )
    set_target_properties(llama4j PROPERTIES
        INSTALL_RPATH "@loader_path"
        BUILD_WITH_INSTALL_RPATH TRUE
    )
elseif(UNIX)
    target_link_libraries(llama4j PRIVATE
        ${LLAMA_LIB_DIR}/libllama-common.so
        ${LLAMA_LIB_DIR}/libllama.so
        ${LLAMA_LIB_DIR}/libggml.so
        ${LLAMA_LIB_DIR}/libggml-base.so
        ${LLAMA_LIB_DIR}/libggml-cpu.so
    )
    # 可选 CUDA
    if(EXISTS "${LLAMA_LIB_DIR}/libggml-cuda.so")
        target_link_libraries(llama4j PRIVATE ${LLAMA_LIB_DIR}/libggml-cuda.so)
    endif()
    set_target_properties(llama4j PROPERTIES
        INSTALL_RPATH "$ORIGIN"
        BUILD_WITH_INSTALL_RPATH TRUE
    )
else()
    # Windows
    target_link_libraries(llama4j PRIVATE
        ${LLAMA_LIB_DIR}/llama-common.lib
        ${LLAMA_LIB_DIR}/llama.lib
        ${LLAMA_LIB_DIR}/ggml.lib
        ${LLAMA_LIB_DIR}/ggml-base.lib
        ${LLAMA_LIB_DIR}/ggml-cpu.lib
    )
    if(EXISTS "${LLAMA_LIB_DIR}/ggml-cuda.lib")
        target_link_libraries(llama4j PRIVATE ${LLAMA_LIB_DIR}/ggml-cuda.lib)
    endif()
endif()
