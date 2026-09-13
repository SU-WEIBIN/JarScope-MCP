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
    private static String description(String name){return switch(name){case "get_method_overview"->"Bounded method call-graph overview with relation counts, categories, representative evidence, and static-analysis warnings.";case "find_callees"->"Page and filter outgoing call targets by category, package, or name; repeated targets are grouped by default.";default->"Java code intelligence: "+name;};}
    private static Map<String,Object> string(){return Map.of("type","string");}
    private static Map<String,Object> integer(){return Map.of("type","integer");}
    private static Map<String,Object> bool(){return Map.of("type","boolean");}
}

