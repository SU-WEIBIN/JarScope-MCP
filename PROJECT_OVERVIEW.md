# Java Code Intelligence MCP

## 1. 文档目的

本文档统一承载项目概要、技术决策、架构设计、实施计划和第一阶段验收标准。

项目当前处于设计和实施准备阶段，当前目录原本为空，后续将从零建立 Java 21 Maven 项目。

MCP 工具调用约定见 [MCP_API.md](./MCP_API.md)。

## 2. 项目定位

Java Code Intelligence MCP 面向 Claude Code 等 AI Agent，帮助 AI 理解没有源码的第三方 Java JAR。

目标能力：

- 扫描 Maven 项目的第三方依赖；
- 解析 class、interface、enum、annotation；
- 建立字段、方法、注解、继承和静态调用索引；
- 查询类、方法、字段、子类和调用方；
- 按需反编译 class；
- 持久化索引和反编译缓存；
- 通过 MCP stdio 暴露给 AI Agent。

本项目不是 Java IDE，不负责编辑、编译、重构或运行目标项目。

## 3. 已确认技术边界

| 项目 | 决策 |
|---|---|
| MCP 服务运行时 | JDK 21 |
| 被分析项目默认目标版本 | Java 8 |
| 第一阶段传输 | stdio |
| 第一阶段项目类型 | Maven / 多 Module |
| 字节码解析 | ASM |
| 反编译器 | Vineflower |
| 索引存储 | SQLite |
| 反编译策略 | 按需反编译 + 持久化缓存 |
| JAR 内容哈希 | 默认关闭，可选开启 |

### 3.1 Java 版本

`targetJavaRelease` 表示目标项目的 Java 版本，默认值为 `8`：

```yaml
targetJavaRelease: 8
enforceTargetRelease: false
```

它影响多版本 JAR 选择、反编译时的类路径上下文和兼容性提示。

服务运行在 JDK 21，可以读取 Java 8 字节码。默认不因为发现高版本 class 而停止；高版本 class 会记录实际 class file 版本。开启 `enforceTargetRelease=true` 后，超出目标版本的 class 会被跳过并记录错误。

多版本 JAR 默认选择不高于 `targetJavaRelease` 的版本：

```text
targetJavaRelease=8  -> 基础 class
targetJavaRelease=17 -> META-INF/versions/17 下的 class（如果存在）
```

### 3.2 JAR 变化检测

JAR 内容哈希不是必需能力，默认不计算 SHA-256。

```text
none      默认，不计算哈希；只有 force=true 才重新索引
metadata  使用路径、文件名、大小、修改时间等轻量信息
sha256    计算完整内容哈希
```

默认使用 `changeDetection=none`。依赖发生变化时由用户显式调用：

```json
{
  "projectPath": "E:/work/order-service",
  "force": true
}
```

只有 `changeDetection=sha256` 时才计算和保存 sha256 字段。

## 4. 可行性结论

技术链路可行：

```text
Maven 项目
  -> 依赖 JAR 发现
  -> ASM 字节码解析
  -> SQLite 语义索引
  -> 查询服务
  -> Vineflower 按需反编译
  -> stdio MCP
  -> Claude Code
```

ASM 可以准确提取 class 结构和直接字节码调用指令，但无法仅靠静态 JAR 分析完整还原反射、Spring AOP、动态代理、容器配置和运行时多态分派。因此调用关系必须标注为静态分析结果。

## 5. 开源方案调研结论

| 项目 | 可复用内容 | 定位 |
|---|---|---|
| [MCP Java SDK](https://github.com/modelcontextprotocol/java-sdk) | 官方 Java MCP Server API 和 stdio 传输 | 直接使用 |
| [JacoDB](https://github.com/UnitTestBot/jacodb) | ASM、类层次、注解、方法使用关系和 SQLite 思路 | 参考，不绑定核心模型 |
| [Vineflower](https://github.com/Vineflower/vineflower) | Java 字节码反编译，可作为 Library 调用 | 直接使用 |
| [jarp-mcp](https://github.com/tersePrompts/jarp-mcp) | MCP 反编译工具和缓存交互方式 | 参考 |
| [dejared-mcp](https://github.com/hqkh4nh/dejared-mcp) | JAR 搜索、ASM 元数据、反编译器切换 | 参考 |
| [Maven Resolver](https://github.com/apache/maven-resolver) | Maven artifact 和依赖解析 | 预留适配器 |
| [sqlite-jdbc](https://github.com/xerial/sqlite-jdbc) | Java 访问 SQLite | 直接使用 |
| [ASM](https://asm.ow2.io/) | JVM class file 访问器 API | 直接使用 |

没有发现一个成熟项目同时覆盖 Maven 依赖发现、持久化语义索引、继承和反向调用关系、按需反编译缓存以及面向 AI 的 Java MCP 查询。因此本项目采用组合式实现，而不是整体 fork 某个项目。

## 6. 总体架构

```text
Claude Code
    | stdio MCP
    v
MCP Server
    |
    +-- Project Manager
    |     +-- Maven 项目发现
    |     +-- 变化检测和增量索引
    |
    +-- Bytecode Indexer
    |     +-- 类、接口、枚举、注解
    |     +-- 字段、方法、参数、异常
    |     +-- 继承关系
    |     +-- 静态字节码调用边
    |
    +-- SQLite Repository
    +-- Query Services
    +-- Decompiler Service
          +-- Vineflower
          +-- decompiled/ 缓存
```

建议包结构：

```text
com.example.jci
├── Main.java
├── mcp/
├── project/
├── index/
├── storage/
├── query/
└── decompile/
```

MCP 层不直接读 class，ASM 层不负责 SQL，反编译层不负责查询协议。

## 7. 缓存目录

默认缓存根目录为用户 home 下的 `.java-code-intelligence`：

```text
.java-code-intelligence/
└── <project-id>/
    ├── index.db
    ├── jars/
    └── decompiled/
        └── com/example/OrderDTO.java
```

项目 ID 由规范化项目路径生成，不使用 JAR 内容哈希作为项目 ID。

JAR 可以直接引用 Maven 原路径，也可以配置为复制到 `jars/`；是否复制与变化检测策略独立。

## 8. SQLite 数据模型

第一阶段表：

```text
projects
jars
classes
class_interfaces
annotations
fields
methods
method_parameters
method_exceptions
calls
decompile_cache
index_errors
schema_version
```

```text
jars:
  id, group_id, artifact_id, version, classifier
  path, file_name, file_size, last_modified
  sha256 nullable, change_detection_mode, index_status

classes:
  id, jar_id, internal_name, fully_qualified_name
  package_name, kind, access_flags, super_name
  signature, source_file, class_file_major_version

methods:
  id, class_id, name, descriptor
  return_type, generic_signature, access_flags

calls:
  caller_method_id, target_owner, target_name
  target_descriptor, opcode, resolution_kind
```

方法通过 `name + descriptor` 唯一定位，用于区分重载。

`resolution_kind`：

```text
DIRECT
POLYMORPHIC_CANDIDATE
UNRESOLVED
```

## 9. 索引流程

### 9.1 首次打开

```text
接收 projectPath
  -> 定位根 pom.xml
  -> 发现 Maven Module
  -> 获取 runtime 依赖 JAR
  -> 根据 changeDetection 判断是否扫描
  -> ASM 解析 class
  -> 事务写入 SQLite
  -> 返回索引统计
```

### 9.2 后续打开

`changeDetection=none` 时：

```text
打开项目
  -> 读取 index.db
  -> 不重新扫描 JAR
  -> 直接允许查询
```

依赖变化后使用 `force=true` 主动重建。单个损坏 class、无法识别的 class 或反编译失败不应中断整个项目索引，错误写入 `index_errors` 后继续处理。

## 10. MCP 工具

第一阶段工具：

```text
index_project
get_index_status
search_classes
get_class
get_method
get_field
find_subtypes
find_callers
decompile_class
```

```json
{
  "projectPath": "E:/work/order-service",
  "force": false,
  "changeDetection": "none",
  "targetJavaRelease": 8,
  "includeTestDependencies": false
}
```

方法查询支持可选 descriptor；不提供 descriptor 时返回全部重载。

所有工具返回结构化 JSON 和面向 AI 的文本摘要。stdio 模式下 stdout 只能输出 MCP 协议，日志全部写入 stderr。

## 11. 反编译流程

```text
查询 class
  -> 定位 class 和 JAR
  -> 检查 decompile_cache
  -> 命中则直接返回
  -> Vineflower 生成源码
  -> 临时文件写入
  -> 原子移动到 decompiled/
  -> 更新 decompile_cache
  -> 返回源码
```

缓存键：

```text
jar_id
class_entry
targetJavaRelease
decompiler_version
```

返回必须注明：

```text
这是由字节码反编译得到的近似源码，不保证与原始源码完全一致。
```

## 12. 编码阶段计划

### 阶段 0：技术验证和工程初始化

- Maven 项目骨架；
- JDK 21 构建配置；
- 最小 stdio MCP Server；
- ASM、SQLite JDBC、Vineflower 集成验证；
- Java 8 fixture JAR。

### 阶段 1：SQLite Schema 和 Repository

- schema 初始化和版本迁移；
- 项目、JAR、类、方法、字段、调用关系表；
- 事务写入和查询；
- WAL 和项目写锁。

### 阶段 2：Maven 依赖发现

- 根 POM 定位；
- 多 Module 识别；
- runtime 依赖发现；
- 本地 Maven 环境和自定义 settings 支持；
- `DependencyProvider` 接口。

第一版优先复用本地 Maven 的依赖解析行为，避免自行重写 profile、父 POM 和依赖冲突规则。后续再接入 Maven Resolver 实现独立解析。

### 阶段 3：ASM 索引器

- 类、接口、枚举、注解；
- 字段、方法、参数、异常、注解；
- 父类和接口；
- 直接字节码调用边；
- class file 版本；
- 单个 class 错误隔离。

### 阶段 4：变化策略和增量索引

```java
enum ChangeDetectionMode {
    NONE,
    METADATA,
    SHA256
}
```

```text
NONE     只有 force=true 才重建
METADATA 文件大小、修改时间或路径变化时重建
SHA256   内容变化时重建
```

### 阶段 5：查询服务

- 类查询；
- 方法查询；
- 字段查询；
- 子类查询；
- 反向调用方查询；
- 简单类名歧义处理；
- 查询限制和分页。

### 阶段 6：按需反编译缓存

- Vineflower Library Adapter；
- 类级反编译；
- 原子缓存写入；
- 缓存命中；
- 错误和超时处理；
- JAR 或目标 Java 版本变化后的缓存失效。

### 阶段 7：MCP 工具接入

- tools/list；
- 9 个第一阶段工具；
- 输入 schema；
- 结构化输出；
- MCP 错误映射；
- stdout/stderr 隔离。

### 阶段 8：打包、测试和文档

- 可执行 fat JAR；
- Windows PowerShell、Linux/macOS 启动脚本；
- Claude Code 配置示例；
- 集成测试；
- 性能基线；
- 第三方许可证清单。

## 13. 测试计划

### 单元测试

- ASM 类解析；
- 字段、方法和 descriptor；
- 注解值；
- 继承关系；
- 直接调用关系；
- 三种变化检测策略；
- SQLite Repository；
- 反编译缓存命中。

### 集成测试

构造包含以下类型的 fixture JAR：

```text
BaseOrder
OrderDTO extends BaseOrder
PaymentService
PaymentRepository
UserEntity
```

验证类查询、方法重载、字段注解、子类查询、直接调用方、反编译、缓存命中、force 重建以及 Java 8 和其他目标版本切换。

### MCP 测试

验证 initialize、tools/list、tools/call、错误参数、项目不存在、类不存在、stdout 协议纯净性和单进程多次调用。

## 14. 第一阶段验收标准

### 功能

1. 能扫描 Maven 多 Module 项目。
2. 能发现 runtime 依赖 JAR。
3. 能保存类、接口、枚举、字段、方法和注解。
4. 能查询父类、实现接口和所有子类。
5. 能查询直接字节码调用方。
6. 能区分方法重载。
7. 能按需反编译指定类并持久化复用。
8. 默认不计算 JAR SHA-256。
9. `metadata` 和 `sha256` 检测模式可选启用。
10. `force=true` 可以强制重建。
11. 默认目标 Java 版本为 8，并支持切换到 11、17、21 等版本。
12. Claude Code 可以通过 stdio 调用 MCP 工具。

### 正确性

- fixture JAR 的类、字段和方法数量与预期一致；
- descriptor 正确区分重载；
- 所有直接字节码调用指令被记录；
- 反射和动态代理不被伪装成确定调用；
- 单个损坏 class 不阻塞整个 JAR。

### 性能目标

在记录硬件和操作系统的参考开发机上：

- 约 400MB 依赖 JAR 首次索引不超过 3 分钟；
- 已有索引重新启动不超过 3 秒；
- 普通类查询 p95 不超过 500ms；
- 已缓存反编译查询 p95 不超过 100ms；
- 单个 JAR 变化不触发无关 JAR 的全量重建。

### 稳定性

- Maven 解析失败返回具体原因；
- JAR 损坏写入错误记录；
- 反编译失败不损坏已有缓存；
- 重启后索引和缓存仍可用；
- 读查询不会修改索引数据，只有反编译缓存可以产生写入。

## 15. 第一阶段不实现

- Java 源码 AST、JDT、IDE、代码编辑和自动重构；
- 编译和运行目标项目；
- 完整 Spring 运行时解析；
- 反射和动态代理的完整调用还原；
- HTTP/SSE MCP 服务；
- 多进程同时写同一个项目索引。

## 16. 风险与应对

### 调用图不完整

标注 `DIRECT`、`POLYMORPHIC_CANDIDATE` 和 `UNRESOLVED`，不宣称等同于运行时调用图。

### Maven 项目差异

第一版复用本地 Maven 行为，保留 `DependencyProvider` 抽象，后续再增加独立 Maven Resolver。

### 反编译结果不等于原始源码

始终返回反编译免责声明，并保留 class、JAR 和方法元数据作为更可靠的信息源。

### SQLite 并发写入

一个项目一个写锁，启用 WAL，第一阶段限定一个 MCP 进程写入项目索引。

## 17. 预计工作量

单人、具备 Java/Maven/ASM 经验时：

```text
技术验证和工程初始化     1 天
SQLite Schema             2 天
Maven 依赖发现            3 天
ASM 索引器                3 天
变化策略和增量索引        2 天
查询服务                  2 天
反编译缓存                2 天
MCP 工具                  2 天
测试、打包和文档          3 天

预计合计                  2～3 周
```

复杂 Maven profile、多个反编译器、Spring 路由识别或高精度多态调用分析会额外增加工作量。

## 18. 第一阶段完成定义

只有同时满足以下条件，第一阶段才算完成：

- MCP Server 能以 JDK 21 启动；
- Java 8 fixture 项目能够被索引；
- SQLite 索引可以跨进程重启复用；
- 默认不执行 SHA-256 JAR 扫描；
- 可选变化检测策略和 force 重建有效；
- 类、方法、字段、继承和直接调用查询全部可用；
- 指定类能够按需反编译并缓存；
- stdio MCP 工具可被 Claude Code 调用；
- 自动化测试通过；
- 文档和启动示例完整。
