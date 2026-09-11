package io.github.javacodeintel.mcp.domain;
import java.util.List;
public record MethodInfo(String owner,String name,String descriptor,String returnType,List<String> parameters,List<String> exceptions,int accessFlags,String genericSignature,List<AnnotationInfo> annotations) { public MethodInfo { parameters=parameters==null?List.of():List.copyOf(parameters); exceptions=exceptions==null?List.of():List.copyOf(exceptions); annotations=annotations==null?List.of():List.copyOf(annotations); } }
