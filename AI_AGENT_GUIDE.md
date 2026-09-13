# Java Code Intelligence MCP：AI Agent 配置与调用指南

本文档面向能够调用 MCP `tools/list` / `tools/call` 的 AI Agent。阅读后，Agent 应该能够：

1. 启动并配置 Java Code Intelligence MCP；
2. 判断何时建立或刷新项目索引；
3. 正确调用当前版本暴露的 11 个 tools；
4. 解释分页、缓存、Maven 诊断和反编译结果。

请求/响应字段的完整协议说明见 [MCP_API.md](MCP_API.md)；本文档优先说明 Agent 何时调用、如何配置以及当前实现的行为边界。

本文中的 `/path/...` 均为脱敏的虚拟路径，实际部署时替换为本机路径。

## 0. 给 AI Agent 的执行规则

- MCP 服务运行在 JDK 21；被分析项目默认按 Java 8 目标版本处理。
- `projectPath` 必须使用被分析 Maven 项目的绝对路径。通常传项目根目录，不要传 `.java-code-intelligence` 缓存目录。
- 第一次查询前先调用 `get_index_status`；没有可用索引时调用 `index_project`。
- 不要要求用户先执行目标项目的 `mvn compile`、`mvn package` 或 `mvn install`。索引阶段只读取 Maven 模型并解析依赖 classpath。
- 当前 MCP schema 设置了 `additionalProperties=false`。只发送本文档列出的参数；不要自行添加 `packagePrefix`、`kind` 等未在当前 schema 中声明的字段。
- 默认不要使用 `force=true`。只有确认依赖或索引内容发生变化时才强制刷新。
- 查询结果包含 `page.truncated=true` 时，使用 `nextOffset` 继续分页，不要重复请求同一页。
- 反编译结果是字节码还原的近似源码，只能作为理解依据，不可当作原始源码或可直接编译的补丁。
- 当用户询问工作区 `.java` 源码时，优先直接读取工作区源码；本 MCP 第一阶段主要索引第三方 JAR，不建立完整 Java 源码 AST。

运行时如果 `tools/list` 与本文档有差异，以服务实际返回的 `tools/list` schema 为准。

## 1. 启动 MCP 服务

### 1.1 Claude Code 配置

```json
{
  "mcpServers": {
    "java-code-intelligence": {
      "command": "java",
      "args": [
        "-Djci.maven.command=/path/maven/bin/mvn.cmd",
        "-Djci.maven.timeoutSeconds=180",
        "-jar",
        "/path/tools/java-code-intelligence-mcp-0.1.0-SNAPSHOT.jar"
      ],
      "env": {
        "JAVA_HOME": "/path/jdk-21",
        "MAVEN_HOME": "/path/maven"
      }
    }
  }
}
```

`JAVA_HOME`、JAR 路径和 Maven 路径替换为实际安装位置。Java 系统属性必须放在 `-jar` 之前。

如果机器上同时存在多个 JDK，建议把 `command` 直接写成 JDK 21 的绝对 `java` 路径（Windows 通常是 `.../bin/java.exe`）；仅设置 `JAVA_HOME` 不一定会改变宿主进程通过 `PATH` 找到的 `java`。

### 1.2 Maven 服务级配置

这些配置不属于某一次 tool 调用，而属于 MCP 进程启动配置：

| 配置 | Java 系统属性 | 环境变量 | 默认值 | 说明 |
|---|---|---|---|---|
| Maven 可执行文件 | `jci.maven.command` | `JCI_MAVEN_COMMAND` | Windows `mvn.cmd`；其他系统 `mvn` | 指定完整可执行文件路径时最稳定 |
| Maven 安装目录 | `maven.home` | `MAVEN_HOME` | 不设置 | 未指定可执行文件时使用 `<MAVEN_HOME>/bin/mvn[.cmd]` |
| Maven 超时 | `jci.maven.timeoutSeconds` | `JCI_MAVEN_TIMEOUT_SECONDS` | `120` | 必须是正整数，每个 Maven 调用单独计时 |

优先级：

```text
系统属性 > 环境变量 > 默认值
```

Maven 会继续使用自己的标准用户配置、本地仓库、镜像和认证配置。服务不会把目标项目目录下名为 `settings.xml` 的普通文件自动当成 Maven settings；如需指定 settings，应通过 Maven 自身的启动环境或可执行文件封装配置。

当前没有 `mavenSettingsPath` tool 参数。若必须固定某个 settings 文件，可把 `JCI_MAVEN_COMMAND` / `jci.maven.command` 指向一个包装脚本，由脚本调用 Maven 并追加 `-s <settings.xml>`；不要把该路径作为 `index_project` 的未知字段发送。

### 1.3 MCP 进程约束

- 服务输出 MCP 协议到 stdout，启动日志写入 stderr。
- 服务端名称为 `java-code-intelligence-mcp`，版本为 `0.1.0`。
- 传输方式为 stdio；客户端负责发送 MCP `initialize`、`tools/list` 和 `tools/call`。
- 不要同时启动多个进程写入同一个项目的索引数据库。

## 2. 项目级索引参数

这些字段主要通过 `index_project` 传入；`decompile_class` 另外允许用 `targetJavaRelease` 选择反编译目标版本，但它不会重新建立索引。MCP schema 未声明的字段不能发送。

| 参数 | 类型 | 默认值 | 适用 tool | 行为 |
|---|---|---:|---|---|
| `projectPath` | string | 必填 | 全部 tools | Maven 项目根目录绝对路径 |
| `targetJavaRelease` | integer | `8` | `index_project`、`decompile_class` | 目标 Java 版本；当前运行时不得高于服务 JDK 版本 |
| `enforceTargetRelease` | boolean | `false` | `index_project` | 是否跳过高于目标版本的 class |
| `changeDetection` | enum | `NONE` | `index_project` | 规范值为 `NONE` / `METADATA` / `SHA256`；服务内部兼容大小写 |
| `includeTestDependencies` | boolean | `false` | `index_project` | 是否将 Maven `test` scope 依赖加入 JAR 集合 |
| `force` | boolean | `false` | `index_project`、`decompile_class` | 忽略对应缓存并重建 |

推荐的 Java 8 二开项目配置：

```json
{
  "projectPath": "/path/projects/sample-project",
  "targetJavaRelease": 8,
  "enforceTargetRelease": false,
  "changeDetection": "NONE",
  "includeTestDependencies": false,
  "force": false
}
```

参数选择建议：

- 目标项目使用 Java 8：保持 `targetJavaRelease=8`。
- 项目使用 Java 17 或 21：把 `targetJavaRelease` 改成对应版本。
- 只希望理解运行时第三方代码：保持 `includeTestDependencies=false`。
- 需要测试工具或测试 DTO：设置 `includeTestDependencies=true`，但 JAR 数量会增加。
- 默认 `changeDetection=NONE` 不读取完整 JAR 内容计算哈希；依赖变更后用 `force=true`。
- 只有明确需要内容指纹时才使用 `SHA256`；`METADATA` 只比较文件大小和修改时间。

### 2.1 无构建索引流程

`index_project` 对 Maven 项目执行以下逻辑：

```text
root pom.xml
  -> 递归发现 Reactor Module
  -> help:effective-pom（读取有效模型）
  -> 区分 workspace 内部依赖和外部依赖
  -> 为每个 Module 生成系统临时 probe POM
  -> dependency:build-classpath
  -> 去重第三方 JAR
  -> ASM 扫描并写入 SQLite
```

目标项目的内部 Module 不需要已经有 `target/*.jar`，也不需要安装到本地 Maven 仓库。服务不会对目标项目执行 `compile`、`package`、`install` 或测试生命周期。Maven 插件、外部仓库或认证不可用时，结果会带有 `MAVEN_*` 诊断。

## 3. 当前暴露的 11 个 tools

| tool | 用途 | 是否建立/修改索引 |
|---|---|---|
| `index_project` | 扫描 Maven 依赖并建立或更新 SQLite 索引 | 是 |
| `get_index_status` | 获取索引统计 | 否（会打开缓存数据库） |
| `search_classes` | 按类名或全限定名搜索类 | 否 |
| `get_class` | 获取类结构、字段和方法 | 否 |
| `get_method` | 查询方法签名，可附带调用方/被调用方 | 否 |
| `get_method_overview` | 返回有界的调用统计、类别和关键证据节点 | 否 |
| `get_field` | 查询字段、注解和 Getter/Setter | 否 |
| `find_subtypes` | 查询继承和接口实现关系 | 否 |
| `find_callers` | 查询静态字节码调用方 | 否 |
| `find_callees` | 按页、类别或包名展开被调用目标 | 否 |
| `decompile_class` | 按需反编译 class 并缓存源码 | 可能写入反编译缓存 |

所有 tool 都通过 `projectPath` 选择项目。除表中列出的参数外不要传其他字段。

### 3.1 `index_project`

建立或更新项目的 Maven 第三方 JAR 索引。

输入：

| 参数 | 必填 | 默认值 | 说明 |
|---|---:|---:|---|
| `projectPath` | 是 | - | 项目根目录 |
| `targetJavaRelease` | 否 | `8` | 目标 Java 版本 |
| `enforceTargetRelease` | 否 | `false` | 是否跳过更高版本 class |
| `changeDetection` | 否 | `NONE` | 规范值 `NONE`、`METADATA`、`SHA256`；服务内部兼容大小写 |
| `includeTestDependencies` | 否 | `false` | 是否纳入 test scope JAR |
| `force` | 否 | `false` | 是否忽略已有 JAR 索引 |

调用示例：

```json
{
  "name": "index_project",
  "arguments": {
    "projectPath": "/path/projects/sample-project",
    "targetJavaRelease": 8,
    "changeDetection": "NONE",
    "includeTestDependencies": false,
    "force": false
  }
}
```

结构化结果的核心字段：

```json
{
  "summary": {
    "modules": 5,
    "jarsDiscovered": 828,
    "jarsIndexed": 828,
    "jarsReused": 0,
    "classes": 0,
    "methods": 0,
    "fields": 0,
    "calls": 0,
    "errors": 0,
    "elapsedMs": 12000,
    "diagnostics": []
  },
  "diagnostics": []
}
```

实际 class、method、field、call 数量取决于 JAR 内容。`diagnostics` 同时位于 `summary` 和结果顶层，内容相同。

### 3.2 `get_index_status`

查看已有索引是否可用。没有建立索引时，统计通常为 0。

输入：

```json
{
  "name": "get_index_status",
  "arguments": {
    "projectPath": "/path/projects/sample-project"
  }
}
```

返回：`{"summary": IndexSummary}`。如果需要建立索引，调用 `index_project`；不要用 `get_index_status` 代替索引。

### 3.3 `search_classes`

按简单类名、全限定类名或关键词搜索已索引的第三方类。

| 参数 | 必填 | 默认值 | 说明 |
|---|---:|---:|---|
| `projectPath` | 是 | - | 项目路径 |
| `query` | 是 | - | 例如 `OrderDTO` 或 `com.example.order.OrderDTO` |
| `limit` | 否 | `50` | 单页数量，允许 `0` 到 `500`；传 `0` 时按 50 处理 |
| `offset` | 否 | `0` | 分页偏移，不能为负数 |

返回：

```json
{
  "items": [
    {
      "name": "com.example.order.OrderDTO",
      "kind": "class",
      "jar": {
        "path": "/path/.m2/repository/com/example/order-api/1.1.0/order-api-1.1.0.jar",
        "coordinate": {
          "groupId": "com.example",
          "artifactId": "order-api",
          "version": "1.1.0",
          "classifier": null
        }
      }
    }
  ],
  "page": {
    "limit": 50,
    "offset": 0,
    "returned": 1,
    "total": 1,
    "truncated": false,
    "nextOffset": null
  }
}
```

如果 `query` 恰好是全限定类名，服务优先返回精确匹配。

### 3.4 `get_class`

查看完整类结构。

| 参数 | 必填 | 默认值 | 说明 |
|---|---:|---:|---|
| `projectPath` | 是 | - | 项目路径 |
| `className` | 是 | - | 全限定类名 |
| `includeMembers` | 否 | `true` | 是否返回 `fields` 和 `methods` |
| `includeAnnotations` | 否 | - | 当前 `ClassInfo` 始终携带类注解；该字段为兼容保留，不改变当前返回结果 |

返回至少包含 `class`；`includeMembers=true` 时还包含字段和方法列表：

```json
{
  "class": {
    "name": "com.example.order.OrderDTO",
    "kind": "class",
    "packageName": "com.example.order",
    "superName": "com.example.order.BaseOrder",
    "interfaces": [],
    "classFileMajorVersion": 52,
    "jar": {
      "path": "/path/.m2/repository/com/example/order-api/1.1.0/order-api-1.1.0.jar",
      "coordinate": {
        "groupId": "com.example",
        "artifactId": "order-api",
        "version": "1.1.0",
        "classifier": null
      }
    }
  },
  "fields": [],
  "methods": []
}
```

### 3.5 `get_method`

查看方法签名，并可附带静态调用关系。

| 参数 | 必填 | 默认值 | 说明 |
|---|---:|---:|---|
| `projectPath` | 是 | - | 项目路径 |
| `owner` | 是 | - | 方法所属类的全限定名 |
| `method` | 是 | - | 方法名，例如 `pay` |
| `descriptor` | 否 | 无 | JVM descriptor；不传时返回所有重载 |
| `includeCallers` | 否 | `false` | 是否附带调用方列表 |
| `includeCallees` | 否 | `false` | 是否附带该方法调用的目标列表 |
| `limit` | 否 | `50` | 方法结果分页 |
| `offset` | 否 | `0` | 方法结果分页 |

建议：方法重载较多时先不传 `descriptor`，根据返回的 `descriptor` 再精确查询。

### 3.6 `get_method_overview`

`get_method_overview` is the preferred first step for understanding a method's call graph. It returns method metadata, relation counts, deterministic navigation categories, a small set of representative targets, and static-analysis warnings. It does not return the complete callee list.

Use `descriptor` when the method is overloaded. `representativeLimit` is capped at 50 and defaults to 5. `includeCallers` defaults to true.

```json
{
  "projectPath": "/path/projects/order-service",
  "owner": "com.example.PaymentService",
  "method": "pay",
  "descriptor": "(Lcom/example/Payment;)V",
  "representativeLimit": 5
}
```

The overview exposes `calleeCallSiteCount`, `uniqueCalleeCount`, `categoryCounts`, `representativeCallees`, and `warnings`. Categories are heuristic navigation hints; `resolutionKind` and the warning explain the limits of static bytecode analysis.

### 3.7 `find_callees`

`find_callees` expands one method's outgoing relations. `descriptor` is required. Results are grouped by target method by default, so repeated bytecode call sites include a `callSiteCount` instead of repeated rows. Set `groupBy="callSite"` when individual bytecode edges are needed.

Supported `category` values are `all`, `business`, `data_access`, `bom_expansion`, `export_or_batch`, `framework_or_utility`, and `unknown`. Use `limit` and `offset` with `page.nextOffset` for pagination. `packagePrefix` and `nameQuery` filter targets before the response is built.

```text
get_method_overview(owner, method, descriptor)
  -> find_callees(category="data_access", limit=50)
  -> find_callees(category="export_or_batch", limit=50)
```

### 3.8 `get_field`

查看字段类型、访问标志、注解以及推断出的 Getter/Setter。

| 参数 | 必填 | 默认值 | 说明 |
|---|---:|---:|---|
| `projectPath` | 是 | - | 项目路径 |
| `owner` | 是 | - | 字段所属类全限定名 |
| `fieldName` | 否 | 无 | 不传时返回该类全部字段 |
| `includeAccessors` | 否 | `true` | 是否按命名和签名推断 Getter/Setter |
| `limit` | 否 | `50` | 字段结果分页 |
| `offset` | 否 | `0` | 字段结果分页 |

Getter/Setter 是静态命名约定推断，不代表运行时一定被调用。

### 3.9 `find_subtypes`

查询某个类的子类，或某个接口的实现类。

| 参数 | 必填 | 默认值 | 说明 |
|---|---:|---:|---|
| `projectPath` | 是 | - | 项目路径 |
| `typeName` | 是 | - | 父类或接口全限定名 |
| `recursive` | 否 | `true` | 是否递归查询多级子类 |
| `includeInterfaces` | 否 | `true` | 是否把接口实现关系也纳入 |
| `limit` | 否 | `50` | 结果分页 |
| `offset` | 否 | `0` | 结果分页 |

### 3.10 `find_callers`

查询谁在字节码中调用某个方法。

| 参数 | 必填 | 默认值 | 说明 |
|---|---:|---:|---|
| `projectPath` | 是 | - | 项目路径 |
| `owner` | 是 | - | 被调用方法所属类全限定名 |
| `method` | 是 | - | 被调用方法名 |
| `descriptor` | 是 | - | 被调用方法 JVM descriptor；当前实现强制要求 |
| `matchMode` | 否 | `exact` | `exact` 或 `polymorphic` |
| `limit` | 否 | `50` | 结果分页 |
| `offset` | 否 | `0` | 结果分页 |

`exact` 只匹配明确的目标 owner；`polymorphic` 会结合继承关系返回可能的多态候选。结果是静态字节码近似，不覆盖反射、Spring AOP、动态代理和运行时配置。

### 3.9 decompile_class\r\n\r\ndecompile_class 会生成反编译 Java 文件，并返回 sourcePath。默认不返回完整源码文本，Agent 应按需读取该路径；传入 includeSource=true 才返回 source。反编译同一 JAR 时，服务会同时提取 XML、SQL、YAML、properties、JSON 等文本资源，并在 esources 字段返回文件路径。MyBatis XML 会尽量返回 
amespace，但不建立方法级关系。Agent 不应为读取这些资源自行解压 JAR。\r\n

将已索引 JAR 中的 class 按需反编译为 Java 源码，并把结果写入持久化缓存。

| 参数 | 必填 | 默认值 | 说明 |
|---|---:|---:|---|
| `projectPath` | 是 | - | 项目路径 |
| `className` | 是 | - | 已索引类的全限定名 |
| `targetJavaRelease` | 否 | `8` | 反编译目标版本；应与索引时的目标版本一致 |
| `force` | 否 | `false` | 忽略反编译缓存重新生成 |
| `includeMetadata` | 否 | - | 当前版本为兼容保留字段，不改变返回结果 |

返回：

```json
{
  "className": "com.example.order.OrderDTO",
  "source": "public class OrderDTO { ... }",
  "cached": false,
  "sourcePath": "/path/.java-code-intelligence/<project-id>/decompiled/com/example/order/OrderDTO.java",
  "decompiler": "1.12.0"
}
```

如果类尚未被索引，先调用 `search_classes` 或 `get_class` 确认名称；找不到时返回 `NOT_FOUND`。

## 4. 推荐调用流程

### 4.1 首次理解一个 Maven 项目

```text
1. get_index_status(projectPath)
2. 如果没有可用类索引：index_project(projectPath, targetJavaRelease=8)
3. 检查 summary.diagnostics
4. search_classes(query)
5. get_class(className, includeMembers=true)
6. 对关键方法先调用 get_method_overview
7. 按类别调用 find_callees 展开关键目标
8. 需要源码时只对已确认的关键类调用 decompile_class
```

### 4.2 用户说“查询 OrderDTO”

```text
search_classes(projectPath, query="OrderDTO")
  -> 选择正确的全限定名和来源 JAR
get_class(projectPath, className, includeMembers=true)
  -> 读取父类、接口、注解、字段和方法
```

### 4.3 用户说“谁调用 PaymentService.pay()”

```text
search_classes(query="PaymentService")
get_method(owner="...PaymentService", method="pay")
  -> 如果有重载，选择目标 descriptor
find_callers(owner="...PaymentService", method="pay", descriptor="...")
```

### 4.4 用户说“查看 OrderDTO 源码”

```text
search_classes -> 确认全限定名
get_class -> 确认来源 JAR
decompile_class -> 获取并缓存近似源码
```

### 4.5 依赖变化后刷新

```text
index_project(projectPath, force=true, changeDetection="NONE")
```

如果希望自动用文件元数据判断变化，使用 `changeDetection="METADATA"`；不要仅为了刷新而默认使用 `SHA256`。

## 5. 结果、分页和错误处理

### 5.1 分页

普通列表默认 50 条，最大 500 条。收到：

```json
{
  "truncated": true,
  "nextOffset": 50
}
```

下一次请求保持原查询条件，只把 `offset` 改为 `nextOffset`。

### 5.2 Tool 错误

当前处理器常见返回：

| code | 处理建议 |
|---|---|
| `INVALID_ARGUMENT` | 修正缺失字段、枚举值、数字范围或 descriptor |
| `NOT_FOUND` | 先搜索类或确认索引是否存在 |
| `INDEX_ERROR` | 查看 message 和 Maven/文件系统诊断，修复环境后再重试 |
| `MAVEN_*` | 出现在 `index_project` 的 `diagnostics`，按模块判断是否可继续使用部分结果 |
| `CLASS_VERSION_EXCLUDED` | `enforceTargetRelease=true` 时的可预期警告；降低目标版本或关闭严格过滤后重新索引 |

`MAVEN_MODULE_RESOLUTION_FAILED` 表示某个 Module 没有可用 classpath；如果其他 Module 有结果，服务仍可能返回部分索引。所有 Module 都失败时，`index_project` 会返回 `INDEX_ERROR`。

### 5.3 诊断字段

```json
{
  "code": "MAVEN_PARTIAL_RESOLUTION",
  "message": "...",
  "severity": "WARNING",
  "jarEntry": null,
  "retryable": true
}
```

`diagnostics` 可能同时包含 Maven 解析诊断和 class 扫描诊断。AI 应把它们作为上下文提示，不要把 warning 误判为全部索引失败。

## 6. 缓存和文件位置

默认缓存根目录：

```text
<user.home>/.java-code-intelligence/<project-id>/
  index.db
  jars/
  decompiled/
```

`project-id` 由规范化项目路径生成。反编译源码不会自动删除。Maven probe、effective POM 和 classpath 临时文件位于系统临时目录，完成后清理，不写入目标项目。

当前没有 MCP tool 参数或专用 `JCI_*` 环境变量用于改缓存根目录、SQLite 文件名、分页上限或 Vineflower 版本。需要改变用户缓存根目录时只能改变 Java 进程的 `user.home`，这属于宿主进程级行为，不建议由 Agent 在普通查询中修改。

## 7. 能力边界

- 只索引第三方 JAR；工作区 `.java` 源码不在第一阶段建立完整 AST 索引。
- ASM 调用图是静态字节码近似，不能还原反射、Spring AOP、动态代理和运行时分派的全部事实。
- Vineflower 生成的是近似源码，注释、局部变量名、泛型细节和控制流可能与原始源码不同。
- MCP 不编译、打包、安装或运行目标项目。
- Maven 仍需要能够解析有效 POM、Maven 插件和外部仓库；认证或仓库不可用时应修复 Maven 环境，而不是反复调用 `force=true`。

## 8. 最小可执行示例

```json
{
  "name": "index_project",
  "arguments": {
    "projectPath": "/path/projects/order-service",
    "targetJavaRelease": 8,
    "changeDetection": "NONE",
    "includeTestDependencies": false
  }
}
```

索引成功后：

```json
{
  "name": "search_classes",
  "arguments": {
    "projectPath": "/path/projects/order-service",
    "query": "OrderDTO",
    "limit": 20,
    "offset": 0
  }
}
```

再根据返回的全限定名调用 `get_class`、`get_method`、`find_callers` 或 `decompile_class`。
