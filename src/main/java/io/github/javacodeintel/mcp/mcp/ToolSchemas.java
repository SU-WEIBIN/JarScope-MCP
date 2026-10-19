package io.github.javacodeintel.mcp.mcp;

import io.modelcontextprotocol.spec.McpSchema;
import java.util.*;

public final class ToolSchemas {
    private ToolSchemas(){}
    public static List<McpSchema.Tool> tools() {
        List<McpSchema.Tool> result = new ArrayList<>();
        result.add(tool("index_project", List.of("projectPath"), Map.of(
                "projectPath", string(),
                "targetJavaRelease", integer(),
                "enforceTargetRelease", bool(),
                "changeDetection", Map.of("type", "string", "enum", List.of("NONE", "METADATA", "SHA256")),
                "includeTestDependencies", bool(),
                "force", bool())));
        result.add(tool("get_index_status", List.of("projectPath"), Map.of("projectPath", string())));
        result.add(tool("search_classes", List.of("projectPath", "query"), Map.of(
                "projectPath", string(), "query", string(), "limit", integer(), "offset", integer())));
        result.add(tool("get_class", List.of("projectPath", "className"), Map.of(
                "projectPath", string(), "className", string(), "includeMembers", bool(), "includeAnnotations", bool())));
        result.add(tool("get_method", List.of("projectPath", "owner", "method"), Map.of(
                "projectPath", string(), "owner", string(), "method", string(), "descriptor", string(),
                "includeCallers", bool(), "includeCallees", bool(), "limit", integer(), "offset", integer())));
        result.add(tool("get_method_overview", List.of("projectPath", "owner", "method"), Map.of(
                "projectPath", string(), "owner", string(), "method", string(), "descriptor", string(),
                "includeCallers", bool(), "representativeLimit", integer())));
        result.add(tool("get_field", List.of("projectPath", "owner"), Map.of(
                "projectPath", string(), "owner", string(), "fieldName", string(), "includeAccessors", bool(),
                "limit", integer(), "offset", integer())));
        result.add(tool("find_subtypes", List.of("projectPath", "typeName"), Map.of(
                "projectPath", string(), "typeName", string(), "recursive", bool(), "includeInterfaces", bool(),
                "limit", integer(), "offset", integer())));
        result.add(tool("find_callers", List.of("projectPath", "owner", "method", "descriptor"), Map.of(
                "projectPath", string(), "owner", string(), "method", string(), "descriptor", string(),
                "matchMode", Map.of("type", "string", "enum", List.of("exact", "polymorphic")),
                "limit", integer(), "offset", integer())));
        result.add(tool("find_callees", List.of("projectPath", "owner", "method", "descriptor"), Map.of(
                "projectPath", string(), "owner", string(), "method", string(), "descriptor", string(),
                "category", Map.of("type", "string", "enum", List.of("all", "business", "data_access", "bom_expansion", "export_or_batch", "framework_or_utility", "unknown")),
                "packagePrefix", string(), "nameQuery", string(),
                "groupBy", Map.of("type", "string", "enum", List.of("target", "callSite")),
                "limit", integer(), "offset", integer())));
        result.add(tool("decompile_class", List.of("projectPath", "className"), Map.of(
                "projectPath", string(), "className", string(), "targetJavaRelease", integer(),
                "force", bool(), "includeMetadata", bool(), "includeSource", bool())));
        return List.copyOf(result);
    }
    private static McpSchema.Tool tool(String name,List<String> required,Map<String,Object> properties){Map<String,Object> schema=new LinkedHashMap<>();schema.put("type","object");schema.put("properties",properties);schema.put("required",required);schema.put("additionalProperties",false);return McpSchema.Tool.builder(name).description(description(name)).inputSchema(schema).build();}
    private static String description(String name){return switch(name){
        case "index_project"->"Build or refresh the bytecode index of a Maven Java project. Call this first when get_index_status reports missing or stale; do not call again while status is INDEX_IN_PROGRESS. Required before any other tool works on that project.";
        case "get_index_status"->"Check whether a Maven project is indexed and fresh. Call this FIRST in any Java code-analysis task, before search_classes/get_method/find_callers. Returns status such as READY, STALE, MISSING, INDEX_IN_PROGRESS.";
        case "search_classes"->"Find Java classes by name or keyword in the project and its dependencies (including jars with no source). Use instead of grep/glob when locating a class, a dependency class, or a fully qualified name.";
        case "get_class"->"Get a class's structure: superclass, interfaces, fields, methods, annotations. Use to inspect a class (including dependency classes without source) before reading or decompiling it.";
        case "get_method"->"Look up a method's signatures and descriptors; optionally include callers/callees. Use to pick the exact descriptor when a method name is overloaded, before find_callers/find_callees/get_method_overview.";
        case "get_method_overview"->"Bounded method call-graph overview with relation counts, categories, representative evidence, and static-analysis warnings. Best first step to answer 'who calls this / what does this call / how does this flow work' instead of grepping across many files.";
        case "get_field"->"Find a field and the methods that read or write it (accessors). Use to trace where a field or constant is used or modified.";
        case "find_subtypes"->"List subclasses and implementations of a class or interface (optionally recursive). Use to find all implementors of an interface or abstract class, which grep cannot do reliably.";
        case "find_callers"->"Page through methods that call a given method (matchMode exact or polymorphic). Use for impact analysis and 'where is this method used'. Check page.nextOffset when truncated.";
        case "find_callees"->"Page and filter outgoing call targets by category, package, or name; repeated targets are grouped by default. Use to see what a method depends on, e.g. data_access or business calls. Check page.nextOffset when truncated.";
        case "decompile_class"->"Decompile a class from bytecode to Java source. Use as a last resort, mainly for dependency classes that have no source available; prefer the structural tools first.";
        default->"Java code intelligence: "+name;};}
    private static Map<String,Object> string(){return Map.of("type","string");}
    private static Map<String,Object> integer(){return Map.of("type","integer");}
    private static Map<String,Object> bool(){return Map.of("type","boolean");}
}

