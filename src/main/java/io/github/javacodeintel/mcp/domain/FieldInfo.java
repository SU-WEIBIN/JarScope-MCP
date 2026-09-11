package io.github.javacodeintel.mcp.domain;
import java.util.List;
public record FieldInfo(String owner,String name,String type,String genericType,int accessFlags,boolean isStatic,boolean isFinal,List<AnnotationInfo> annotations) { public FieldInfo { annotations=annotations==null?List.of():List.copyOf(annotations); } public boolean staticField(){return isStatic;} public boolean finalField(){return isFinal;} }
