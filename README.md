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
$env:JAVA_HOME = 'C:\path\to\jdk-21'
mvn clean verify
```

产物位于 `target/java-code-intelligence-mcp-0.1.0-SNAPSHOT.jar`，是包含依赖的可执行 shaded JAR。

## Claude Code 配置示例

```json
{
  "mcpServers": {
    "java-code-intelligence": {
      "command": "java",
      "args": ["-jar", "E:/tools/java-code-intelligence-mcp-0.1.0-SNAPSHOT.jar"]
    }
  }
}
```

调用 `index_project` 时传入项目根目录。首次索引之后，索引数据库和反编译源码默认保存到用户目录下的 `.java-code-intelligence/<project-id>/`。

## 重要边界

服务不会加载被分析的 class，也不会执行静态初始化；调用图是静态字节码近似结果，不覆盖反射、Spring AOP 和运行时代理。详细工具输入输出见 [MCP_API.md](MCP_API.md)，设计和验收范围见 [PROJECT_OVERVIEW.md](PROJECT_OVERVIEW.md)。
