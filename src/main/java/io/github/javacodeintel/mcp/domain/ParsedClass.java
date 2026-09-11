package io.github.javacodeintel.mcp.domain;
import java.util.List;
public record ParsedClass(ClassInfo classInfo,List<FieldInfo> fields,List<MethodInfo> methods,List<CallEdge> callEdges) { public ParsedClass { fields=fields==null?List.of():List.copyOf(fields); methods=methods==null?List.of():List.copyOf(methods); callEdges=callEdges==null?List.of():List.copyOf(callEdges); } }
