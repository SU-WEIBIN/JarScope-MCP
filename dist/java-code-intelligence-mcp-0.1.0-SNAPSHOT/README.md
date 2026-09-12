# Java Code Intelligence MCP

面向 AI Agent 的 Java 第三方二进制代码理解服务。服务运行在 JDK 21 上，默认按 Java 8 目标版本选择和解析依赖中的 class；被分析项目可以继续使用 JDK 8。

当前 MVP 已包含：

- Maven 多模块依赖 JAR 发现（通过 `ProcessBuilder`，不拼接 shell 命令）
- ASM class/interface/enum、字段、方法、注解、继承和静态调用边解析
- SQLite 持久化索引和按项目缓存布局
- `NONE`（默认）、`METADATA`、`SHA256` 三种可选变更检测模式；默认不计算 JAR 指纹
- 类、方法、字段、子类型、调用方查询及分页
- Vineflower 1.12.0 按需反编译和源码缓存
- MCP stdio 九个工具

## 构建

需要 JDK 21 和 Maven 3.6+：

```powershell
$env:JAVA_HOME = '/path/jdk-21'
mvn clean verify
```

产物位于 `target/java-code-intelligence-mcp-0.1.0-SNAPSHOT.jar`，是包含依赖的可执行 shaded JAR。

## Claude Code 配置示例

```json
{
  "mcpServers": {
    "java-code-intelligence": {
      "command": "java",
      "args": ["-jar", "/path/tools/java-code-intelligence-mcp-0.1.0-SNAPSHOT.jar"]
    }
  }
}
```

调用 `index_project` 时传入项目根目录。首次索引之后，索引数据库和反编译源码默认保存到用户目录下的 `.java-code-intelligence/<project-id>/`。

面向 AI Agent 的启动、参数和调用流程见 [AI Agent 配置与调用指南](AI_AGENT_GUIDE.md)；完整 MCP 协议和 tool schema 见 [MCP_API.md](MCP_API.md)。

文档示例中的 `/path/...` 仅为虚拟路径，不代表真实机器目录。

## 配置

### `index_project` 请求参数

索引行为通过 MCP 工具参数配置，不需要修改配置文件：

```json
{
  "projectPath": "/path/projects/order-service",
  "targetJavaRelease": 8,
  "enforceTargetRelease": false,
  "changeDetection": "NONE",
  "includeTestDependencies": false,
  "force": false
}
```

- `targetJavaRelease` 默认是 `8`，可按被分析项目改为 `11`、`17` 或 `21`。
- `enforceTargetRelease=true` 时跳过高于目标版本的 class；否则保留并记录版本提示。
- `changeDetection` 默认是 `NONE`：不计算 JAR SHA-256，依赖变化需要显式 `force=true`。`METADATA` 比较文件大小和修改时间，`SHA256` 才会读取并保存完整 JAR 指纹。
- `includeTestDependencies=true` 时会把 Maven test scope 依赖加入待分析 JAR；默认只解析 runtime 依赖。

### Maven 进程配置

Maven 属于服务启动环境配置，不作为 MCP 请求参数传入。默认执行 PATH 中的 Maven（Windows 为 `mvn.cmd`，其他系统为 `mvn`），Maven 会继续使用项目 POM、用户 `settings.xml` 和本机仓库配置。需要指定 Maven 安装位置或超时时，可在启动 MCP 服务前设置：

```powershell
$env:JAVA_HOME = '/path/jdk-21'
$env:MAVEN_HOME = '/path/maven'
$env:JCI_MAVEN_TIMEOUT_SECONDS = '180'
java -jar .\target\java-code-intelligence-mcp-0.1.0-SNAPSHOT.jar
```

也可以直接指定可执行文件（优先级高于 `MAVEN_HOME` 和 PATH）：

```powershell
$env:JCI_MAVEN_COMMAND = '/path/maven/bin/mvn.cmd'
```

使用 Claude Code 启动时，也可将 Java 系统属性放在 `-jar` 前：

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
      ]
    }
  }
}
```

`JCI_MAVEN_COMMAND` / `-Djci.maven.command` 的优先级高于 `MAVEN_HOME`；`JCI_MAVEN_TIMEOUT_SECONDS` / `-Djci.maven.timeoutSeconds` 默认是 120 秒，必须是正整数。服务进程仍必须使用 JDK 21，`JAVA_HOME` 不会改变被分析项目声明的 `targetJavaRelease`。

### 多 Module 项目的无构建解析

索引多 Module 根目录时，服务会先执行 Maven 的 `help:effective-pom` 读取 Reactor 模型，再为每个 Module 生成位于系统临时目录的 external-only probe POM，最后只执行 `dependency:build-classpath` 收集第三方 JAR。Reactor 内部依赖会从 probe POM 中移除，因此不要求内部 Module 已经生成或安装 JAR，也不会执行目标项目的 `compile`、`package`、`install` 或测试生命周期。

因此，使用 `index_project` 前不需要先运行 Maven 打包。服务仍依赖本机 Maven 能够解析 effective POM、插件和外部依赖；如果 Maven 本身无法访问仓库、插件或认证镜像，结果会返回模块级诊断。临时 POM、classpath 输出和 Maven 日志不会写入被分析项目目录。

## 重要边界

服务不会加载被分析的 class，也不会执行静态初始化；调用图是静态字节码近似结果，不覆盖反射、Spring AOP 和运行时代理。详细工具输入输出见 [MCP_API.md](MCP_API.md)，设计和验收范围见 [PROJECT_OVERVIEW.md](PROJECT_OVERVIEW.md)。
