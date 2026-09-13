# Method Overview and Call Navigation Implementation Plan

> **For agentic workers:** Execute this plan task by task with a test-first cycle. Every production change must be preceded by a failing test.

**Goal:** Reduce large Java call-graph responses while preserving enough structure and evidence for an AI agent to understand a project and expand only relevant nodes.

**Architecture:** Keep the existing method metadata and caller APIs compatible. Add a paged, grouped callee query and a separate method-overview response that reports counts, semantic categories, and representative edges. Semantic labels are deterministic heuristics with explicit reasons; they are navigation hints, not claims about runtime behavior.

**Tech Stack:** Java 21, Maven, JUnit 5, AssertJ, SQLite, MCP Java SDK, ASM.

**Spec:** The approved design in the preceding conversation: IDE-style progressive disclosure, grouped call targets, bounded overview responses, paged filtered expansion, and evidence-backed summaries.

## Global Constraints

- Existing `get_method`, `find_callers`, and pagination fields remain usable by current clients.
- New overview responses must not eagerly serialize all callees.
- A call graph is static bytecode analysis; `ResolutionKind` and the static-analysis caveat remain visible.
- `descriptor` remains the authoritative overload discriminator.
- Overview representative items are bounded by a fixed server-side limit and sorted deterministically.
- `fullResultPath` remains a fallback for legacy large responses; the normal navigation path uses structured pagination.

---

### Task 1: Lock down the response and query behavior with failing tests

**Files:**
- Modify: `src/test/java/io/github/javacodeintel/mcp/query/QueryServicesTest.java`
- Modify: `src/test/java/io/github/javacodeintel/mcp/storage/SqliteIndexStoreTest.java`
- Modify: `src/test/java/io/github/javacodeintel/mcp/mcp/ToolHandlersTest.java`
- Modify: `src/test/java/io/github/javacodeintel/mcp/mcp/McpStdioIntegrationTest.java`

**Tests to add:**

- A paged callee query returns `total`, `truncated`, and `nextOffset`, with stable non-overlapping pages.
- Grouping by target collapses repeated bytecode call sites and reports the call-site count.
- A method overview with hundreds of callees returns category counts and at most the configured number of representative targets.
- Overview output remains below the response budget for a fixture with 285 synthetic call edges.
- Category filtering returns only matching targets and preserves deterministic ordering.
- The MCP tool list exposes the new tool and rejects unknown arguments because schemas use `additionalProperties=false`.
- Calling the overview tool with an overloaded method and no descriptor returns all overloads without mixing their relations.

**TDD execution:**

- Write the assertions against the intended records and handler result shape before adding production types.
- Run each focused test and confirm it fails because the new API or behavior is absent, not because of a fixture typo.

Run: `mvn -o -f pom.xml -Dtest=QueryServicesTest,SqliteIndexStoreTest,ToolHandlersTest,McpStdioIntegrationTest test`

Expected before implementation: compilation failures for the new query/overview APIs and handler behavior.

### Task 2: Add typed query and summary models

**Files:**
- Create: `src/main/java/io/github/javacodeintel/mcp/query/CalleeQuery.java`
- Create: `src/main/java/io/github/javacodeintel/mcp/query/CallCategory.java`
- Create: `src/main/java/io/github/javacodeintel/mcp/query/CallEdgeView.java`
- Create: `src/main/java/io/github/javacodeintel/mcp/query/CallCategoryCount.java`
- Create: `src/main/java/io/github/javacodeintel/mcp/query/MethodOverview.java`

**Interfaces:**

- `CalleeQuery` carries `limit`, `offset`, `category`, `packagePrefix`, `nameQuery`, and `groupBy`; validate limits using the existing `PageRequest` bounds.
- `CallCategory` contains `BUSINESS`, `DATA_ACCESS`, `BOM_EXPANSION`, `EXPORT_OR_BATCH`, `FRAMEWORK_OR_UTILITY`, and `UNKNOWN`.
- `CallEdgeView` contains a `CallEdge`, category, call-site count, and a short deterministic reason.
- `MethodOverview` contains method metadata, caller/callee totals, category counts, representative edges, and static-analysis warnings.

**TDD execution:**

- Add constructor validation tests for negative offsets, invalid limits, and unsupported grouping values.
- Add equality and serialization-oriented tests for records so null collections become empty immutable lists.
- Run the focused model tests and confirm they pass before wiring storage.

### Task 3: Make callee storage pageable and groupable

**Files:**
- Modify: `src/main/java/io/github/javacodeintel/mcp/ports/IndexStore.java`
- Modify: `src/main/java/io/github/javacodeintel/mcp/storage/SqliteIndexStore.java`
- Modify: `src/main/java/io/github/javacodeintel/mcp/query/RelationQueryService.java`

**Interfaces:**

- Add `PageResult<CallEdgeView> callees(String owner, String name, String descriptor, CalleeQuery query)` to `RelationQueryService`.
- Add `long countCallees(String owner, String name, String descriptor, CalleeQuery query)` and a paged `findCallees` method to `IndexStore`.
- Keep the existing unpaged `findCallees` default method for source compatibility, delegating only where safe.

**Implementation details:**

- Use `ORDER BY target_owner, target_name, target_descriptor, opcode` for stable raw-edge pages.
- For `groupBy=target`, group by target signature and return one view per target with its call-site count.
- Apply package and method-name filters in SQL before pagination.
- Apply category filtering through the classifier before constructing the page; if a SQL filter cannot express a category, scan deterministically and stop only after enough matching items are collected, while reporting the true total.
- Preserve `ResolutionKind` and source-jar metadata in every returned edge.

**TDD execution:**

- Implement only enough SQL and service code to make the storage tests green.
- Run `mvn -o -f pom.xml -Dtest=SqliteIndexStoreTest,QueryServicesTest test` after each focused change.

### Task 4: Add deterministic semantic classification

**Files:**
- Create: `src/main/java/io/github/javacodeintel/mcp/query/CallEdgeClassifier.java`
- Modify: `src/main/java/io/github/javacodeintel/mcp/query/RelationQueryService.java`

**Classification rules:**

- `FRAMEWORK_OR_UTILITY` for JDK, logging, collection, Spring, and common framework package prefixes.
- `DATA_ACCESS` for `Mapper`/`Repository` target classes or query/update/select/insert/delete method names.
- `BOM_EXPANSION` for target names containing `bom`, `expand`, `tree`, `parallel`, or `part`.
- `EXPORT_OR_BATCH` for target names containing `export`, `batch`, `excel`, `file`, or `write`.
- `BUSINESS` for remaining project-local-looking targets.
- `UNKNOWN` when the target cannot be classified confidently.

Rules are evaluated in a fixed precedence order and return a reason string such as `target class ends with Mapper` or `method name contains export`.

**TDD execution:**

- Add table-driven tests covering each category, precedence conflicts, null metadata, and unknown targets.
- Add a regression test proving a Mapper method containing `query` is categorized as data access even when its package also contains a generic utility word.
- Run classifier tests before integrating it with SQLite pages.

### Task 5: Implement the bounded method overview

**Files:**
- Create: `src/main/java/io/github/javacodeintel/mcp/query/MethodOverviewService.java`
- Modify: `src/main/java/io/github/javacodeintel/mcp/mcp/ToolHandlers.java`
- Modify: `src/main/java/io/github/javacodeintel/mcp/mcp/ToolSchemas.java`

**Interface:**

```text
get_method_overview(
  projectPath,
  owner,
  method,
  descriptor?,
  includeCallers?,
  representativeLimit?
)
```

**Behavior:**

- Resolve all overloads when `descriptor` is absent; resolve exactly one method when it is present.
- Return method metadata, caller count and examples, callee call-site count, unique-target count, category counts, representative targets, and warnings.
- Use a fixed default representative limit of 5 per high-value category and cap the caller examples at the same global budget.
- Rank data-access, BOM-expansion, export/batch, and business targets before framework/utility targets; break ties by fully qualified target signature.
- Do not include the complete callee list in the overview response.
- Return a short text sentence that points to `find_callees` for expansion rather than duplicating the structured payload.

**TDD execution:**

- First add handler tests for one method, overloads, empty relations, and a 285-edge fixture.
- Run the handler tests and confirm the expected failures.
- Implement the service and handler wiring minimally, then rerun until green.

### Task 6: Add the paged `find_callees` MCP tool

**Files:**
- Modify: `src/main/java/io/github/javacodeintel/mcp/mcp/ToolHandlers.java`
- Modify: `src/main/java/io/github/javacodeintel/mcp/mcp/ToolSchemas.java`
- Modify: `src/test/java/io/github/javacodeintel/mcp/mcp/ToolHandlersTest.java`
- Modify: `src/test/java/io/github/javacodeintel/mcp/mcp/McpStdioIntegrationTest.java`

**Input schema:**

```text
projectPath, owner, method, descriptor,
limit, offset, category, packagePrefix, nameQuery, groupBy
```

`descriptor` is required. `category` defaults to all categories. `groupBy` defaults to `target`.

**Behavior:**

- Return `{owner, method, descriptor, items, page}`.
- Return `CallEdgeView` items for grouped pages and raw `CallEdgeView` items for call-site pages.
- Reject missing descriptors, invalid categories, invalid grouping, negative offsets, and limits above 500 as `INVALID_ARGUMENT`.
- Keep `get_method` unchanged for compatibility during the first rollout; document the new overview and callee tools as the preferred path.

### Task 7: Update documentation and agent workflow

**Files:**
- Modify: `AI_AGENT_GUIDE.md`
- Modify: `MCP_API.md`
- Modify: `README.md`

Document the following workflow:

```text
search_classes
  -> get_class(includeMembers=false)
  -> get_method (confirm descriptor)
  -> get_method_overview
  -> find_callees(category=...)
  -> decompile_class for selected classes only
```

Document that `fullResultPath` is a fallback artifact, that `truncated=true` requires `nextOffset`, and that classifications are heuristic navigation hints while `ResolutionKind` describes static call resolution.

### Task 8: Full verification and regression checks

**Files:**
- No production files; run the complete project checks.

Run:

```powershell
mvn -o -f pom.xml test
mvn -o -f pom.xml verify
git diff --check
git status --short
```

Review the serialized overview from the large fixture and record its byte size. Verify that no overview path calls the unbounded `findCallees` method. Confirm the baseline commit remains the parent of the feature commits and that no push was performed.

