# llama4j

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![JDK](https://img.shields.io/badge/JDK-17+-green.svg)]()
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3+-6db33f.svg)]()
[![llama.cpp](https://img.shields.io/badge/llama.cpp-latest-orange.svg)]()

> **Production-grade Java bindings for llama.cpp** -- Spring Boot native LLM inference framework

**llama4j** brings large language models into the Java ecosystem with zero friction. It wraps [llama.cpp](https://github.com/ggerganov/llama.cpp) via JNI, delivering a Spring Boot-native experience with OpenAI-compatible APIs, automatic chat template detection, function calling, and production-ready observability.

**Stop stitching together Python microservices. Run LLM inference where your Java code lives.**

---

## Why llama4j?

### The Problem

Deploying LLMs in Java enterprises today is painful:

```
Traditional Approach                          llama4j
─────────────────────────────────────────     ─────────────────────────────────
[Java App] ──HTTP──▶ [Python Server]          [Java App + llama4j]
              │        │                      │
              │     [FastAPI]                  │  Direct JNI call
              │     [vLLM/ollama]              │  Zero network hop
              │     [CUDA Runtime]             │  Single process
              │        │                      │
              └────────┘                      └──▶ [llama.cpp + GPU]
                                                  ↑
Extra: Python env, Docker, service discovery,     One JAR. One process.
load balancing, health checks ×2, latency tax.    Zero DevOps overhead.
```

### Head-to-Head Comparison

| Dimension | Traditional (Python sidecar) | Ollama | vLLM | **llama4j** |
|-----------|------------------------------|--------|------|-------------|
| **Language** | Python + HTTP bridge | Go (custom API) | Python + CUDA | **Java native JNI** |
| **Deployment** | 2 services + Docker Compose | Standalone binary | Docker + GPU image | **Single Spring Boot JAR** |
| **Network hop** | HTTP (1-5ms overhead) | HTTP | HTTP | **In-process (0ms)** |
| **Serialization** | JSON round-trip | JSON | JSON | **Direct object pass** |
| **Spring Boot integration** | Manual REST client | Manual REST client | Manual REST client | **Auto-config, Actuator, DI** |
| **OpenAI API** | You build it | Built-in (different format) | Built-in | **Drop-in compatible** |
| **Function calling** | DIY prompt engineering | Limited | Limited | **@Tool annotation, ReAct loop** |
| **Observability** | Separate metrics per service | Basic | Prometheus | **Micrometer, 8 metrics, auto-export** |
| **Session/KV cache** | Stateless per request | Basic | PagedAttention | **Checkpoint/restore, session affinity** |
| **Chat templates** | Hardcoded per model | Auto-detect | Auto-detect | **10+ formats + Jinja2 parser** |
| **Cold start** | Python init + model load | Fast | Slow (compile kernels) | **Fast (precompiled JNI)** |
| **Memory overhead** | Python runtime (~200MB) | ~50MB | ~500MB+ | **~20MB (JVM only)** |
| **DevOps complexity** | High (2 stacks) | Low (but external) | Medium | **Zero (embedded)** |

### When to Choose llama4j

- **You're a Java/Spring shop** and don't want to maintain a Python stack
- **You need sub-millisecond inference latency** without network hops
- **You want LLM inference as a library**, not as a service
- **You need production observability** integrated with your existing Micrometer/Prometheus/Grafana stack
- **You want function calling** that feels native to Java (annotations, not prompt hacking)

---

## Architecture

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
│  │   embed() / GrammarConstraint / EmbeddingVector                       │  │
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
│  ┌──────────────────────────┐  ┌─────────────────────────────────────────┐  │
│  │   HuggingFace Hub        │  │  Hardware-aware Quantization Advisor    │  │
│  │   Model ID → GGUF DL    │  │  VRAM detection → Q4/Q5/Q8 recommend   │  │
│  └──────────────────────────┘  └─────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────────────────────┘
```

**Request flow:** HTTP request → Spring MVC → `ChatService` → chat template render → `LlamaContext.generate()` → JNI → llama.cpp sampler → token stream → SSE response

---

## Quick Start

### 1. Add Dependency

```xml
<dependency>
    <groupId>com.llama4j</groupId>
    <artifactId>llama4j-spring-boot-starter</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### 2. Configure Model

```yaml
llama4j:
  model:
    path: /models/qwen2.5-7b-q4_k_m.gguf
    # Or auto-download from HuggingFace:
    # id: unsloth/Qwen2.5-7B-Instruct:Q4_K_M
    n-ctx: 4096
    n-gpu-layers: -1    # offload all layers to GPU
    n-threads: 8
```

### 3. Run

```bash
mvn spring-boot:run
```

### 4. Call the API

```bash
curl -X POST http://localhost:8080/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{
    "messages": [
      {"role": "system", "content": "You are a helpful assistant."},
      {"role": "user", "content": "Explain quantum computing in one paragraph."}
    ],
    "temperature": 0.7
  }'
```

That's it. No Python. No Docker. No sidecar. Just Java.

---

## Core Features

### OpenAI-Compatible Drop-In API

Your existing OpenAI SDK clients work unchanged:

```java
// Works with openai-python, openai-node, curl, Postman, anything
POST /v1/chat/completions
POST /v1/chat/completions  (stream: true → SSE)
GET  /v1/models
```

### Function Calling with @Tool

```java
@Component
public class WeatherTools {

    @Tool(name = "get_weather", description = "Get current weather for a city")
    public WeatherReport getWeather(
            @ToolParam(description = "City name, e.g. Beijing") String city,
            @ToolParam(description = "Temperature unit") String unit) {
        return weatherService.fetch(city, unit);
    }
}

// LLM automatically invokes tools in a ReAct loop -- no prompt engineering needed
```

### Grammar-Constrained Generation & JSON Mode

Force structured output with GBNF grammar constraints:

```java
// One-flag JSON mode
ChatRequest.builder().jsonMode(true).addMessage(Role.USER, "...").build();

// Custom grammar with AutoCloseable lifecycle
try (GrammarConstraint gc = GrammarConstraint.create(ctx, gbnf, "root")) {
    params = GenerateParams.builder("...").grammar(gc).build();
}
```

### Embedding Vectors & Similarity Search

```java
EmbeddingService service = new EmbeddingService(ctx);
EmbeddingVector vec = service.embed("text");
double score = service.similarity("cat", "dog");
List<SimilarityResult> topK = service.findMostSimilar("query", candidates, 5);
```

### 10+ Chat Template Formats

Auto-detected from GGUF metadata -- zero configuration:

| Format | Models |
|--------|--------|
| **Llama 3** | Meta Llama 3 / 3.1 / 3.2 |
| **ChatML** | Qwen 2.5, Yi, DeepSeek V2 |
| **Gemma** | Google Gemma 2 |
| **Phi-3** | Microsoft Phi-3 / 3.5 |
| **Mistral** | Mistral, Mixtral |
| **DeepSeek** | DeepSeek Coder/V2/V3 |
| **Vicuna** | Vicuna, LongChat |
| **Alpaca** | Alpaca, OpenAssistant |
| **Yi** | Yi-34B, Yi-1.5 |
| **Jinja2** | Any GGUF with embedded Jinja2 template |

Falls back to built-in Jinja2 subset parser when no hardcoded format matches.

### Production Observability

8 metrics exported automatically via Micrometer:

```
llama4j.inference.requests     (Counter)   -- total inference requests
llama4j.inference.latency      (Timer)     -- inference latency distribution
llama4j.tokens.prompt          (Summary)   -- prompt token count
llama4j.tokens.completion      (Summary)   -- completion token count
llama4j.tokens.per.second      (Gauge)     -- generation throughput
llama4j.kv.cache.usage         (Gauge)     -- KV cache utilization
llama4j.queue.depth            (Gauge)     -- request queue depth
llama4j.inference.errors       (Counter)   -- inference error count
```

Plug into Prometheus, Datadog, or Grafana with zero code changes.

### Session Management & KV Cache

```java
SessionManager manager = new SessionManager(new InMemorySessionStore());
Session session = manager.createSession("qwen2.5-7b");

// Multi-turn conversation with KV cache checkpoint
manager.checkpoint(session.id(), context);

// Resume later -- KV cache restored, no re-prompting
Session restored = manager.resumeSession(session.id(), context);
```

### Hardware-Aware Model Selection

```java
GgufRepository repo = new GgufRepository();

// Auto-download from HuggingFace
Path model = repo.resolve("unsloth/Qwen2.5-7B-Instruct:Q4_K_M");

// Smart quantization recommendation based on available VRAM
String quant = repo.recommendQuantization(7.0); // 7B model
// Returns: "Q4_K_M" for 8GB VRAM, "Q5_K_M" for 12GB, "Q8_0" for 16GB+
```

---

## Module Reference

| Module | Purpose |
|--------|---------|
| **llama4j-native** | JNI bridge to llama.cpp. `LlamaContext`, `GrammarConstraint`, `EmbeddingVector`, `GenerateParams` -- native resource management |
| **llama4j-core** | `ChatService`, `EmbeddingService`, `SessionManager`, `InferenceStats`, `ChatTemplateUtil` -- the orchestration layer |
| **llama4j-chat** | Chat template engine with 10+ formats and Jinja2 parser |
| **llama4j-tools** | `@Tool` annotation-driven function calling with ReAct loop |
| **llama4j-metrics** | Micrometer integration with 8 core metrics |
| **llama4j-repository** | HuggingFace Hub integration + hardware-aware quantization advisor |
| **llama4j-spring-boot-starter** | Auto-configuration, OpenAI API controller, Actuator endpoints |
| **llama4j-samples** | Example applications |

---

## Advanced Usage

### JSON Mode / Grammar Constraints

Force the model to output valid JSON (or any GBNF grammar):

```java
// Simple JSON mode — one flag
ChatRequest request = ChatRequest.builder()
    .system("You are a data extraction assistant.")
    .addMessage(Role.USER, "Extract name and age from: John is 30 years old")
    .jsonMode(true)
    .build();
ChatResponse response = service.chat(request);
// Output: {"name": "John", "age": 30}

// Custom grammar with lifecycle management
try (GrammarConstraint gc = GrammarConstraint.json(ctx)) {
    GenerateParams params = GenerateParams.builder("Generate a JSON array of colors")
        .grammar(gc)
        .maxTokens(256)
        .build();
    String result = ctx.generate(params);
}  // gc.close() called automatically

// Custom GBNF grammar (e.g., only output specific values)
try (GrammarConstraint gc = GrammarConstraint.create(ctx, myGbnf, "root")) {
    // ...
}
```

### Embedding Vectors & Similarity

```java
try (LlamaContext ctx = new LlamaContext(modelPath, ModelParams.DEFAULT)) {
    EmbeddingService embedService = new EmbeddingService(ctx);

    // Single text embedding
    EmbeddingVector vec = embedService.embed("机器学习是人工智能的一个分支");

    // Similarity between two texts
    double score = embedService.similarity("猫是宠物", "狗是宠物");  // ~0.85

    // Find most similar documents
    List<String> docs = List.of("Java是编程语言", "猫是哺乳动物", "Python是脚本语言");
    List<SimilarityResult> top2 = embedService.findMostSimilar("编程", docs, 2);
    // → [SimilarityResult("Java是编程语言", 0.92), SimilarityResult("Python是脚本语言", 0.88)]

    // Direct vector operations
    EmbeddingVector v1 = embedService.embed("hello");
    EmbeddingVector v2 = embedService.embed("world");
    double cosine = v1.cosineSimilarity(v2);
    double dist = v1.euclideanDistance(v2);
}
```

### Chat Template Utility

```java
// Render messages to prompt string using model's embedded template
List<Message> messages = List.of(
    Message.system("You are helpful."),
    Message.user("Hello!")
);
String prompt = ChatTemplateUtil.applyTemplate(ctx, messages, true);

// Or use ChatService's public renderPrompt
String prompt = chatService.renderPrompt(messages);
```

### Streaming with SSE

```java
service.chatStream(request, new ChatStreamListener() {
    @Override public void onToken(String token) { System.out.print(token); }
    @Override public void onComplete(ChatResponse r) { System.out.println("\nDone!"); }
    @Override public void onError(Throwable e) { log.error("Stream failed", e); }
});
```

### Direct JNI Control

```java
try (LlamaContext ctx = new LlamaContext("/models/qwen2.5-7b.gguf", ModelParams.DEFAULT)) {
    // Sync generation
    String response = ctx.generate(GenerateParams.builder("Hello!")
        .maxTokens(256).temperature(0.7f).build());

    // Streaming
    ctx.generateStream("Tell me a story", token -> System.out.print(token));

    // JSON mode via GenerateParams
    String json = ctx.generate(GenerateParams.builder("Generate a user profile")
        .jsonMode(true).maxTokens(256).build());

    // Tokenization
    int[] tokens = ctx.tokenize("Hello world");

    // Embeddings
    float[] embedding = ctx.embed("Hello world");

    // Model metadata
    System.out.println(ctx.getModelDescription());  // "Qwen2 1.5B Q4_K_M"
    System.out.println(ctx.getModelSize());          // 1117320736
    System.out.println(ctx.getModelParameterCount()); // 1543714304

    // KV cache save/restore
    SessionState state = ctx.saveSession();
    ctx.loadSession(state);
}
```

### Stream via curl

```bash
curl -N http://localhost:8080/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{"messages":[{"role":"user","content":"Write a haiku"}],"stream":true}'
```

---

## Building Native Libraries

llama4j calls llama.cpp through JNI. Pre-compiled native libraries are included, but you can rebuild:

```bash
# macOS (Apple Silicon + Metal)
./scripts/build-native.sh --classifier macos-aarch64 --gpu metal

# Linux (CUDA)
./scripts/build-native.sh --classifier linux-x86_64 --gpu cuda

# Windows (PowerShell)
.\scripts\build-native.ps1 -Classifier windows-x86_64 -Gpu cuda
```

| Platform | GPU Backend | Library |
|----------|-------------|---------|
| macOS (Apple Silicon / Intel) | Metal | `.dylib` |
| Linux (x86_64) | CUDA / Vulkan / CPU | `.so` |
| Windows (x86_64) | CUDA / CPU | `.dll` |

See **[BUILD_NATIVE.md](docs/BUILD_NATIVE.md)** for detailed instructions and troubleshooting.

---

## Build

```bash
# Full build (all modules + native)
mvn clean install

# Java only (skip native compilation)
mvn clean install -DskipNativeBuild=true

# Run tests
mvn test
```

---

## Requirements

| Component | Minimum |
|-----------|---------|
| JDK | 17+ |
| llama.cpp | latest release |
| CMake | 3.14+ |
| GCC / Clang | 12+ / 15+ |
| Spring Boot | 3.3+ |

---

## License

MIT License -- see [LICENSE](LICENSE)
