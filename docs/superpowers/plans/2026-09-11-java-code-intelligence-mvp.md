# Java Code Intelligence MCP MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a JDK 21 stdio MCP server that indexes Maven dependency JARs, persists Java bytecode semantics in SQLite, answers class/member/relation queries, and decompiles classes on demand.

**Architecture:** A ports-and-adapters design separates project dependency discovery, ASM parsing, SQLite persistence, query services, decompilation, and MCP transport. Shared immutable domain records are established first; independent adapters are then implemented in isolated worktrees and merged before orchestration and MCP integration.

**Tech Stack:** Java 21, Maven, MCP Java SDK 2.0.0, ASM 9.10.1, sqlite-jdbc 3.53.4.0, Vineflower 1.12.0, JUnit Jupiter 5.12.2, AssertJ 3.27.3.

**Spec:** `PROJECT_OVERVIEW.md` and `MCP_API.md`

## Global Constraints

- The MCP service runtime is JDK 21.
- The analyzed project defaults to `targetJavaRelease=8` and accepts configurable later releases.
- `changeDetection=none` is the default and MUST NOT compute SHA-256.
- SHA-256 is computed only when `changeDetection=sha256`.
- `force=true` rebuilds the project index and invalidates related decompilation cache entries.
- Phase one transport is stdio; stdout contains MCP protocol messages only and diagnostics go to stderr.
- Phase one analyzes Maven runtime dependencies and excludes test dependencies by default.
- Call graph results are static bytecode facts or labeled candidates, never represented as a complete runtime call graph.
- Every production behavior is developed test-first with recorded RED and GREEN evidence.
- Fixture Java sources are compiled with `javac --release 8` when testing Java 8 behavior.
- Package root is `io.github.javacodeintel.mcp`.
- Agents working concurrently must use separate worktrees and disjoint owned files.
- Dependency versions are pinned; no dynamic or `LATEST` versions are allowed.
- Phase one does not add Java source AST, JDT, IDE editing, Spring runtime analysis, HTTP, or SSE.

## File Structure

```text
pom.xml
README.md
.gitignore
PROJECT_OVERVIEW.md
MCP_API.md
docs/superpowers/plans/2026-09-11-java-code-intelligence-mvp.md
src/main/java/io/github/javacodeintel/mcp/Main.java
src/main/java/io/github/javacodeintel/mcp/config/ProjectOptions.java
src/main/java/io/github/javacodeintel/mcp/domain/*.java
src/main/java/io/github/javacodeintel/mcp/ports/*.java
src/main/java/io/github/javacodeintel/mcp/project/*.java
src/main/java/io/github/javacodeintel/mcp/index/*.java
src/main/java/io/github/javacodeintel/mcp/storage/*.java
src/main/java/io/github/javacodeintel/mcp/query/*.java
src/main/java/io/github/javacodeintel/mcp/decompile/*.java
src/main/java/io/github/javacodeintel/mcp/mcp/*.java
src/main/resources/db/migration/V1__initial.sql
src/test/java/io/github/javacodeintel/mcp/**/*.java
src/test/resources/fixtures/maven-project/pom.xml
scripts/run.ps1
scripts/run.sh
```

## Parallel Execution Waves

- Foundation: Task 1 runs alone.
- Wave A: Tasks 2, 3, and 4 run concurrently in separate worktrees.
- Integration checkpoint: merge Wave A and run the full suite.
- Wave B: Tasks 5, 6, and 7 run concurrently in separate worktrees.
- Final integration: Tasks 8 and 9 run sequentially.

---

### Task 1: Build Foundation and Shared Contracts

**Files:**
- Create: `pom.xml`
- Create: `.gitignore`
- Create: `src/main/java/io/github/javacodeintel/mcp/config/ProjectOptions.java`
- Create: `src/main/java/io/github/javacodeintel/mcp/domain/ChangeDetectionMode.java`
- Create: `src/main/java/io/github/javacodeintel/mcp/domain/ResolutionKind.java`
- Create: `src/main/java/io/github/javacodeintel/mcp/domain/MavenCoordinate.java`
- Create: `src/main/java/io/github/javacodeintel/mcp/domain/JarArtifact.java`
- Create: `src/main/java/io/github/javacodeintel/mcp/domain/AnnotationInfo.java`
- Create: `src/main/java/io/github/javacodeintel/mcp/domain/FieldInfo.java`
- Create: `src/main/java/io/github/javacodeintel/mcp/domain/MethodInfo.java`
- Create: `src/main/java/io/github/javacodeintel/mcp/domain/ClassInfo.java`
- Create: `src/main/java/io/github/javacodeintel/mcp/domain/CallEdge.java`
- Create: `src/main/java/io/github/javacodeintel/mcp/domain/ParsedClass.java`
- Create: `src/main/java/io/github/javacodeintel/mcp/domain/IndexDiagnostic.java`
- Create: `src/main/java/io/github/javacodeintel/mcp/domain/ScanResult.java`
- Create: `src/main/java/io/github/javacodeintel/mcp/domain/IndexSummary.java`
- Create: `src/main/java/io/github/javacodeintel/mcp/domain/DecompileCacheKey.java`
- Create: `src/main/java/io/github/javacodeintel/mcp/domain/DecompiledSource.java`
- Create: `src/main/java/io/github/javacodeintel/mcp/ports/BytecodeParser.java`
- Create: `src/main/java/io/github/javacodeintel/mcp/ports/DependencyProvider.java`
- Create: `src/main/java/io/github/javacodeintel/mcp/ports/IndexStore.java`
- Create: `src/main/java/io/github/javacodeintel/mcp/ports/ClassDecompiler.java`
- Test: `src/test/java/io/github/javacodeintel/mcp/config/ProjectOptionsTest.java`

**Interfaces:**
- Produces: `ProjectOptions.defaults()` returning target release 8, non-strict target enforcement, change detection NONE, and test dependencies disabled.
- Produces: immutable domain records used by every later task.
- Produces: ports that storage, ASM, Maven, and decompiler adapters implement.

- [ ] **Step 1: Write the failing defaults test**

```java
@Test
void defaultsTargetJavaEightWithoutJarHashing() {
    ProjectOptions options = ProjectOptions.defaults();

    assertThat(options.targetJavaRelease()).isEqualTo(8);
    assertThat(options.enforceTargetRelease()).isFalse();
    assertThat(options.changeDetection()).isEqualTo(ChangeDetectionMode.NONE);
    assertThat(options.includeTestDependencies()).isFalse();
}
```

- [ ] **Step 2: Run the focused test and verify RED**

Run:

```powershell
$env:JAVA_HOME='/path/jdk-21'
mvn -Dtest=ProjectOptionsTest test
```

Expected: FAIL because `ProjectOptions` and `ChangeDetectionMode` do not exist.

- [ ] **Step 3: Add the pinned Maven build**

Use Java release 21 and these versions:

```xml
<properties>
  <maven.compiler.release>21</maven.compiler.release>
  <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
  <mcp.version>2.0.0</mcp.version>
  <asm.version>9.10.1</asm.version>
  <sqlite.version>3.53.4.0</sqlite.version>
  <vineflower.version>1.12.0</vineflower.version>
  <junit.version>5.12.2</junit.version>
  <assertj.version>3.27.3</assertj.version>
</properties>
```

Configure compiler plugin 3.13.0, surefire 3.5.2, and shade plugin 3.6.0 with a services-resource transformer and `io.github.javacodeintel.mcp.Main` as the executable main class.

- [ ] **Step 4: Implement ProjectOptions and enums**

```java
public record ProjectOptions(
        int targetJavaRelease,
        boolean enforceTargetRelease,
        ChangeDetectionMode changeDetection,
        boolean includeTestDependencies) {

    public ProjectOptions {
        if (targetJavaRelease < 8 || targetJavaRelease > Runtime.version().feature()) {
            throw new IllegalArgumentException("targetJavaRelease must be between 8 and "
                    + Runtime.version().feature());
        }
        Objects.requireNonNull(changeDetection, "changeDetection");
    }

    public static ProjectOptions defaults() {
        return new ProjectOptions(8, false, ChangeDetectionMode.NONE, false);
    }
}
```

- [ ] **Step 5: Add immutable domain records and ports**

Use Java records. `MethodInfo` includes owner, name, descriptor, return type, parameters, exceptions, access flags, generic signature, and annotations. `ParsedClass` contains one `ClassInfo` plus fields, methods, and call edges. `JarArtifact` contains path and Maven coordinate without eagerly calculating any hash.

Required port signatures:

```java
public interface BytecodeParser {
    ParsedClass parse(byte[] bytecode, JarArtifact source);
}

public interface DependencyProvider {
    List<JarArtifact> resolve(Path projectPath, ProjectOptions options) throws IOException;
}

public interface ClassDecompiler {
    DecompiledSource decompile(
            Path projectPath,
            JarArtifact jar,
            String className,
            int targetJavaRelease,
            boolean force) throws IOException;
}
```

`IndexStore` uses these exact signatures so parallel modules share one contract:

```java
public interface IndexStore extends AutoCloseable {
    void initialize() throws IOException;
    boolean containsJar(JarArtifact jar) throws IOException;
    void replaceJarIndex(JarArtifact jar, List<ParsedClass> classes) throws IOException;
    void removeJar(JarArtifact jar) throws IOException;

    List<ClassInfo> searchClasses(
            String query, String packagePrefix, String kind, int limit, int offset) throws IOException;
    long countClasses(String query, String packagePrefix, String kind) throws IOException;
    Optional<ClassInfo> findClass(String fullyQualifiedName) throws IOException;
    List<MethodInfo> findMethods(
            String owner, String name, String descriptor) throws IOException;
    List<FieldInfo> findFields(String owner, String fieldName) throws IOException;
    List<ClassInfo> findSubtypes(
            String typeName, boolean recursive, boolean includeInterfaces,
            int limit, int offset) throws IOException;
    long countSubtypes(String typeName, boolean recursive, boolean includeInterfaces)
            throws IOException;
    List<CallEdge> findCallers(
            String owner, String name, String descriptor, boolean includePolymorphic,
            int limit, int offset) throws IOException;
    long countCallers(
            String owner, String name, String descriptor, boolean includePolymorphic)
            throws IOException;

    Optional<DecompiledSource> findDecompiled(DecompileCacheKey key) throws IOException;
    void saveDecompiled(DecompileCacheKey key, DecompiledSource source) throws IOException;
    void invalidateDecompiledForJar(JarArtifact jar) throws IOException;
    IndexSummary summary() throws IOException;
}
```

`ScanResult` is `record ScanResult(List<ParsedClass> classes, List<IndexDiagnostic> diagnostics)` and `IndexDiagnostic` stores code, message, severity, JAR entry, and retryable. `DecompileCacheKey` stores jar identity, class entry, target Java release, and decompiler version.

- [ ] **Step 6: Run tests and verify GREEN**

Run `mvn test` with JDK 21.

Expected: 1 test passes, build exits 0, and output has no warnings caused by project code.

- [ ] **Step 7: Commit**

```bash
git add pom.xml .gitignore src
git commit -m "build: establish Java code intelligence foundation"
```

---

### Task 2: SQLite Schema and Index Store

**Files:**
- Create: `src/main/resources/db/migration/V1__initial.sql`
- Create: `src/main/java/io/github/javacodeintel/mcp/storage/SchemaMigrator.java`
- Create: `src/main/java/io/github/javacodeintel/mcp/storage/SqliteIndexStore.java`
- Create: `src/main/java/io/github/javacodeintel/mcp/storage/SqlValueCodec.java`
- Test: `src/test/java/io/github/javacodeintel/mcp/storage/SqliteIndexStoreTest.java`

**Interfaces:**
- Consumes: domain records and `IndexStore` from Task 1.
- Produces: `SqliteIndexStore.open(Path databasePath)`.
- Produces: atomic `replaceJarIndex` behavior and query methods used by Tasks 5 and 6.

- [ ] **Step 1: Write a failing persistence test**

Create a temporary SQLite file, store one JAR containing one class, one field, one method, and one call edge, close the store, reopen it, and assert every value is queryable.

- [ ] **Step 2: Verify RED**

Run `mvn -Dtest=SqliteIndexStoreTest test`.

Expected: FAIL because `SqliteIndexStore` does not exist.

- [ ] **Step 3: Create schema migration**

Create the exact tables defined in `PROJECT_OVERVIEW.md`: projects, jars, classes, class_interfaces, annotations, fields, methods, method_parameters, method_exceptions, calls, decompile_cache, index_errors, and schema_version. Add indexes for class fully-qualified name, class simple name, method owner/name/descriptor, subtype parent/interface, and call target owner/name/descriptor.

- [ ] **Step 4: Implement transactional persistence**

Open with `jdbc:sqlite:<absolute-path>`, set `PRAGMA foreign_keys=ON`, `PRAGMA journal_mode=WAL`, and `PRAGMA busy_timeout=5000`. `replaceJarIndex` deletes the old rows for that JAR and inserts the complete new model inside one transaction; rollback on any failure.

- [ ] **Step 5: Implement real query behavior**

Use prepared statements only. Return empty lists for valid no-match queries. Preserve overloads by descriptor. Store annotation values as JSON text through `SqlValueCodec`.

- [ ] **Step 6: Verify GREEN**

Run the focused test, then `mvn test`.

Expected: persistent round-trip passes and the full suite exits 0.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/io/github/javacodeintel/mcp/storage src/main/resources/db src/test/java/io/github/javacodeintel/mcp/storage
git commit -m "feat: persist bytecode index in SQLite"
```

---

### Task 3: ASM Bytecode and JAR Indexing

**Files:**
- Create: `src/main/java/io/github/javacodeintel/mcp/index/AsmBytecodeParser.java`
- Create: `src/main/java/io/github/javacodeintel/mcp/index/AnnotationCollector.java`
- Create: `src/main/java/io/github/javacodeintel/mcp/index/JarClassScanner.java`
- Create: `src/main/java/io/github/javacodeintel/mcp/index/MultiReleaseClassSelector.java`
- Create: `src/test/java/io/github/javacodeintel/mcp/testing/JavaFixtureCompiler.java`
- Test: `src/test/java/io/github/javacodeintel/mcp/index/AsmBytecodeParserTest.java`
- Test: `src/test/java/io/github/javacodeintel/mcp/index/JarClassScannerTest.java`
- Test: `src/test/java/io/github/javacodeintel/mcp/index/MultiReleaseClassSelectorTest.java`

**Interfaces:**
- Consumes: `BytecodeParser`, `ParsedClass`, and `JarArtifact` from Task 1.
- Produces: `AsmBytecodeParser` and `JarClassScanner.scan(JarArtifact, ProjectOptions)`.

- [ ] **Step 1: Write failing Java 8 fixture tests**

Compile literal fixture source with the system compiler using `--release 8`. Assert class kind, parent, interface, field type, overloaded method descriptors, annotations, and a direct `invokeinterface` call edge.

- [ ] **Step 2: Verify RED**

Run `mvn -Dtest=AsmBytecodeParserTest test`.

Expected: FAIL because the ASM parser does not exist.

- [ ] **Step 3: Implement ASM visitors**

Use `ClassReader.accept(visitor, ClassReader.SKIP_FRAMES)`. Parse JVM descriptors with `Type`. Capture class, field, method, parameter, exception, and annotation metadata. Capture method instructions from `visitMethodInsn` and label them `DIRECT`. Capture invokedynamic bootstrap handles as `UNRESOLVED` edges rather than inventing runtime targets.

- [ ] **Step 4: Implement JAR entry scanning**

Read ZIP entries without loading the full JAR in memory. Continue after a single invalid class and return diagnostics for failed entries. Do not execute class initializers or load analyzed classes into the service JVM.

- [ ] **Step 5: Implement multi-release selection**

For target release 8, use the base class. For a configured release N, select the highest `META-INF/versions/V` entry where `V <= N`. Ensure one logical class is indexed once.

- [ ] **Step 6: Verify GREEN**

Run the three focused tests, then `mvn test`.

Expected: fixture metadata and call edges match hand-written literals; all tests pass.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/io/github/javacodeintel/mcp/index src/test/java/io/github/javacodeintel/mcp/index src/test/java/io/github/javacodeintel/mcp/testing
git commit -m "feat: index Java bytecode with ASM"
```

---

### Task 4: Maven Dependency Discovery

**Files:**
- Create: `src/main/java/io/github/javacodeintel/mcp/project/MavenDependencyProvider.java`
- Create: `src/main/java/io/github/javacodeintel/mcp/project/MavenProcessRunner.java`
- Create: `src/main/java/io/github/javacodeintel/mcp/project/PomModuleDiscovery.java`
- Create: `src/main/java/io/github/javacodeintel/mcp/project/MavenClasspathParser.java`
- Test: `src/test/java/io/github/javacodeintel/mcp/project/PomModuleDiscoveryTest.java`
- Test: `src/test/java/io/github/javacodeintel/mcp/project/MavenClasspathParserTest.java`
- Test: `src/test/java/io/github/javacodeintel/mcp/project/MavenDependencyProviderTest.java`
- Create: `src/test/resources/fixtures/maven-project/pom.xml`
- Create: `src/test/resources/fixtures/maven-project/module-a/pom.xml`
- Create: `src/test/resources/fixtures/maven-project/module-b/pom.xml`

**Interfaces:**
- Consumes: `DependencyProvider`, `JarArtifact`, and `ProjectOptions` from Task 1.
- Produces: Maven runtime JAR discovery without shell string interpolation.

- [ ] **Step 1: Write failing module and classpath tests**

Assert a root POM with module-a and module-b returns both module POM paths in declared order. Assert Windows semicolon and Unix colon classpaths are parsed into normalized, deduplicated JAR paths without losing drive letters.

- [ ] **Step 2: Verify RED**

Run `mvn -Dtest=PomModuleDiscoveryTest,MavenClasspathParserTest test`.

Expected: FAIL because discovery classes do not exist.

- [ ] **Step 3: Implement safe POM module discovery**

Use the JDK XML parser with external entity and DTD loading disabled. Resolve module paths under the root and reject module paths that escape the project root.

- [ ] **Step 4: Implement Maven process boundary**

Use `ProcessBuilder(List<String>)` with fixed Maven arguments. Never call a shell. Invoke the dependency plugin for each module, write classpath output to a temporary file, capture stderr separately, impose a configurable timeout, and delete only the known temporary file.

- [ ] **Step 5: Implement coordinate inference**

Infer groupId, artifactId, version, and classifier from Maven repository layout when possible. Leave unknown coordinate fields null instead of guessing.

- [ ] **Step 6: Verify GREEN**

Run the focused tests, then `mvn test`.

Expected: module discovery, classpath parsing, and fake process-runner boundary tests pass without network access.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/io/github/javacodeintel/mcp/project src/test/java/io/github/javacodeintel/mcp/project src/test/resources/fixtures/maven-project
git commit -m "feat: discover Maven dependency jars"
```

---

### Task 5: Index Orchestration and Change Policies

**Files:**
- Create: `src/main/java/io/github/javacodeintel/mcp/project/ProjectCacheLayout.java`
- Create: `src/main/java/io/github/javacodeintel/mcp/project/JarChangeDetector.java`
- Create: `src/main/java/io/github/javacodeintel/mcp/project/IndexCoordinator.java`
- Create: `src/main/java/io/github/javacodeintel/mcp/project/ProjectWriteLock.java`
- Test: `src/test/java/io/github/javacodeintel/mcp/project/JarChangeDetectorTest.java`
- Test: `src/test/java/io/github/javacodeintel/mcp/project/IndexCoordinatorTest.java`

**Interfaces:**
- Consumes: dependency provider, JAR scanner, and index store from Tasks 2-4.
- Produces: `IndexCoordinator.index(Path, ProjectOptions, boolean force)`.
- Produces: status values indexed, reused, partial, failed, and in_progress.

- [ ] **Step 1: Write failing change-policy tests**

Prove NONE reuses stored state without opening or hashing a JAR, METADATA compares normalized path/size/mtime, SHA256 reads content, and force always rebuilds.

- [ ] **Step 2: Verify RED**

Run `mvn -Dtest=JarChangeDetectorTest,IndexCoordinatorTest test`.

Expected: FAIL because orchestration does not exist.

- [ ] **Step 3: Implement cache layout and lock**

Default cache root is user-home `.java-code-intelligence`. Normalize project paths before deriving project IDs. Use a project-scoped lock so one process cannot start a second write transaction.

- [ ] **Step 4: Implement detector strategies**

Do not calculate SHA-256 in NONE or METADATA. Calculate it lazily and only in SHA256. Return an explicit reason for reuse or rebuild.

- [ ] **Step 5: Implement coordinator**

Resolve JARs, decide changes, scan only selected JARs, transactionally replace their data, retain diagnostics, and invalidate matching decompilation cache entries on force or rebuild.

- [ ] **Step 6: Verify GREEN**

Run focused tests and `mvn test`.

Expected: spy input streams prove hashing is absent by default; force and each strategy behave as specified.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/io/github/javacodeintel/mcp/project src/test/java/io/github/javacodeintel/mcp/project
git commit -m "feat: coordinate persistent project indexing"
```

---

### Task 6: Query Services

**Files:**
- Create: `src/main/java/io/github/javacodeintel/mcp/query/ClassQueryService.java`
- Create: `src/main/java/io/github/javacodeintel/mcp/query/MethodQueryService.java`
- Create: `src/main/java/io/github/javacodeintel/mcp/query/FieldQueryService.java`
- Create: `src/main/java/io/github/javacodeintel/mcp/query/RelationQueryService.java`
- Create: `src/main/java/io/github/javacodeintel/mcp/query/PageRequest.java`
- Create: `src/main/java/io/github/javacodeintel/mcp/query/PageResult.java`
- Test: `src/test/java/io/github/javacodeintel/mcp/query/QueryServicesTest.java`

**Interfaces:**
- Consumes: read operations from `IndexStore`.
- Produces: exact business payloads described by `MCP_API.md`.
- Produces: default limit 50 and maximum limit 500.

- [ ] **Step 1: Write failing behavior tests**

Test full-name precedence, ambiguous simple names returning candidates, method overload preservation, fields with inferred accessors, recursive subtypes, exact callers, polymorphic candidate labeling, and pagination truncation.

- [ ] **Step 2: Verify RED**

Run `mvn -Dtest=QueryServicesTest test`.

Expected: FAIL because query services do not exist.

- [ ] **Step 3: Implement query services**

Keep SQL in the storage adapter. Query services validate user input, apply defaults, assemble domain results, and never silently choose one ambiguous class.

- [ ] **Step 4: Implement paging**

Reject negative offsets, reject limits above 500, default to 50, and populate returned, total, truncated, and nextOffset.

- [ ] **Step 5: Verify GREEN**

Run focused tests and `mvn test`.

Expected: all query semantics match literal fixtures and overload descriptors remain intact.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/io/github/javacodeintel/mcp/query src/test/java/io/github/javacodeintel/mcp/query
git commit -m "feat: add bytecode intelligence queries"
```

---

### Task 7: Vineflower Decompilation Cache

**Files:**
- Create: `src/main/java/io/github/javacodeintel/mcp/decompile/VineflowerDecompiler.java`
- Create: `src/main/java/io/github/javacodeintel/mcp/decompile/DecompileCache.java`
- Create: `src/main/java/io/github/javacodeintel/mcp/decompile/DecompileResultSaver.java`
- Test: `src/test/java/io/github/javacodeintel/mcp/decompile/VineflowerDecompilerTest.java`
- Test: `src/test/java/io/github/javacodeintel/mcp/decompile/DecompileCacheTest.java`

**Interfaces:**
- Consumes: `ClassDecompiler`, `JarArtifact`, `DecompiledSource`, and store cache methods.
- Produces: class-level decompilation cached by jar ID, class entry, target release, and pinned decompiler version.

- [ ] **Step 1: Write failing cache test**

Compile a Java 8 fixture class, package it into a temporary JAR, decompile it twice, assert source contains the literal class and method names, and assert the second call reports `cached=true` without invoking the engine again.

- [ ] **Step 2: Verify RED**

Run `mvn -Dtest=VineflowerDecompilerTest,DecompileCacheTest test`.

Expected: FAIL because the decompiler adapter does not exist.

- [ ] **Step 3: Implement the Vineflower library adapter**

Use the supported Vineflower builder/library API. Provide the containing JAR as input and indexed dependency JARs as libraries. Collect only the requested top-level class source and its nested classes.

- [ ] **Step 4: Implement safe cache writes**

Write to a temporary file under the same cache directory and atomically move it to `decompiled/<package>/<Class>.java`. On failure, preserve any previous valid cache entry. Enforce a 2 MB response limit while retaining the full cached file.

- [ ] **Step 5: Verify GREEN**

Run focused tests and `mvn test`.

Expected: the Java 8 fixture decompiles, cache reuse is observable, and force bypasses cache.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/io/github/javacodeintel/mcp/decompile src/test/java/io/github/javacodeintel/mcp/decompile
git commit -m "feat: decompile and cache Java classes"
```

---

### Task 8: stdio MCP Server and Tool Adapters

**Files:**
- Create: `src/main/java/io/github/javacodeintel/mcp/Main.java`
- Create: `src/main/java/io/github/javacodeintel/mcp/mcp/McpServerFactory.java`
- Create: `src/main/java/io/github/javacodeintel/mcp/mcp/ToolSchemas.java`
- Create: `src/main/java/io/github/javacodeintel/mcp/mcp/ToolHandlers.java`
- Create: `src/main/java/io/github/javacodeintel/mcp/mcp/ToolErrorMapper.java`
- Test: `src/test/java/io/github/javacodeintel/mcp/mcp/ToolHandlersTest.java`
- Test: `src/test/java/io/github/javacodeintel/mcp/mcp/McpStdioIntegrationTest.java`

**Interfaces:**
- Consumes: IndexCoordinator, query services, and ClassDecompiler.
- Produces: the nine tools and exact input/output semantics from `MCP_API.md`.
- Produces: executable main class that reserves stdout for protocol traffic.

- [ ] **Step 1: Write failing handler tests**

Verify all nine names are registered, default arguments become target release 8 and change detection none, missing required fields map to INVALID_ARGUMENT, and domain errors map to stable error codes.

- [ ] **Step 2: Verify RED**

Run `mvn -Dtest=ToolHandlersTest test`.

Expected: FAIL because MCP adapters do not exist.

- [ ] **Step 3: Implement tool schemas and handlers**

Register index_project, get_index_status, search_classes, get_class, get_method, get_field, find_subtypes, find_callers, and decompile_class. Keep MCP DTO conversion inside the MCP package.

- [ ] **Step 4: Implement stdio server**

Use MCP Java SDK 2.0.0 synchronous server APIs and stdio transport. Write diagnostics through stderr. Add a process-level uncaught exception handler that never prints to stdout.

- [ ] **Step 5: Add protocol integration test**

Start `Main` in a child JVM, send initialize followed by notifications/initialized and tools/list, assert all nine tools are returned, and assert every stdout line is valid protocol JSON.

- [ ] **Step 6: Verify GREEN**

Run focused tests and `mvn test`.

Expected: handler and protocol integration tests pass with no stdout contamination.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/io/github/javacodeintel/mcp/Main.java src/main/java/io/github/javacodeintel/mcp/mcp src/test/java/io/github/javacodeintel/mcp/mcp
git commit -m "feat: expose code intelligence over MCP stdio"
```

---

### Task 9: End-to-End Acceptance, Packaging, and Operator Docs

**Files:**
- Create: `src/test/java/io/github/javacodeintel/mcp/acceptance/JavaCodeIntelligenceAcceptanceTest.java`
- Create: `scripts/run.ps1`
- Create: `scripts/run.sh`
- Create: `README.md`
- Create: `THIRD_PARTY_LICENSES.md`
- Modify: `PROJECT_OVERVIEW.md`
- Modify: `MCP_API.md`

**Interfaces:**
- Consumes: the complete server.
- Produces: a shaded executable JAR and documented Claude Code setup.

- [ ] **Step 1: Write failing end-to-end acceptance test**

Build a Java 8 fixture Maven project and dependency JAR containing BaseOrder, OrderDTO, PaymentService, and PaymentRepository. Through real services, index it, query class/method/field/subtypes/callers, decompile OrderDTO twice, and assert the second response is cached.

- [ ] **Step 2: Verify RED**

Run `mvn -Dtest=JavaCodeIntelligenceAcceptanceTest test`.

Expected: FAIL on the first incomplete integration seam rather than a fixture construction error.

- [ ] **Step 3: Complete the smallest integration gaps**

Wire real adapters in a composition root. Do not add new phase-two features. Ensure default NONE mode never opens a JAR solely to calculate a hash.

- [ ] **Step 4: Add launch scripts and README**

Both scripts verify Java feature version 21 or newer, launch the shaded JAR, and send their own messages to stderr. README includes build, test, package, Claude Code configuration, cache location, force rebuild, targetJavaRelease, and changeDetection examples.

- [ ] **Step 5: Build the executable artifact**

Run:

```powershell
$env:JAVA_HOME='/path/jdk-21'
mvn clean verify
```

Expected: exit 0 with all tests passing.

Then run:

```powershell
java -jar target/java-code-intelligence-mcp-0.1.0-SNAPSHOT.jar
```

Expected: process starts as a stdio MCP server without non-protocol stdout output.

- [ ] **Step 6: Validate requirements line by line**

Check every first-phase item in `PROJECT_OVERVIEW.md` and every tool in `MCP_API.md`. Record any unsupported item as an explicit limitation in README rather than claiming support.

- [ ] **Step 7: Commit**

```bash
git add README.md THIRD_PARTY_LICENSES.md scripts src/test PROJECT_OVERVIEW.md MCP_API.md
git commit -m "test: verify Java code intelligence MCP end to end"
```

---

## Final Verification

Run with JDK 21:

```powershell
$env:JAVA_HOME='/path/jdk-21'
mvn clean verify
```

Required evidence:

- Maven exits 0.
- Unit, integration, and MCP protocol tests report zero failures and zero errors.
- The shaded JAR exists.
- The acceptance fixture is compiled with Java release 8.
- No default code path calculates a JAR SHA-256.
- All nine MCP tools appear in tools/list.
- Repeated decompilation returns cached=true.
- stdout protocol integration test reports no contamination.

## Execution Ruling

The user explicitly requested multiple agents to implement independent modules concurrently. This overrides the subagent-driven-development default of one implementation agent at a time. Parallel tasks must use isolated Git worktrees and disjoint file ownership. The cost if this ruling is wrong is merge conflict or interface drift; the mitigation is a shared-contract foundation, wave-level integration tests, and task-scoped reviews before final integration.
