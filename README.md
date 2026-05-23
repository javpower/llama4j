<div align="center">

# 🦙 llama4j

### **Java 生态唯一的生产级本地大模型推理框架**

**直接在 JVM 里跑 LLM —— 不需要 Python，不需要 Docker，不需要外部服务**

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![JDK](https://img.shields.io/badge/JDK-17+-green.svg)]()
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3+-6db33f.svg)]()
[![llama.cpp](https://img.shields.io/badge/llama.cpp-latest-orange.svg)]()
[![Modules](https://img.shields.io/badge/Modules-12-purple.svg)]()
[![GPU](https://img.shields.io/badge/GPU-Metal%20%7C%20CUDA%20%7C%20Vulkan-brightgreen.svg)]()

</div>

> [llama.cpp](https://github.com/ggerganov/llama.cpp) 是当今最强的高性能 LLM 推理引擎 —— Apple、Google、Microsoft 都在用它跑本地模型。
> **但它没有官方 Java 绑定。** Java 开发者要么起一个 Python 服务走 HTTP，要么调 Ollama 的 REST API，忍受 1-5ms 的网络延迟和 JSON 序列化开销。
>
> **llama4j 改变了这一切。**
>
> 通过 JNI 直接调用 llama.cpp 原生 C++ 核心，把大模型推理变成一个普通的 Java 方法调用 —— 零网络跳转、零序列化、零外部进程，延迟从毫秒级降到微秒级。同时内置浏览器端 AI 编码助手和云端大模型支持，本地模型 + 云端 API 一键切换。
>
> 一个 JAR。一个进程。GPU 全速。这就是 llama4j。

---

## 为什么需要 llama4j？

### 核心问题：Java 生态缺少本地 LLM 推理能力

2026 年了，所有主流 LLM 推理框架都在 Python 生态：

| 框架 | 语言 | Java 支持 |
|------|------|-----------|
| llama.cpp | C/C++ | 无官方绑定 |
| vLLM | Python | 无 |
| Ollama | Go | 仅 HTTP API |
| llamafile | C++ | 无 |
| ONNX Runtime | Python/C++ | 有但无 LLM 优化 |
| DJL | Java | 仅云端模型，无 GGUF |

**Java 开发者想跑本地大模型，只有两条路：**

```
❌ 方案 A：Python 侧车服务（痛点爆炸）

   [Spring Boot] ──HTTP──▶ [Python FastAPI]
         │                     │
         │                  [vLLM / ollama]
         │                     │
         │                  [CUDA Runtime]
         │                     │
   需要：Python 环境             需要：Docker + GPU 镜像
         维护两套代码                  服务发现 + 健康检查 ×2
         JSON 序列化开销              负载均衡
         网络延迟 (1-5ms/调用)        冷启动慢
         双语言栈运维成本              2× 内存开销


✅ 方案 B：llama4j（一个 JAR 搞定）

   [Spring Boot + llama4j]
         │
    直接 JNI 调用，零网络开销
    单进程，单 JAR
    自动 GPU 加速
    Spring Boot 原生集成
```

### 没有对比就没有伤害

| 维度 | Python 侧车方案 | Ollama | **llama4j** |
|------|-----------------|--------|-------------|
| **语言** | Python + HTTP 桥接 | Go（私有 API） | **Java 原生 JNI** |
| **部署** | 2 个服务 + Docker Compose | 独立二进制 | **单个 Spring Boot JAR** |
| **网络开销** | HTTP 1-5ms/调用 | HTTP 1-5ms | **进程内调用 0ms** |
| **序列化** | JSON 往返 | JSON 往返 | **直接对象传递** |
| **Spring 集成** | 手写 REST Client | 手写 REST Client | **Auto-config + DI + Actuator** |
| **OpenAI API** | 自己实现 | 内置（格式不同） | **完全兼容，即插即用** |
| **函数调用** | 自己拼 Prompt | 有限 | **@Tool 注解 + ReAct 循环** |
| **可观测性** | 两套监控系统 | 基础 | **Micrometer 8 指标自动导出** |
| **会话管理** | 无状态 | 基础 | **KV Cache 存档/恢复** |
| **聊天模板** | 每个模型硬编码 | 自动检测 | **10+ 格式 + Jinja2 解析器** |
| **内存开销** | Python 运行时 ~200MB | ~50MB | **仅 JVM ~20MB** |
| **DevOps** | 高（双技术栈） | 中（外部依赖） | **零（嵌入式）** |

---

## 架构

```
                         ┌─────────────────────────────────────┐
                         │        Your Spring Boot App         │
                         │   (REST Controllers, Services, DI)  │
                         └──────────────┬──────────────────────┘
                                        │
                                        ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│                        llama4j-spring-boot-starter                           │
│  ┌─────────────────┐   ┌──────────────────┐   ┌──────────────────────────┐  │
│  │  Auto-Config     │   │  OpenAI Compat   │   │  Actuator Endpoints      │  │
│  │  LlamaProperties │   │  /v1/chat/...    │   │  /health  /info          │  │
│  └────────┬────────┘   └────────┬─────────┘   └────────────┬─────────────┘  │
└───────────┼─────────────────────┼───────────────────────────┼────────────────┘
            │                     │                           │
            ▼                     ▼                           ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│                             llama4j-core                                     │
│  ┌───────────────────┐   ┌───────────────────┐   ┌───────────────────────┐  │
│  │   ChatService      │   │  SessionManager   │   │   InferenceStats      │  │
│  │   (sync + stream)  │   │  (KV checkpoint)  │   │   (latency, tokens)   │  │
│  └────────┬──────────┘   └────────┬──────────┘   └───────────┬───────────┘  │
└───────────┼───────────────────────┼───────────────────────────┼──────────────┘
            │                       │                           │
            ▼                       ▼                           ▼
┌────────────────────┐  ┌───────────────────┐  ┌──────────────────────────────┐
│   llama4j-chat     │  │  llama4j-tools    │  │       llama4j-metrics        │
│                    │  │                   │  │                              │
│  10+ chat formats  │  │  @Tool annotation │  │  8 Micrometer metrics        │
│  Jinja2 parser     │  │  ToolRegistry     │  │  Prometheus/Datadog export   │
│  Auto-detection    │  │  ReAct loop       │  │  InferenceTimer, TokenMeter  │
└────────┬───────────┘  └────────┬──────────┘  └──────────────┬───────────────┘
         │                       │                            │
         └───────────────────────┼────────────────────────────┘
                                 │
                                 ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│                           llama4j-native (JNI)                               │
│  ┌────────────────────────────────────────────────────────────────────────┐  │
│  │                        LlamaContext                                    │  │
│  │   generate() / generateStream() / tokenize() / saveSession()          │  │
│  │   embed() / GrammarConstraint / EmbeddingVector / MultimodalContext   │  │
│  │   Thread-safe (std::mutex) | Zero-copy buffers | Use-after-free guard  │  │
│  └────────────────────────────────────────────────────────────────────────┘  │
│  ┌──────────────────────────┐  ┌─────────────────────────────────────────┐  │
│  │   NativeLoader           │  │  llama.cpp C++ layer                    │  │
│  │   Platform detection     │  │  llama_batch + sampler chain            │  │
│  │   JAR extraction         │  │  Metal / CUDA / Vulkan / CPU backends   │  │
│  └──────────────────────────┘  └─────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────────────────────┘
                                 │
                                 ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│                         llama4j-repository                                   │
│  ┌──────────────────┐ ┌──────────────────┐ ┌──────────────────────────────┐  │
│  │  ModelScope (魔搭) │ │  HuggingFace Hub  │ │  Quantization Advisor       │  │
│  │  国内优先下载       │ │  国际回退下载      │ │  VRAM → Q4/Q5/Q8 recommend  │  │
│  └──────────────────┘ └──────────────────┘ └──────────────────────────────┘  │
└──────────────────────────────────────────────────────────────────────────────┘
```

---

## 快速开始

### 30 秒上手

```xml
<dependency>
    <groupId>com.llama4j</groupId>
    <artifactId>llama4j-spring-boot-starter</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

```yaml
llama4j:
  model:
    path: /models/qwen2.5-7b-q4_k_m.gguf
    # 也支持自动下载（ModelScope 优先，HuggingFace 回退）：
    # id: Qwen/Qwen2.5-7B-Instruct-GGUF:Q4_K_M
    # id: modelscope:Qwen/Qwen2.5-7B-Instruct-GGUF:Q4_K_M
    # id: hf:unsloth/Qwen2.5-7B-Instruct:Q4_K_M
    n-ctx: 4096
    n-gpu-layers: -1    # 全部层卸载到 GPU
    n-threads: 8
```

```bash
mvn spring-boot:run
```

```bash
curl -X POST http://localhost:8080/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{
    "messages": [
      {"role": "user", "content": "用一段话解释量子计算"}
    ],
    "temperature": 0.7
  }'
```

没有 Python。没有 Docker。没有外部依赖。一个 JAR，一个进程，大模型推理就在你的 Java 应用里跑起来了。

---

## 核心能力

### 本地模型推理 — 零外部依赖

这是 llama4j 的核心价值。不是调用云端 API，不是启动 Python 服务，而是**直接在 JVM 进程内跑 GGUF 模型**：

- **Metal** (Apple Silicon) / **CUDA** (NVIDIA) / **Vulkan** / **CPU** 全平台 GPU 加速
- 10+ 聊天模板自动检测（Llama 3, ChatML, Gemma, Phi-3, Mistral, DeepSeek, Jinja2...）
- KV Cache 存档/恢复，多轮对话无需重新 prompt
- Grammar 约束生成 + JSON Mode
- Embedding 向量 + 相似度搜索
- 流式输出（SSE）

### 视觉大模型（VLM）— 图片理解

llama4j 支持多模态推理，可以用 Qwen2-VL、LLaVA 等 VLM 模型进行图片理解：

**Java API：**

```java
// 加载 VLM 模型（需要模型文件 + mmproj 投影器权重）
LocalModel model = LocalModel.fromFileWithVision(
    "/models/Qwen2-VL-2B-Instruct-Q4_K_M.gguf",
    "/models/mmproj-Qwen2-VL-2B-Instruct-f16.gguf"
);

// 发送图片 + 文本
ChatRequest request = ChatRequest.builder()
    .addMessage(Role.USER, "描述这张图片")
    .images(List.of(ImageData.fromFile(Paths.get("photo.jpg"))))
    .build();
ChatResponse response = model.chat(request);
```

**YAML 配置（Spring Boot）：**

```yaml
llama4j:
  models:
    qwen2vl:
      type: local
      path: /models/Qwen2-VL-2B-Instruct-Q4_K_M.gguf
      mmproj-path: /models/mmproj-Qwen2-VL-2B-Instruct-f16.gguf
      n-ctx: 4096
      n-gpu-layers: -1
```

**OpenAI 兼容 API（REST）：**

```bash
curl -X POST http://localhost:8080/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{
    "model": "qwen2vl",
    "messages": [{"role": "user", "content": [
      {"type": "text", "text": "描述这张图片"},
      {"type": "image_url", "image_url": {"url": "data:image/jpeg;base64,..."}}
    ]}]
  }'
```

支持同步和流式（SSE）两种模式，前端可直接上传图片。

> **关于 mmproj**：VLM 模型需要两个文件协同工作——GGUF 模型文件（语言理解）和 mmproj 投影器（视觉编码）。部分模型的 mmproj 内嵌在 GGUF 中，此时 `mmproj-path` 与 `model-path` 设为相同即可。

### 云端大模型 — 同时支持

llama4j 不只是本地推理。通过 `llama4j-providers` 模块，无缝接入任何 OpenAI 兼容 API：

```yaml
llama4j:
  providers:
    - name: deepseek
      api-key: sk-xxx
      base-url: https://api.deepseek.com
      model: deepseek-chat
    - name: minimax
      api-key: xxx
      base-url: https://api.minimax.chat/v1
      model: MiniMax-M2.7
```

**本地模型 + 云端模型，同一套 API，运行时一键切换。**

### 函数调用 — Java 原生 @Tool 注解

```java
@Component
public class OrderTools {

    @Tool(name = "query_order", description = "根据订单号查询订单状态")
    public OrderStatus queryOrder(
            @ToolParam(description = "订单号") String orderId) {
        return orderService.getStatus(orderId);
    }
}
```

LLM 自动在 ReAct 循环中调用工具——不需要手写 Prompt，不需要解析 JSON，注册即用。

### AI 编码助手 — 浏览器端 Cursor 级体验

llama4j 内置了一个完整的浏览器端 AI 编码助手（`llama4j-web` 模块），类似 Cursor/Windsurf 的交互体验：

```
┌───────────────────────────────────────────────────────────────┐
│  Llama4j Agent          [Model: Qwen2.5-7B ▼]       [⚙] [?] │
├────┬───────────┬──────────────────────────┬───────────────────┤
│    │           │                          │                   │
│ A  │  文件树    │     Monaco Editor        │   Agent 对话面板   │
│ c  │  [Files]  │     (多标签 + Diff 视图)   │                   │
│ t  │  [Search] │                          │  🤖 Agent: 我来   │
│ i  │  [Git]    │     语法高亮 + Minimap    │  帮你分析这个文件  │
│ v  │           │                          │  [tool:read_file]  │
│ i  │  ├─src/   │                          │  ✓ 128 lines      │
│ t  │  │ ├─Main │                          │                   │
│ y  │  │ └─App  │                          │  Agent: 这个方法   │
│    │  ├─pom.xml│                          │  有个空指针风险... │
│ B  │  └─README │                          │                   │
│ a  │           ├──────────────────────────┤  [输入...]  [发送] │
│ r  │           │     集成终端              │                   │
│    │           │     (xterm.js + WebSocket)│                   │
│    │           │     bash$ _              │                   │
├────┴───────────┴──────────────────────────┴───────────────────┤
│  Git: main │ Java 17 │ UTF-8 │ Spaces: 4 │ Ln 42             │
└───────────────────────────────────────────────────────────────┘
```

**功能亮点：**
- **9 种 Agent 工具**：read_file / write_file / edit_file / list_files / search_files / find_files / run_command / web_search / web_fetch
- **Monaco Editor**：多标签、语法高亮、Diff 视图、Ctrl+S 保存
- **集成终端**：xterm.js + WebSocket PTY，真实 shell
- **Git 面板**：状态、Diff、提交、分支切换（JGit）
- **流式 Agent 对话**：SSE 实时 token 流、工具调用折叠面板、权限审批弹窗
- **Markdown 渲染**：marked.js + highlight.js + DOMPurify 防 XSS
- **双模运行**：本地 GGUF 模型 或 云端 API，随时切换

```bash
# 启动 Web IDE
java -jar llama4j-web-1.0.0-SNAPSHOT.jar

# 浏览器访问
open http://localhost:8080
```

### OpenAI 兼容 API — 零改造迁移

你的 OpenAI SDK 客户端一行不用改：

```
POST /v1/chat/completions          # 对话补全（同步 + SSE 流式）
POST /v1/chat/completions?stream=true
GET  /v1/models                    # 模型列表
```

### API 安全 — 生产就绪

所有 `/v1/*` 和 `/api/*` 端点均支持 Bearer Token 认证。不配置 API Key 则自动进入开发模式（不过滤）：

```yaml
llama4j:
  api:
    key: ${LLAMA4J_API_KEY}   # 环境变量，生产环境必须设置
```

```bash
# 未配置 key 时 → 不校验（开发模式）
curl http://localhost:8080/v1/models

# 配置 key 后 → 必须携带 Authorization 头
curl -H "Authorization: Bearer $LLAMA4J_API_KEY" http://localhost:8080/v1/models

# WebSocket 终端的 CORS 也可配置
llama4j:
  web:
    cors:
      allowed-origins: https://your-domain.com, http://localhost:8080
```

### 生产级可观测性

8 个 Micrometer 指标自动导出，接入 Prometheus / Grafana 零代码：

```
llama4j.inference.requests     -- 推理请求总数
llama4j.inference.latency      -- 推理延迟分布
llama4j.tokens.prompt          -- Prompt Token 数
llama4j.tokens.completion      -- 补全 Token 数
llama4j.tokens.per.second      -- 生成吞吐量
llama4j.kv.cache.usage         -- KV Cache 利用率
llama4j.queue.depth            -- 请求队列深度
llama4j.inference.errors       -- 推理错误计数
```

---

## 模块一览

| 模块 | 职责 |
|------|------|
| **llama4j-native** | JNI 桥接 llama.cpp。`LlamaContext`、`GrammarConstraint`、`EmbeddingVector`、`MultimodalContext` — 原生资源管理 |
| **llama4j-core** | `ChatService`、`EmbeddingService`、`SessionManager`、`InferenceStats` — 编排层 |
| **llama4j-chat** | 聊天模板引擎，10+ 格式 + Jinja2 解析器 |
| **llama4j-tools** | `@Tool` 注解驱动的函数调用 + ReAct 推理循环 |
| **llama4j-providers** | OpenAI 兼容 API 客户端，支持任何云端大模型 |
| **llama4j-metrics** | Micrometer 集成，8 个核心指标 |
| **llama4j-repository** | ModelScope + HuggingFace 模型下载 + 硬件量化推荐 |
| **llama4j-agent** | Agent 核心：权限管理、上下文加载、系统提示词组装 |
| **llama4j-web** | 浏览器端 AI 编码助手 — Monaco Editor + xterm.js + Agent 聊天 |
| **llama4j-spring-boot-starter** | Spring Boot 自动配置 + OpenAI API 控制器 + Actuator |
| **llama4j-samples** | 示例应用 |

---

## 进阶用法

### JSON Mode / Grammar 约束

```java
// 一行开启 JSON 模式
ChatRequest request = ChatRequest.builder()
    .system("你是数据提取助手")
    .addMessage(Role.USER, "从以下文本提取姓名和年龄：张三今年30岁")
    .jsonMode(true)
    .build();
ChatResponse response = service.chat(request);
// 输出: {"name": "张三", "age": 30}

// 自定义 GBNF 语法约束
try (GrammarConstraint gc = GrammarConstraint.create(ctx, gbnf, "root")) {
    params = GenerateParams.builder("...").grammar(gc).build();
}
```

### Embedding 向量 + 相似度

```java
EmbeddingService embedService = new EmbeddingService(ctx);

EmbeddingVector vec = embedService.embed("机器学习是人工智能的一个分支");
double score = embedService.similarity("猫是宠物", "狗是宠物");  // ~0.85

List<SimilarityResult> top2 = embedService.findMostSimilar("编程",
    List.of("Java是编程语言", "猫是哺乳动物", "Python是脚本语言"), 2);
// → [Java是编程语言 (0.92), Python是脚本语言 (0.88)]
```

### 流式推理 + 工具调用

```java
// 纯流式
service.chatStream(request, new ChatStreamListener() {
    @Override public void onToken(String token) { System.out.print(token); }
    @Override public void onComplete(ChatResponse r) { System.out.println("\nDone!"); }
    @Override public void onError(Throwable e) { log.error("Stream failed", e); }
});

// 流式 + 服务端工具执行
service.chatStreamWithTools(request, new StreamingToolListener() {
    @Override public void onContentToken(String token) { System.out.print(token); }
    @Override public void onToolCall(ToolCall call) { log.info("调用工具: {}", call.toolName()); }
    @Override public void onToolResult(ToolResult result) { log.info("结果: {}", result.content()); }
    @Override public void onComplete(ChatResponse r) { System.out.println("\nDone!"); }
});
```

### 直接 JNI 控制

```java
try (LlamaContext ctx = new LlamaContext("/models/qwen2.5-7b.gguf", ModelParams.DEFAULT)) {
    String response = ctx.generate(GenerateParams.builder("你好！")
        .maxTokens(256).temperature(0.7f).build());

    ctx.generateStream("讲个故事", token -> System.out.print(token));

    int[] tokens = ctx.tokenize("Hello world");
    float[] embedding = ctx.embed("Hello world");

    System.out.println(ctx.getModelDescription());   // "Qwen2 1.5B Q4_K_M"
    System.out.println(ctx.getModelParameterCount()); // 1543714304

    SessionState state = ctx.saveSession();    // 存档 KV Cache
    ctx.loadSession(state);                    // 恢复，无需重新 prompt
}
```

### 模型自动下载 + 硬件感知量化推荐

```java
GgufRepository repo = new GgufRepository();

// 自动下载（国内 ModelScope 优先，HuggingFace 回退）
Path model = repo.resolve("Qwen/Qwen2.5-7B-Instruct-GGUF:Q4_K_M");

// 根据你的 GPU 显存推荐最佳量化级别
String quant = repo.recommendQuantization(7.0);
// 8GB VRAM → Q4_K_M | 12GB → Q5_K_M | 16GB+ → Q8_0
```

---

## 编译原生库

llama4j 通过 JNI 调用 llama.cpp。预编译的原生库已包含在 JAR 中，但你也可以自己编译：

```bash
# macOS (Apple Silicon + Metal)
./scripts/build-native.sh --classifier macos-aarch64 --gpu metal

# Linux (CUDA)
./scripts/build-native.sh --classifier linux-x86_64 --gpu cuda

# Windows (PowerShell)
.\scripts\build-native.ps1 -Classifier windows-x86_64 -Gpu cuda
```

| 平台 | GPU 后端 | 库文件 |
|------|----------|--------|
| macOS (Apple Silicon / Intel) | Metal | `.dylib` |
| Linux (x86_64) | CUDA / Vulkan / CPU | `.so` |
| Windows (x86_64) | CUDA / CPU | `.dll` |

详见 **[BUILD_NATIVE.md](docs/BUILD_NATIVE.md)**。

---

## 构建

```bash
mvn clean install           # 完整构建（所有 12 个模块）
mvn clean install -DskipTests  # 跳过测试
mvn test                    # 运行测试
```

---

## 系统要求

| 组件 | 最低版本 |
|------|----------|
| JDK | 17+ |
| Spring Boot | 3.3+ |
| CMake | 3.14+（编译原生库时） |
| GCC / Clang | 12+ / 15+（编译原生库时） |

---

## License

MIT License — see [LICENSE](LICENSE)
