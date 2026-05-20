# 贡献指南

感谢你对 llama4j 的关注！本文档描述如何参与项目贡献。

## 开发环境

### 前置条件

- JDK 17+
- Maven 3.9+
- Git
- (可选) CUDA Toolkit — 用于 GPU 加速编译
- (可选) Vulkan SDK — 用于跨平台 GPU 加速

### 本地构建

```bash
git clone https://github.com/your-org/llama4j.git
cd llama4j
mvn clean compile
```

### 编译原生库

如需修改 JNI 层代码，请参考 [BUILD_NATIVE.md](docs/BUILD_NATIVE.md) 编译原生动态库。

## 项目结构

```
llama4j/
├── llama4j-native/          # JNI 桥接层 + 原生库
├── llama4j-chat/            # 对话模板引擎
├── llama4j-core/            # 核心服务（ChatService、会话管理）
├── llama4j-tools/           # 函数调用 / 工具集成
├── llama4j-metrics/         # Micrometer 指标收集
├── llama4j-repository/      # 模型仓库（HuggingFace 下载）
├── llama4j-spring-boot-starter/  # Spring Boot Starter
└── llama4j-samples/         # 示例应用
```

## 代码规范

### Java 代码

- 遵循项目现有的代码风格
- 使用 4 空格缩进
- 公开 API 必须有 Javadoc（包含 `@param`、`@return`、`@throws`）
- Javadoc 中章节标题使用 `<h2>` 而非 `<h3>`

### 命名约定

- 类名：PascalCase（如 `ChatService`）
- 方法名：camelCase（如 `generateStream`）
- 常量：UPPER_SNAKE_CASE（如 `MODEL_NOT_FOUND`）
- 包名：全小写（如 `com.llama4j.core`）

### 异常处理

- 继承 `Llama4jException`，提供错误码常量
- 在构造器中设置有意义的错误描述

### 日志

- 使用 SLF4J（`org.slf4j.Logger`）
- 关键操作记录 INFO 级别日志
- 调试信息使用 DEBUG 级别
- 异常使用 ERROR 级别

## 提交规范

### Commit Message

使用简洁的中文或英文描述：

```
feat: 新增 Vulkan GPU 后端支持
fix: 修复流式推理空指针异常
docs: 更新编译文档
refactor: 重构 NativeLoader 平台检测逻辑
test: 添加 ChatTemplateEngine 单元测试
```

### 分支策略

- `main` — 稳定版本
- `dev` — 开发分支
- `feature/xxx` — 功能分支
- `fix/xxx` — 修复分支

## Pull Request 流程

1. Fork 仓库并创建功能分支
2. 编写代码并添加必要的单元测试
3. 确保所有测试通过：`mvn clean test`
4. 确保 Javadoc 编译通过：`mvn javadoc:javadoc`
5. 提交 PR，描述改动内容和原因

### PR 检查清单

- [ ] 代码编译通过（`mvn clean compile`）
- [ ] 单元测试通过（`mvn test`）
- [ ] Javadoc 编译通过（`mvn javadoc:javadoc`）
- [ ] 公开 API 有完整的 Javadoc
- [ ] 不引入新的编译警告

## 报告问题

提交 Issue 时请包含：

- llama4j 版本
- JDK 版本和操作系统
- 问题复现步骤
- 相关日志输出

## 原生库贡献

原生库（llama.cpp 绑定）的修改需要额外注意：

- 修改 `llama4j-native/src/main/c++/llama4j.cpp` 后需要重新编译
- 确保修改兼容 macOS、Linux、Windows 三平台
- 如涉及新 API，需在 `LlamaContext.java` 中添加对应的 Java 声明
- 提交编译后的动态库到对应平台的 resources 目录

详见 [BUILD_NATIVE.md](docs/BUILD_NATIVE.md)。
