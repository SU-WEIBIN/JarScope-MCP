# Maven Reactor Workspace Resolution Implementation Plan

> **Implementation note:** This plan was executed inline in the current workspace because the user asked to preserve the existing working tree. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** 让 Maven 多模块项目无需预先执行 compile/package/install，即可从根目录解析并索引全部第三方依赖 JAR。

**Architecture:** 先通过 Maven effective POM 建立 Reactor 工作区模型，将内部依赖映射为 workspace module；再为每个模块生成移除内部依赖的临时 probe POM，由 Maven 只解析外部依赖。ASM 和 SQLite 索引流程保持聚焦第三方 JAR。

**Tech Stack:** Java 21、Maven Help/Dependency Plugin、DOM XML、ASM、SQLite、JUnit 5、AssertJ

**Spec:** `docs/superpowers/specs/2026-09-12-maven-reactor-workspace-resolution-design.md`

## 当前执行状态（2026-09-12）

| 工作项 | 状态 | 验证 |
|---|---|---|
| 递归发现根 POM 和嵌套 Module | 已完成 | `PomModuleDiscoveryTest` |
| Maven effective POM / classpath 命令封装 | 已完成 | `MavenCommandLineTest`、真实 XGD smoke |
| Reactor 模型和 workspace 依赖分类 | 已完成 | `MavenEffectivePomParserTest` |
| external-only probe POM | 已完成 | `MavenExternalProbePomTest` |
| 外部 JAR 合并、去重和部分诊断 | 已完成 | `MavenDependencyProviderTest` |
| IndexSummary / MCP 诊断输出 | 已完成 | `IndexSummaryTest`、完整 MCP 测试 |
| 文档、配置和验收标准 | 已完成 | `git diff --check` |
| 全量服务验证 | 已完成 | JDK 21 下 27 个测试通过 |
| XGD 依赖解析验收 | 已完成 | 5 个 Module、828 个 JAR、无 `MAVEN_MODULE_RESOLUTION_FAILED` |

“已完成”表示本轮代码已写入当前工作区；文档末尾的步骤保留为实现轨迹和后续维护清单。没有创建自动 Git commit，以免覆盖工作区原有未提交修改。

## Global Constraints

- 不对被分析项目执行 `mvn compile`、`mvn package`、`mvn install` 或 `mvn test`；本 MCP 工程自身仍需运行单元测试进行验证。
- 不要求 Reactor 内部模块已生成 JAR 或 `target/classes`。
- 不在被分析项目目录写入临时文件。
- 默认按 Java 8 目标版本解析，允许请求覆盖。
- JAR 指纹默认关闭，继续由 `changeDetection` 显式开启。
- 不使用子 Agent；在当前工作区内保留已有未提交改动。

---

### Task 1: 递归发现 Maven 模块

**Files:**
- Modify: `src/main/java/io/github/javacodeintel/mcp/project/PomModuleDiscovery.java`
- Modify: `src/test/java/io/github/javacodeintel/mcp/project/PomModuleDiscoveryTest.java`

**Interfaces:**
- Consumes: 根 `pom.xml` 的规范化绝对路径。
- Produces: `List<Path> discover(Path rootPom)`，按深度优先、POM 声明顺序返回所有后代模块，且不包含重复项。

- [x] **Step 1: 写递归嵌套模块和循环/重复引用的失败测试**

```java
assertThat(new PomModuleDiscovery().discover(rootPom))
    .containsExactly(moduleAPom, nestedModulePom);
```

- [x] **Step 2: 运行测试并确认只返回直接子模块导致失败**

Run: `$env:JAVA_HOME='/path/jdk-21'; mvn -o -Dtest=PomModuleDiscoveryTest test`

Expected: FAIL，缺少 nested module。

- [x] **Step 3: 用 visited set 实现安全递归**

```java
private void discover(Path pom, Path projectRoot, Set<Path> visited, List<Path> result)
```

所有模块路径必须保持在根项目目录内；缺失模块 POM 产生 `IOException`，而不是静默忽略。

- [x] **Step 4: 运行定向测试并确认通过**

Run: `$env:JAVA_HOME='/path/jdk-21'; mvn -o -Dtest=PomModuleDiscoveryTest test`

### Task 2: 扩展 Maven 命令执行边界

**Files:**
- Modify: `src/main/java/io/github/javacodeintel/mcp/project/MavenProcessRunner.java`
- Create: `src/test/java/io/github/javacodeintel/mcp/project/MavenProcessRunnerTest.java`

**Interfaces:**
- Produces: `Result effectivePom(Path rootPom)`。
- Produces: `Result buildClasspath(Path probePom, boolean includeTestDependencies)`。
- Preserves: `MavenRuntimeOptions` 的 executable 和 timeout 配置。

- [x] **Step 1: 写 fake Maven executable 测试，捕获参数并写出指定 output 文件**

测试断言 effective model 命令只包含 `help:effective-pom`，classpath 命令只包含 `dependency:build-classpath`，且临时输出不位于 fixture project 下。

- [x] **Step 2: 运行测试并确认缺少新接口而失败**

Run: `$env:JAVA_HOME='/path/jdk-21'; mvn -o -Dtest=MavenProcessRunnerTest test`

- [x] **Step 3: 提取通用 Maven 进程执行方法**

```java
Result effectivePom(Path pom) throws IOException;
Result buildClasspath(Path pom, boolean includeTests) throws IOException;
```

生产 runner 为每次命令创建独立临时输出、stdout 和 stderr 文件，始终在 finally 中删除。

- [x] **Step 4: 运行定向测试并确认通过**

Run: `$env:JAVA_HOME='/path/jdk-21'; mvn -o -Dtest=MavenProcessRunnerTest test`

### Task 3: 解析 effective POM Reactor 模型

**Files:**
- Create: `src/main/java/io/github/javacodeintel/mcp/domain/MavenDependency.java`
- Create: `src/main/java/io/github/javacodeintel/mcp/domain/MavenModuleModel.java`
- Create: `src/main/java/io/github/javacodeintel/mcp/domain/MavenReactorModel.java`
- Create: `src/main/java/io/github/javacodeintel/mcp/project/MavenEffectivePomParser.java`
- Create: `src/test/java/io/github/javacodeintel/mcp/project/MavenEffectivePomParserTest.java`

**Interfaces:**
- Produces: `MavenReactorModel parse(String xml, Path rootPom, List<Path> modulePoms)`。
- `MavenReactorModel.workspaceCoordinates()` 用于识别内部依赖。
- `MavenModuleModel` 持有 GAV、POM 路径、packaging、依赖及保留的 effective XML 节点数据。

- [x] **Step 1: 写聚合 `<projects>` fixture 测试**

覆盖 parent、两个模块、workspace dependency、外部 dependency、scope、classifier、optional 和默认 source/output 目录。

- [x] **Step 2: 运行测试并确认 parser 不存在而失败**

Run: `$env:JAVA_HOME='/path/jdk-21'; mvn -o -Dtest=MavenEffectivePomParserTest test`

- [x] **Step 3: 实现禁用外部实体的 DOM 解析和模块路径映射**

```java
public MavenReactorModel parse(
    String effectivePomXml,
    Path rootPom,
    List<Path> discoveredModulePoms)
```

聚合输出和单 `<project>` 输出都必须支持；模块映射优先使用 artifactId/GAV，再以 effective `<basedir>`/build directory 归一化校验。

- [x] **Step 4: 运行定向测试并确认通过**

Run: `$env:JAVA_HOME='/path/jdk-21'; mvn -o -Dtest=MavenEffectivePomParserTest test`

### Task 4: 生成 external-only probe POM

**Files:**
- Create: `src/main/java/io/github/javacodeintel/mcp/project/MavenExternalProbePom.java`
- Create: `src/test/java/io/github/javacodeintel/mcp/project/MavenExternalProbePomTest.java`

**Interfaces:**
- Consumes: `MavenModuleModel` 与 `Set<MavenCoordinate> workspaceCoordinates`。
- Produces: `String render(...)`，供临时文件写入后执行 `dependency:build-classpath`。

- [x] **Step 1: 写行为测试**

断言 probe POM 删除 reactor dependency，保留外部依赖的 version/scope/type/classifier/optional/exclusions，并在 `includeTestDependencies=false` 时删除 test scope。

- [x] **Step 2: 运行测试并确认 generator 不存在而失败**

Run: `$env:JAVA_HOME='/path/jdk-21'; mvn -o -Dtest=MavenExternalProbePomTest test`

- [x] **Step 3: 使用 DOM 构建并安全序列化 probe POM**

```java
String render(MavenModuleModel module,
              Set<MavenCoordinate> workspace,
              boolean includeTests)
```

不要用字符串替换原 POM；只复制受支持的 dependencyManagement、repositories 和 dependencies 元素。

- [x] **Step 4: 运行定向测试并确认通过**

Run: `$env:JAVA_HOME='/path/jdk-21'; mvn -o -Dtest=MavenExternalProbePomTest test`

### Task 5: Reactor-aware dependency provider

**Files:**
- Create: `src/main/java/io/github/javacodeintel/mcp/domain/DependencyResolution.java`
- Modify: `src/main/java/io/github/javacodeintel/mcp/ports/DependencyProvider.java`
- Modify: `src/main/java/io/github/javacodeintel/mcp/project/MavenDependencyProvider.java`
- Modify: `src/test/java/io/github/javacodeintel/mcp/project/MavenDependencyProviderTest.java`

**Interfaces:**
- Changes: `DependencyProvider.resolve(Path, ProjectOptions)` 返回 `DependencyResolution`。
- `DependencyResolution` 包含 `MavenReactorModel reactor`、`List<JarArtifact> jars`、`List<IndexDiagnostic> diagnostics`。

- [x] **Step 1: 写多模块无内部 JAR的失败测试**

fake runner 返回聚合 effective POM，并验证收到的 probe POM 中不存在内部 SNAPSHOT；两个模块的外部 classpath 合并且按绝对路径去重。

- [x] **Step 2: 写部分成功失败测试**

一个 probe 返回非零退出但含有效 classpath，断言保留 JAR并产生 warning；所有 probe 都无有效 JAR时断言抛出 `IOException`。

- [x] **Step 3: 运行测试并确认旧 provider 在内部依赖处失败**

Run: `$env:JAVA_HOME='/path/jdk-21'; mvn -o -Dtest=MavenDependencyProviderTest test`

- [x] **Step 4: 实现 effective-model -> probe -> external classpath 流程**

临时 probe 目录使用 `Files.createTempDirectory("jci-maven-probe-")`，在 finally 中只删除该已验证目录。

- [x] **Step 5: 运行定向测试并确认通过**

Run: `$env:JAVA_HOME='/path/jdk-21'; mvn -o -Dtest=MavenDependencyProviderTest test`

### Task 6: 将模块数和诊断接入索引/MCP

**Files:**
- Modify: `src/main/java/io/github/javacodeintel/mcp/project/IndexCoordinator.java`
- Modify: `src/main/java/io/github/javacodeintel/mcp/domain/IndexSummary.java`
- Modify: `src/main/java/io/github/javacodeintel/mcp/mcp/ToolHandlers.java`
- Modify: `src/test/java/io/github/javacodeintel/mcp/mcp/ToolHandlersTest.java`
- Modify: `src/test/java/io/github/javacodeintel/mcp/acceptance/JavaCodeIntelligenceAcceptanceTest.java`

**Interfaces:**
- `IndexSummary.modules` 等于 Reactor model 的项目数量。
- `IndexSummary` 增加只读 `diagnostics` 列表，并保留已有便利构造函数。

- [x] **Step 1: 写 summary 模块数和 warning 序列化失败测试**

- [x] **Step 2: 运行定向测试并确认 modules 仍为 0 而失败**

Run: `$env:JAVA_HOME='/path/jdk-21'; mvn -o -Dtest=ToolHandlersTest,JavaCodeIntelligenceAcceptanceTest test`

- [x] **Step 3: 修改 coordinator 和 handler**

索引文案包含模块数、发现/新增/复用 JAR数和诊断数量；diagnostics 放入结构化 result，而不是只拼接文本。

- [x] **Step 4: 运行定向测试并确认通过**

Run: `$env:JAVA_HOME='/path/jdk-21'; mvn -o -Dtest=ToolHandlersTest,JavaCodeIntelligenceAcceptanceTest test`

### Task 7: 更新公开文档

**Files:**
- Modify: `PROJECT_OVERVIEW.md`
- Modify: `MCP_API.md`
- Modify: `README.md`

**Interfaces:**
- Documents: 多模块索引默认无需构建、Maven 只运行模型/依赖解析 goal、已知 generated-sources 限制。

- [x] **Step 1: 删除“需要先 install 或指向子模块”的旧限制**

- [x] **Step 2: 补充无构建 Reactor 数据流、临时文件位置和错误语义**

- [x] **Step 3: 运行 Markdown/差异检查**

Run: `git diff --check`

### Task 8: 全量验证和 XGD 验收

**Files:**
- Test only; production source不再修改。

**Interfaces:**
- Verifies: 自动测试与真实多模块项目均不要求构建。

- [x] **Step 1: JDK 21 下运行全量测试**

Run: `$env:JAVA_HOME='/path/jdk-21'; mvn -o verify`

Expected: 全部测试通过。

- [x] **Step 2: 对 XGD 只运行 dependency resolution smoke test**

直接从 `/path/projects/sample-project` 根 POM建立 effective model 和 external classpath，不执行完整 823 JAR ASM 扫描。

Expected: 识别 5 个项目，返回非空第三方 JAR列表，内部 `com.inte` SNAPSHOT 不导致失败。

- [x] **Step 3: 检查命令审计和项目目录变化**

确认日志中不存在 compile/package/install/test goal，并确认 `/path/projects/sample-project` 没有新增 JCI 临时文件。

- [x] **Step 4: 最终差异检查**

Run: `git status --short`

Run: `git diff --check`

## 执行说明

用户已要求在当前会话直接开始修改，因此采用 inline execution。当前工作区已有未提交改动，为避免混入或重写用户状态，本计划不自动创建 Git commit；完成后提供精确文件清单和验证结果。
