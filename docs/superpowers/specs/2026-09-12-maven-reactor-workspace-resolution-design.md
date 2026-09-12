# Maven Reactor Workspace Resolution Design

## 背景

当前依赖发现直接对根 POM 和子模块执行 `dependency:build-classpath`。当模块 A 依赖同一 Reactor 中尚未执行 `package` 或 `install` 的模块 B 时，Maven Dependency Plugin 会把 B 当成普通仓库制品解析，从而因本地/远程仓库中不存在内部 SNAPSHOT JAR 而失败。

Java Code Intelligence MCP 的使用前提必须是“打开项目即可索引”，不能要求用户预先执行 `mvn compile`、`mvn package` 或 `mvn install`。

## 目标

- 对 Maven 单模块和多模块项目使用同一入口。
- 根目录索引不依赖 Reactor 内部模块的 JAR 或 `target/classes`。
- 从所有 Reactor 模块收集完整的第三方编译期依赖；测试依赖仍由现有选项控制。
- 复用用户通过 PATH/MAVEN_HOME 选择的 Maven、settings、镜像、本地仓库和认证配置。
- Maven 模型解析失败时给出模块和命令阶段明确的错误；单个模块外部依赖解析失败但已有可用结果时允许部分成功并返回诊断。
- 不在被分析项目中写入临时 POM、classpath 文件或构建产物。

## 非目标

- 本次不执行 Maven 生命周期中的编译、测试、打包或安装阶段。
- 本次不实现完整 Java 源码 AST 索引。工作区源码本来即可由 AI Agent 读取；一期继续聚焦第三方二进制依赖。
- 本次不重新实现 Maven 的 parent、profile、dependencyManagement、BOM 和属性插值规则，而是读取 Maven 生成的 effective POM。
- 本次不自动执行生成源码插件。已存在的 generated-sources 可在后续源码索引阶段使用。

## 架构

### 1. Reactor 模型导入

`MavenProcessRunner` 在根 POM 上执行只读模型命令：

```text
mvn -q -f <root-pom> help:effective-pom -Doutput=<tool-temp-file>
```

该命令只建立 Maven 模型，不执行 `compile/package/install`。多模块输出的 `<projects>` 中每个 `<project>` 被解析为 `MavenModuleModel`：

- GAV 与 packaging
- POM/base directory
- main/test source directory
- output/test output directory
- 编译目标版本
- dependencies、dependencyManagement、repositories

原始 POM 中的 `<modules>` 仍由 `PomModuleDiscovery` 递归扫描，用来把 effective POM 项目按 GAV/目录稳定地映射回实际模块路径。

### 2. Workspace Resolution

全部 Reactor 模块坐标组成 workspace coordinate set。模块依赖按以下规则分类：

```text
dependency GAV 命中 workspace set -> WORKSPACE_MODULE
否则                         -> EXTERNAL_ARTIFACT
```

工作区依赖保存为模块关系，不要求对应 JAR 存在，也不交给外部制品解析器。

### 3. 外部依赖探测

对每个 effective module model 生成一个位于 MCP 临时目录的 probe POM。probe POM保留：

- effective dependencyManagement
- repositories
- 当前模块的外部 dependencies
- dependency 的 scope/type/classifier/exclusions/optional

probe POM删除所有 workspace dependencies。随后继续使用 Maven Dependency Plugin 构建外部 classpath。不同模块分别探测，以保留模块级 dependencyManagement 和同一制品不同版本的结果；最终按规范化绝对路径去重。

所有 probe POM 和命令输出都放在系统临时目录，并在解析完成后清理。被分析项目目录保持只读。

### 4. 结果与诊断

`DependencyProvider` 返回 `DependencyResolution`，包含：

- Reactor 模型
- 去重后的第三方 JAR
- warning/error diagnostics

`IndexCoordinator` 使用模型中的模块数量填充 `IndexSummary.modules`，只把第三方 JAR交给 ASM。Maven 进程非零退出时：

- classpath 中有有效 JAR：保留结果并记录 warning；
- 没有任何有效结果：该模块记录 error；
- 所有模块都无法解析：整体索引失败。

### 5. 缓存与变化

- JAR 缓存继续使用现有 `changeDetection` 选项，默认 `NONE`，不强制计算指纹。
- effective POM 与 probe POM 第一版只在单次索引期间存在，不永久缓存。
- 后续可按根/模块 POM 的修改时间缓存 Maven 模型，但不作为本次正确性要求。

## 安全与兼容性

- XML 解析禁用 DTD 和外部实体。
- 临时文件只写入 MCP 管理的临时目录。
- Maven 命令继续支持 Windows `.cmd/.bat`、自定义可执行文件与超时。
- 服务运行在 JDK 21；`targetJavaRelease` 默认 8，仍可按请求覆盖。
- 根目录下名为 `settings.xml` 的普通文件不会自动作为 Maven settings；Maven 使用其标准配置或用户显式配置。

## 验收

### 自动化 fixture

构造 parent + module-a + module-b：

- module-a 依赖 module-b 的 SNAPSHOT；
- module-a/module-b 分别依赖不同的外部测试 JAR；
- 本地仓库中不存在 module-b JAR；
- runner 返回聚合 effective POM。

断言：

- 两个子模块均被识别；
- module-b 被分类为 workspace dependency；
- probe POM 不含 module-b；
- 两个外部 JAR 被发现并去重；
- 未调用任何 Maven compile/package/install goal。

### XGD 实测

直接对 `/path/projects/sample-project` 根目录执行依赖解析：

- 不读取项目根目录伪 `settings.xml`；
- 不要求执行 Maven 打包或安装；
- 识别根项目及四个子模块；
- 内部 `com.inte:*:1.0-SNAPSHOT` 不进入外部 probe POM；
- 能发现 Maven 仓库中的第三方 JAR；
- 可选进行小范围索引查询，避免再次无控制地扫描全部 823 个 JAR。
