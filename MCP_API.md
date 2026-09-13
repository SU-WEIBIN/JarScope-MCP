# Java Code Intelligence MCP 接口文档

## 1. 文档信息

| 项目 | 内容 |
|---|---|
| 文档状态 | 第一阶段接口草案 |
| 服务名称 | Java Code Intelligence MCP |
| 传输方式 | stdio |
| 服务运行时 | JDK 21 |
| 默认分析目标 | Java 8 项目 |
| 默认变化检测 | `NONE` |
| 接口风格 | MCP Tools + 结构化 JSON |

本接口文档定义 MCP 业务工具层的输入、输出和错误语义。JSON-RPC 编解码、传输管理和协议版本协商由官方 MCP Java SDK 负责。

文档示例中的 `/path/...` 均为脱敏虚拟路径，实际调用时请替换为目标机器上的路径。

如果你是负责实际调用服务的 AI Agent，请先阅读 [AI Agent 配置与调用指南](AI_AGENT_GUIDE.md)；本文档更适合实现 MCP Client 或核对协议细节。

参考实现：[MCP Java SDK](https://github.com/modelcontextprotocol/java-sdk)。

## 2. 全局约定

### 2.1 传输

第一阶段只支持 stdio：

- MCP Server 从 stdin 读取协议消息；
- MCP Server 向 stdout 写入协议消息；
- stdout 不允许输出日志；
- 日志、诊断和启动信息写入 stderr；
- 服务作为本地子进程由 Claude Code 等 MCP Client 启动。

HTTP、SSE 和 Streamable HTTP 不属于第一阶段接口承诺。

### 2.2 路径

所有工具都使用 `projectPath` 定位目标项目。

建议使用绝对路径：

```json
{
  "projectPath": "/path/projects/order-service"
}
```

服务会：

1. 规范化路径；
2. 检查路径存在且为目录；
3. 定位根 `pom.xml`；
4. 使用项目路径生成缓存项目 ID；
5. 不把项目路径原样拼接到任意 shell 命令中。

### 2.3 默认值

| 参数 | 默认值 |
|---|---|
| `targetJavaRelease` | `8` |
| `enforceTargetRelease` | `false` |
| `changeDetection` | `NONE` |
| `force` | `false` |
| `includeTestDependencies` | `false` |
| 查询 `limit` | 50 |
| 查询最大 `limit` | 500 |
| 递归子类查询 | `true` |
| 多态调用匹配 | `exact` |

### 2.4 结果限制

- 普通列表查询默认最多返回 50 条；
- 单次查询最多返回 500 条；
- 超过限制时返回 `truncated=true` 和分页信息；
- 反编译源码默认最大返回 2 MB；
- 超过源码大小时返回完整缓存路径和截断提示；
- 工具不会因为结果过多而自动把所有数据拼接到文本字段中。

### 2.5 Maven 运行环境

`index_project` 不接受 Maven 可执行文件路径作为请求参数。服务默认执行 PATH 中的 Maven（Windows 为 `mvn.cmd`，其他系统为 `mvn`），并继承 Maven 的 POM、用户 `settings.xml` 和本地仓库配置。启动服务时可以使用以下环境变量或 Java 系统属性覆盖：

| 配置 | 环境变量 | Java 系统属性 | 默认值 |
|---|---|---|---|
| Maven 可执行文件 | `JCI_MAVEN_COMMAND` | `jci.maven.command` | Windows 为 `mvn.cmd`，其他系统为 `mvn`（若设置 `MAVEN_HOME` 则使用其 `bin/mvn[.cmd]`） |
| Maven 超时 | `JCI_MAVEN_TIMEOUT_SECONDS` | `jci.maven.timeoutSeconds` | `120` 秒 |

系统属性优先于环境变量。Maven 路径和超时属于服务级配置，不会写入索引数据库；`includeTestDependencies` 仍是 `index_project` 的请求级选项。

多 Module 根 POM 不要求先执行 `compile`、`package` 或 `install`。服务先读取 `help:effective-pom`，识别 Reactor 内部坐标，再为每个 Module 在系统临时目录生成移除内部依赖的 probe POM，并调用 `dependency:build-classpath` 解析第三方 JAR。目标项目目录不会写入 probe POM、classpath 文件或构建产物。

如果本机 Maven 无法解析插件、外部仓库或认证镜像，工具会返回模块级 `diagnostics`；有可用 classpath 的模块会保留部分结果。服务不会把 `target/` 下已有文件注册为 Maven artifact，也不会替用户执行目标项目的构建生命周期。

## 3. MCP 生命周期

### 3.1 初始化

客户端启动服务后发送 `initialize`：

```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "method": "initialize",
  "params": {
    "protocolVersion": "client-negotiated-version",
    "capabilities": {},
    "clientInfo": {
      "name": "claude-code",
      "version": "client-version"
    }
  }
}
```

服务通过 SDK 返回服务器信息和能力：

```json
{
  "protocolVersion": "server-selected-version",
  "capabilities": {
    "tools": {}
  },
  "serverInfo": {
    "name": "java-code-intelligence",
    "version": "0.1.0"
  }
}
```

客户端随后发送：

```json
{
  "jsonrpc": "2.0",
  "method": "notifications/initialized",
  "params": {}
}
```

### 3.2 工具发现

客户端调用 `tools/list` 获取工具定义。服务必须返回工具名称、描述和输入 schema。

### 3.3 工具调用

客户端调用 `tools/call`：

```json
{
  "jsonrpc": "2.0",
  "id": 2,
  "method": "tools/call",
  "params": {
    "name": "get_class",
    "arguments": {
      "projectPath": "/path/projects/order-service",
      "className": "com.example.order.OrderDTO"
    }
  }
}
```

业务结果使用结构化 JSON 返回，同时生成适合 AI 阅读的文本摘要。

### 3.4 服务存活

服务支持 MCP 标准 `ping`。索引和反编译属于同步工具操作时，客户端不应重复启动同一个项目的第二个写入进程。

## 4. 工具总览

| 工具 | 作用 | 是否写入 |
|---|---|---|
| `index_project` | 扫描 Maven 项目并建立或更新索引 | 是 |
| `get_index_status` | 查询索引状态和统计 | 否 |
| `search_classes` | 按类名或包名搜索类 | 否 |
| `get_class` | 获取完整类结构 | 否 |
| `get_method` | 获取方法签名和关系 | 否 |
| `get_method_overview` | 获取有界的调用关系统计和代表性证据 | 否 |
| `get_field` | 获取字段、类型和注解 | 否 |
| `find_subtypes` | 查询继承和实现关系 | 否 |
| `find_callers` | 查询静态字节码调用方 | 否 |
| `find_callees` | 按类别、包名或方法名分页查询被调用方 | 否 |
| `decompile_class` | 按需反编译并缓存源码 | 可能写入缓存 |

## 5. 公共数据类型

### 5.1 ProjectRef

```json
{
  "projectPath": "/path/projects/order-service"
}
```

### 5.2 JarRef

```json
{
  "id": 12,
  "groupId": "com.example",
  "artifactId": "order-api",
  "version": "1.1.0",
  "classifier": null,
  "fileName": "order-api-1.1.0.jar",
  "path": "/path/.m2/repository/com/example/order-api/1.1.0/order-api-1.1.0.jar"
}
```

### 5.3 PageInfo

```json
{
  "limit": 50,
  "offset": 0,
  "returned": 2,
  "total": 2,
  "truncated": false,
  "nextOffset": null
}
```

### 5.4 Diagnostic

```json
{
  "code": "CLASS_PARSE_FAILED",
  "message": "无法解析 class 文件",
  "severity": "warning",
  "jar": "order-api-1.1.0.jar",
  "entry": "com/example/Bad.class",
  "retryable": false
}
```

### 5.5 ClassInfo

```json
{
  "name": "com.example.order.OrderDTO",
  "internalName": "com/example/order/OrderDTO",
  "kind": "class",
  "packageName": "com.example.order",
  "access": ["public"],
  "superClass": "com.example.order.BaseOrder",
  "interfaces": ["java.io.Serializable"],
  "sourceFile": "OrderDTO.java",
  "classFileJavaRelease": 8,
  "jar": {
    "groupId": "com.example",
    "artifactId": "order-api",
    "version": "1.1.0",
    "fileName": "order-api-1.1.0.jar"
  }
}
```

### 5.6 MethodInfo

```json
{
  "owner": "com.example.PaymentService",
  "name": "pay",
  "descriptor": "(Lcom/example/Payment;)V",
  "parameters": [
    {
      "name": "payment",
      "type": "com.example.Payment"
    }
  ],
  "returnType": "void",
  "exceptions": [],
  "access": ["public"],
  "annotations": []
}
```

### 5.7 FieldInfo

```json
{
  "owner": "com.example.order.OrderDTO",
  "name": "status",
  "type": "java.lang.String",
  "genericType": null,
  "access": ["private"],
  "static": false,
  "final": false,
  "annotations": [
    {
      "type": "com.fasterxml.jackson.annotation.JsonProperty",
      "values": {
        "value": "status"
      }
    }
  ],
  "accessors": {
    "getter": "getStatus()",
    "setter": "setStatus(java.lang.String)"
  }
}
```

### 5.8 CallSiteInfo

```json
{
  "caller": {
    "owner": "com.example.PaymentService",
    "name": "pay",
    "descriptor": "(Lcom/example/Payment;)V"
  },
  "target": {
    "owner": "com.example.PaymentRepository",
    "name": "save",
    "descriptor": "(Lcom/example/Payment;)V"
  },
  "opcode": "invokeinterface",
  "resolutionKind": "DIRECT",
  "sourceJar": "payment-service-1.0.0.jar"
}
```

`resolutionKind` 定义：

| 值 | 含义 |
|---|---|
| `DIRECT` | 字节码中存在明确目标调用 |
| `POLYMORPHIC_CANDIDATE` | 根据继承关系推导出的可能目标 |
| `UNRESOLVED` | 无法静态解析 |

## 6. 工具接口

## 6.1 index_project

建立或更新项目索引。

### 输入

```json
{
  "projectPath": "/path/projects/order-service",
  "force": false,
  "changeDetection": "NONE",
  "targetJavaRelease": 8,
  "enforceTargetRelease": false,
  "includeTestDependencies": false
}
```

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|---|---|---:|---:|---|
| `projectPath` | string | 是 | - | Maven 项目目录 |
| `force` | boolean | 否 | false | 忽略现有索引并重建 |
| `changeDetection` | enum | 否 | NONE | NONE / METADATA / SHA256（大小写不敏感） |
| `targetJavaRelease` | integer | 否 | 8 | 目标 Java 版本 |
| `enforceTargetRelease` | boolean | 否 | false | 是否跳过更高版本 class |
| `includeTestDependencies` | boolean | 否 | false | 是否包含 test 依赖 |

### 返回

```json
{
  "status": "indexed",
  "project": {
    "projectPath": "/path/projects/order-service",
    "projectId": "project-9c1f",
    "targetJavaRelease": 8,
    "changeDetection": "NONE"
  },
  "summary": {
    "modules": 3,
    "jarsDiscovered": 126,
    "jarsIndexed": 4,
    "jarsReused": 122,
    "classes": 18420,
    "methods": 96500,
    "fields": 31200,
    "calls": 248000,
    "errors": 2,
    "elapsedMs": 18400,
    "diagnostics": []
  },
  "diagnostics": [],
  "text": "项目索引完成：3 个 Module，126 个 JAR，新增索引 4 个。"
}
```

`diagnostics` 同时出现在 `summary` 和工具结果的顶层，内容相同，便于 MCP 客户端直接读取。Maven 解析诊断使用 `MAVEN_*` code；有可用 classpath 的模块可以在存在 warning 时继续索引。

`status`：

| 值 | 含义 |
|---|---|
| `indexed` | 已完成索引 |
| `reused` | 直接复用已有索引 |
| `partial` | 部分 class 或 JAR 失败 |
| `failed` | 项目级索引失败 |
| `in_progress` | 已有索引任务正在执行 |

当 `changeDetection=NONE` 时，依赖变化不会自动触发重建，必须使用 `force=true`。服务同时接受小写写法，但 MCP schema 使用大写枚举作为规范形式。

## 6.2 get_index_status

查询项目当前索引状态。

### 输入

```json
{
  "projectPath": "/path/projects/order-service"
}
```

### 返回

```json
{
  "projectPath": "/path/projects/order-service",
  "projectId": "project-9c1f",
  "exists": true,
  "status": "ready",
  "lastIndexedAt": "2026-09-11T10:30:00+08:00",
  "targetJavaRelease": 8,
  "changeDetection": "NONE",
  "summary": {
    "modules": 3,
    "jars": 126,
    "classes": 18420,
    "methods": 96500,
    "fields": 31200,
    "calls": 248000,
    "errors": 2
  },
  "databasePath": "/path/.java-code-intelligence/project-9c1f/index.db",
  "rebuildRequired": false,
  "text": "索引已就绪。"
}
```

`rebuildRequired` 只表示服务已明确知道需要重建，例如配置版本发生变化；`changeDetection=NONE` 不主动计算 JAR 内容变化。

## 6.3 search_classes

按简单类名、全限定类名或包前缀搜索类。

### 输入

```json
{
  "projectPath": "/path/projects/order-service",
  "query": "OrderDTO",
  "packagePrefix": "com.example",
  "kind": "class",
  "limit": 50,
  "offset": 0
}
```

| 字段 | 类型 | 必填 | 说明 |
|---|---|---:|---|
| `query` | string | 是 | 类名、全限定名或模糊关键词 |
| `packagePrefix` | string | 否 | 限制包前缀 |
| `kind` | enum | 否 | class / interface / enum / annotation |
| `limit` | integer | 否 | 返回数量 |
| `offset` | integer | 否 | 分页偏移 |

### 返回

```json
{
  "items": [
    {
      "name": "com.example.order.OrderDTO",
      "kind": "class",
      "jar": "order-api-1.1.0.jar",
      "score": 1.0
    }
  ],
  "page": {
    "limit": 50,
    "offset": 0,
    "returned": 1,
    "total": 1,
    "truncated": false,
    "nextOffset": null
  },
  "text": "找到 1 个类：com.example.order.OrderDTO"
}
```

如果简单类名对应多个候选，服务返回全部候选，不自动猜测唯一结果。

## 6.4 get_class

获取类的完整结构。

### 输入

```json
{
  "projectPath": "/path/projects/order-service",
  "className": "com.example.order.OrderDTO",
  "includeMembers": true,
  "includeAnnotations": true
}
```

| 字段 | 类型 | 必填 | 默认值 |
|---|---|---:|---:|
| `className` | string | 是 | - |
| `includeMembers` | boolean | 否 | true |
| `includeAnnotations` | boolean | 否 | true |

### 返回

```json
{
  "class": {
    "name": "com.example.order.OrderDTO",
    "kind": "class",
    "packageName": "com.example.order",
    "superClass": "com.example.order.BaseOrder",
    "interfaces": ["java.io.Serializable"],
    "annotations": [],
    "fields": [],
    "methods": [],
    "jar": {
      "groupId": "com.example",
      "artifactId": "order-api",
      "version": "1.1.0",
      "fileName": "order-api-1.1.0.jar"
    }
  },
  "text": "com.example.order.OrderDTO extends BaseOrder implements Serializable"
}
```

## 6.5 get_method

查询方法签名、参数、返回值和调用关系。

### 输入

```json
{
  "projectPath": "/path/projects/order-service",
  "owner": "com.example.PaymentService",
  "method": "pay",
  "descriptor": "(Lcom/example/Payment;)V",
  "includeCallers": true,
  "includeCallees": true
}
```

| 字段 | 类型 | 必填 | 默认值 |
|---|---|---:|---:|
| `owner` | string | 是 | - |
| `method` | string | 是 | - |
| `descriptor` | string | 否 | null |
| `includeCallers` | boolean | 否 | false |
| `includeCallees` | boolean | 否 | false |

未提供 descriptor 时返回全部重载方法。

当 `includeCallees=true` 时，`get_method` 会返回原始静态调用边，调用边很多时结果可能很大。对于调用关系分析，推荐先调用 `get_method_overview` 获取有界摘要，再用 `find_callees` 按类别和分页展开；这样仍保留 `get_method` 的向后兼容行为，同时避免一次传输整个调用图。

### 返回

```json
{
  "items": [
    {
      "method": {
        "owner": "com.example.PaymentService",
        "name": "pay",
        "descriptor": "(Lcom/example/Payment;)V",
        "parameters": [],
        "returnType": "void",
        "access": ["public"],
        "annotations": []
      },
      "callers": [],
      "callees": [
        {
          "owner": "com.example.PaymentRepository",
          "name": "save",
          "descriptor": "(Lcom/example/Payment;)V",
          "resolutionKind": "DIRECT"
        }
      ]
    }
  ],
  "text": "找到 1 个方法：PaymentService.pay(Payment): void"
}
```

## 6.5a get_method_overview

获取一个方法或全部重载的有界调用关系概览。该接口只返回计数、少量调用方示例和代表性被调用方，不返回完整 callee 列表。

### 输入

```json
{
  "projectPath": "/path/projects/order-service",
  "owner": "com.example.PaymentService",
  "method": "pay",
  "descriptor": "(Lcom/example/Payment;)V",
  "includeCallers": true,
  "representativeLimit": 5
}
```

| 字段 | 类型 | 必填 | 默认值 |
|---|---|---:|---:|
| `projectPath` | string | 是 | - |
| `owner` | string | 是 | - |
| `method` | string | 是 | - |
| `descriptor` | string | 否 | null，返回全部重载 |
| `includeCallers` | boolean | 否 | true |
| `representativeLimit` | integer | 否 | 5，范围 1-50 |

### 返回

```json
{
  "owner": "com.example.PaymentService",
  "method": "pay",
  "items": [
    {
      "method": {
        "owner": "com.example.PaymentService",
        "name": "pay",
        "descriptor": "(Lcom/example/Payment;)V",
        "parameters": [],
        "returnType": "void",
        "access": ["public"],
        "annotations": []
      },
      "callerCount": 2,
      "callerExamples": [],
      "calleeCallSiteCount": 4,
      "uniqueCalleeCount": 3,
      "categoryCounts": [
        { "category": "DATA_ACCESS", "count": 1 },
        { "category": "BUSINESS", "count": 2 },
        { "category": "FRAMEWORK_OR_UTILITY", "count": 1 }
      ],
      "representativeCallees": [
        {
          "edge": {
            "callerOwner": "com.example.PaymentService",
            "callerName": "pay",
            "callerDescriptor": "(Lcom/example/Payment;)V",
            "targetOwner": "com.example.PaymentRepository",
            "targetName": "save",
            "targetDescriptor": "(Lcom/example/Payment;)V",
            "resolutionKind": "DIRECT"
          },
          "category": "DATA_ACCESS",
          "callSiteCount": 1,
          "reason": "Mapper/Repository or SQL-like target"
        }
      ],
      "warnings": []
    }
  ],
  "text": "Overview for 1 method overload(s); use find_callees to expand targets"
}
```

`categoryCounts` 和 `representativeCallees` 使用确定性的启发式分类，仅用于导航和筛选，不替代完整的类型或数据流分析。`warnings` 会提示未解析调用或无法分类的调用边。

## 6.5b find_callees

分页查询指定方法的 outgoing call edges。默认按目标方法合并重复调用点，并在 `callSiteCount` 中报告合并数量；需要逐条静态调用边时将 `groupBy` 设置为 `callSite`。

### 输入

```json
{
  "projectPath": "/path/projects/order-service",
  "owner": "com.example.PaymentService",
  "method": "pay",
  "descriptor": "(Lcom/example/Payment;)V",
  "category": "data_access",
  "packagePrefix": "com.example.payment",
  "nameQuery": "save",
  "groupBy": "target",
  "limit": 50,
  "offset": 0
}
```

| 字段 | 类型 | 必填 | 默认值 |
|---|---|---:|---:|
| `projectPath` | string | 是 | - |
| `owner` | string | 是 | - |
| `method` | string | 是 | - |
| `descriptor` | string | 是 | - |
| `category` | string | 否 | `all` |
| `packagePrefix` | string | 否 | 不筛选目标包 |
| `nameQuery` | string | 否 | 不筛选目标 owner/name |
| `groupBy` | string | 否 | `target` |
| `limit` | integer | 否 | 50，最大 500 |
| `offset` | integer | 否 | 0 |

`category` 可选值为 `all`、`business`、`data_access`、`bom_expansion`、`export_or_batch`、`framework_or_utility`、`unknown`；`groupBy` 可选 `target` 或 `callSite`。

### 返回

```json
{
  "owner": "com.example.PaymentService",
  "method": "pay",
  "descriptor": "(Lcom/example/Payment;)V",
  "items": [
    {
      "edge": {
        "callerOwner": "com.example.PaymentService",
        "callerName": "pay",
        "callerDescriptor": "(Lcom/example/Payment;)V",
        "targetOwner": "com.example.PaymentRepository",
        "targetName": "save",
        "targetDescriptor": "(Lcom/example/Payment;)V",
        "resolutionKind": "DIRECT"
      },
      "category": "DATA_ACCESS",
      "callSiteCount": 2,
      "reason": "Mapper/Repository or SQL-like target"
    }
  ],
  "page": {
    "limit": 50,
    "offset": 0,
    "returned": 1,
    "total": 1,
    "truncated": false,
    "nextOffset": null
  },
  "text": "Found 1 callee targets"
}
```

`page.total` 是当前筛选和分组方式下的总数。外层适配器如果因响应过大生成 `fullResultPath`，该文件可作为原始结果兜底；正常使用时优先通过 `limit`、`offset`、`category`、`packagePrefix` 和 `nameQuery` 分页读取。

## 6.6 get_field

查询字段、类型、访问权限、注解和可能的 Getter/Setter。

### 输入

```json
{
  "projectPath": "/path/projects/order-service",
  "owner": "com.example.order.OrderDTO",
  "fieldName": "status",
  "includeAccessors": true
}
```

| 字段 | 类型 | 必填 | 默认值 |
|---|---|---:|---:|
| `owner` | string | 是 | - |
| `fieldName` | string | 否 | null |
| `includeAccessors` | boolean | 否 | true |

未提供 `fieldName` 时返回该类的全部字段。

### 返回

```json
{
  "items": [
    {
      "owner": "com.example.order.OrderDTO",
      "name": "status",
      "type": "java.lang.String",
      "access": ["private"],
      "annotations": [],
      "accessors": {
        "getter": "getStatus()",
        "setter": "setStatus(java.lang.String)"
      }
    }
  ],
  "text": "找到字段：OrderDTO.status: String"
}
```

Getter/Setter 是根据命名和 descriptor 推导的可能关系，不保证一定是 JavaBean 语义。

## 6.7 find_subtypes

查询某个 class 或 interface 的子类和实现类。

### 输入

```json
{
  "projectPath": "/path/projects/order-service",
  "typeName": "com.example.order.BaseOrder",
  "recursive": true,
  "includeInterfaces": true,
  "limit": 100,
  "offset": 0
}
```

### 返回

```json
{
  "type": "com.example.order.BaseOrder",
  "items": [
    {
      "name": "com.example.order.OrderDTO",
      "relation": "extends",
      "distance": 1,
      "jar": "order-api-1.1.0.jar"
    }
  ],
  "page": {
    "limit": 100,
    "offset": 0,
    "returned": 1,
    "total": 1,
    "truncated": false,
    "nextOffset": null
  },
  "text": "BaseOrder 共有 1 个子类或实现类。"
}
```

## 6.8 find_callers

查询调用某个方法的静态字节码调用方。

### 输入

```json
{
  "projectPath": "/path/projects/order-service",
  "owner": "com.example.PaymentRepository",
  "method": "save",
  "descriptor": "(Lcom/example/Payment;)V",
  "matchMode": "exact",
  "limit": 100,
  "offset": 0
}
```

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|---|---|---:|---:|---|
| `owner` | string | 是 | - | 目标方法所属类 |
| `method` | string | 是 | - | 目标方法名 |
| `descriptor` | string | 是 | - | JVM 方法 descriptor；当前实现强制要求 |
| `matchMode` | enum | 否 | exact | exact / polymorphic |

`exact` 只返回目标 owner、name 和 descriptor 完全匹配的调用边。`polymorphic` 可以根据已知继承关系返回可能的多态候选，并在每条结果中标明 `POLYMORPHIC_CANDIDATE`。

### 返回

```json
{
  "items": [
    {
      "callerOwner": "com.example.PaymentService",
      "callerName": "pay",
      "callerDescriptor": "(Lcom/example/Payment;)V",
      "targetOwner": "com.example.PaymentRepository",
      "targetName": "save",
      "targetDescriptor": "(Lcom/example/Payment;)V",
      "opcode": "invokeinterface",
      "resolutionKind": "DIRECT",
      "sourceJar": {
        "path": "/path/.m2/repository/com/example/payment-service/1.0.0/payment-service-1.0.0.jar",
        "coordinate": {
          "groupId": "com.example",
          "artifactId": "payment-service",
          "version": "1.0.0",
          "classifier": null
        }
      }
    }
  ],
  "page": {
    "limit": 100,
    "offset": 0,
    "total": 1,
    "truncated": false,
    "nextOffset": null
  },
  "text": "找到 1 个直接调用方。"
}
```

该工具不承诺覆盖反射、动态代理、Spring AOP 或运行时容器调用。

## 6.9 decompile_class

反编译指定类并缓存结果。

### 输入

```json
{
  "projectPath": "/path/projects/order-service",
  "className": "com.example.order.OrderDTO",
  "targetJavaRelease": 8,
  "force": false,
  "includeMetadata": true
}
```

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|---|---|---:|---:|---|
| `className` | string | 是 | - | 全限定类名 |
| `targetJavaRelease` | integer | 否 | 项目默认值 | 反编译目标版本 |
| `force` | boolean | 否 | false | 忽略已有反编译缓存 |
| `includeMetadata` | boolean | 否 | true | 是否返回来源和缓存信息 |

### 返回

```json
{
  "className": "com.example.order.OrderDTO",
  "source": "package com.example.order;\\n\\npublic class OrderDTO { ... }",
  "cached": true,
  "sourcePath": "/path/.java-code-intelligence/project-9c1f/decompiled/com/example/order/OrderDTO.java",
  "jar": {
    "fileName": "order-api-1.1.0.jar",
    "version": "1.1.0"
  },
  "decompiler": {
    "name": "vineflower",
    "version": "pinned-version"
  },
  "warnings": [
    "这是由字节码反编译得到的近似源码，不保证与原始源码完全一致。"
  ],
  "text": "已返回 OrderDTO 的反编译源码，缓存命中。"
}
```

## 7. 错误协议

工具业务错误不应泄露 Java stack trace、机器绝对路径或 Maven 私有凭证。

### 7.1 错误结构

```json
{
  "error": {
    "code": "CLASS_NOT_FOUND",
    "message": "未找到类 com.example.OrderDTO",
    "details": {
      "className": "com.example.OrderDTO",
      "suggestions": [
        "com.example.order.OrderDTO"
      ]
    },
    "retryable": false
  },
  "text": "未找到目标类，请尝试使用完整类名。"
}
```

### 7.2 错误码

| 错误码 | 含义 | 可重试 |
|---|---|---:|
| `INVALID_ARGUMENT` | 参数缺失或格式错误 | 否 |
| `PROJECT_NOT_FOUND` | 项目目录不存在 | 否 |
| `POM_NOT_FOUND` | 未找到 pom.xml | 否 |
| `PATH_NOT_ALLOWED` | 路径不在允许范围 | 否 |
| `MAVEN_RESOLUTION_FAILED` | Maven 依赖解析失败 | 视情况 |
| `INDEX_NOT_FOUND` | 尚未建立索引 | 否 |
| `INDEX_IN_PROGRESS` | 索引正在执行 | 是 |
| `CLASS_NOT_FOUND` | 未找到类 | 否 |
| `AMBIGUOUS_SYMBOL` | 简单名对应多个候选 | 否 |
| `METHOD_NOT_FOUND` | 未找到方法 | 否 |
| `FIELD_NOT_FOUND` | 未找到字段 | 否 |
| `UNSUPPORTED_CLASS_VERSION` | class 版本不支持 | 否 |
| `DECOMPILATION_FAILED` | 反编译失败 | 是 |
| `RESULT_TOO_LARGE` | 结果超过大小限制 | 否 |
| `IO_ERROR` | 文件或数据库读写失败 | 是 |
| `INTERNAL_ERROR` | 未分类服务错误 | 视情况 |

`MAVEN_EFFECTIVE_POM_PARTIAL`、`MAVEN_MODULE_RESOLUTION_FAILED`、`MAVEN_PARTIAL_RESOLUTION` 和 `CLASS_VERSION_EXCLUDED` 是 `index_project` 返回的 `diagnostics[].code`，用于表达 Maven 的整体警告、单模块失败、可恢复的部分 classpath 以及严格 Java 版本过滤；它们不替代 MCP 协议级错误。

### 7.3 MCP 错误与工具错误

协议级错误由 MCP SDK 处理。业务错误通过工具结果返回，并设置工具结果的错误状态。客户端必须同时检查：

- MCP JSON-RPC 是否返回协议错误；
- 工具结果是否包含 `error`；
- 工具结果是否标记为 `isError=true`。

## 8. 状态和并发约定

### 8.1 索引状态

```text
new
indexing
ready
partial
failed
```

### 8.2 写入锁

- 同一项目同一时间只允许一个索引写入任务；
- 反编译缓存写入使用临时文件和原子移动；
- 查询可以在索引完成后并发执行；
- 第一阶段不保证多个 MCP 进程同时写入同一个项目数据库。

### 8.3 长任务

第一阶段工具调用以同步结果为主。索引期间再次调用 `index_project` 返回 `INDEX_IN_PROGRESS`，而不是启动第二个索引任务。

后续如果需要进度通知，可以增加 MCP progress notification，但不改变现有工具输入输出。

## 9. 安全约定

- `projectPath` 必须经过规范化和允许根目录检查；
- Maven 子进程必须使用参数数组，不通过 shell 拼接命令；
- 不执行来自 JAR 内容的任意代码；
- 不把 Maven settings 中的密码写入返回值或日志；
- 限制 JAR 条目大小和反编译输出大小；
- 日志默认不输出完整源码和敏感路径；
- 反编译结果只作为 AI 阅读参考，不作为可执行代码。

## 10. Claude Code 配置示例

服务打包为可执行 JAR 后，MCP 配置可以使用：

```json
{
  "mcpServers": {
    "java-code-intelligence": {
      "command": "java",
      "args": [
        "-jar",
        "/path/tools/java-code-intelligence/java-code-intelligence.jar"
      ]
    }
  }
}
```

如果服务需要显式指定缓存目录，可以通过环境变量：

```json
{
  "mcpServers": {
    "java-code-intelligence": {
      "command": "java",
      "args": [
        "-Djava-code-intelligence.cache-root=/path/java-code-intelligence-cache",
        "-jar",
        "/path/tools/java-code-intelligence/java-code-intelligence.jar"
      ]
    }
  }
}
```

## 11. 版本兼容

工具输入字段新增时必须保持旧字段语义不变。

兼容规则：

- 新增可选字段不提高接口主版本；
- 删除字段或改变字段类型必须提高主版本；
- 错误码只新增，不复用已有错误码；
- 枚举新增值时客户端必须允许未知值；
- 返回对象新增字段时，客户端不得依赖字段集合完全固定；
- 方法 descriptor 的 JVM 格式不可改为 Java 源码签名。

接口版本随服务版本发布，例如：

```text
0.1.x  第一阶段接口
0.2.x  向后兼容的工具扩展
1.0.x  稳定接口
```

## 12. 第一阶段接口验收

- 客户端可以完成 initialize 和 tools/list；
- 11 个工具都能被发现；
- 所有必填参数缺失时返回 `INVALID_ARGUMENT`；
- 项目不存在时返回 `PROJECT_NOT_FOUND`；
- 首次 `index_project` 可以建立 SQLite 索引；
- 默认 `changeDetection=NONE` 不计算 JAR SHA-256；
- `force=true` 可以触发重建；
- 默认 `targetJavaRelease=8`；
- 可以为请求指定 Java 11、17 或 21；
- `get_class`、`get_method`、`get_method_overview`、`get_field`、`find_subtypes`、`find_callers`、`find_callees` 返回结构化结果；
- `decompile_class` 首次生成源码、二次调用命中缓存；
- 列表结果具有 limit、offset 和 truncated；
- 业务错误具有稳定错误码；
- stdout 不混入日志；
- Claude Code 可以正常调用所有第一阶段工具。

## 13. 与项目概要的关系

本文件定义 MCP 调用层协议。总体架构、SQLite 详细设计、编码阶段拆分和非目标范围见：

[PROJECT_OVERVIEW.md](./PROJECT_OVERVIEW.md)

## 14. 调用关系渐进式读取

推荐按需逐层展开调用图：

```text
get_method
  -> 确认目标重载的 descriptor
get_method_overview
  -> 查看调用方/被调用方计数、类别、代表性证据和 warnings
find_callees
  -> 只展开当前问题相关的类别、包名或方法名，并使用 limit/offset 分页
decompile_class
  -> 仅对选中的类查看反编译源码
```

`get_method_overview` 始终有界，不包含完整 callee 列表；`categoryCounts` 和 `representativeCallees` 是确定性的导航提示。`find_callees` 必须提供 descriptor，默认按目标方法合并重复调用点，并返回 `page.total`、`page.truncated` 和 `page.nextOffset`。

外层适配器如果因响应过大生成 `fullResultPath`，该文件是原始结果兜底，不是正常分页流程的前置条件。优先使用 `get_method_overview` 和 `find_callees`，只有在需要逐条调用点或完整原始数据时才读取大结果文件。
