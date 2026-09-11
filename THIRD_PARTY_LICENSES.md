# Third-party licenses

运行时依赖及其许可证以 Maven POM 为准。项目当前直接依赖：

| 组件 | 版本 | 用途 | 许可证 |
|---|---:|---|---|
| Model Context Protocol Java SDK | 2.0.0 | MCP stdio transport/server | MIT |
| ASM | 9.10.1 | JVM class 文件解析 | BSD-3-Clause |
| Xerial SQLite JDBC | 3.53.4.0 | SQLite 存储 | Apache-2.0 / MIT |
| Vineflower | 1.12.0 | Java 反编译 | Apache-2.0 |
| JUnit Jupiter | 5.12.2 | 测试（test scope） | EPL-2.0 |
| AssertJ | 3.27.3 | 测试断言（test scope） | Apache-2.0 |

发布 shaded JAR 时应保留依赖包中的 LICENSE/NOTICE 文件；Maven Shade 配置会合并服务发现资源。
