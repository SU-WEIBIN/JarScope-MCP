package io.github.javacodeintel.mcp.domain;
import java.util.List;
public record ClassInfo(String name,String internalName,String packageName,String kind,int accessFlags,String superName,List<String> interfaces,String signature,String sourceFile,int classFileMajorVersion,JarArtifact jar,List<AnnotationInfo> annotations) {
 public ClassInfo { interfaces=interfaces==null?List.of():List.copyOf(interfaces); annotations=annotations==null?List.of():List.copyOf(annotations); }
 public ClassInfo(String name,String internalName,String packageName,String kind,int accessFlags,String superName,List<String> interfaces,String signature,String sourceFile,int classFileMajorVersion,JarArtifact jar) { this(name,internalName,packageName,kind,accessFlags,superName,interfaces,signature,sourceFile,classFileMajorVersion,jar,List.of()); }
 public String fullyQualifiedName(){return name;} public String superClass(){return superName;} public int classFileJavaRelease(){return classFileMajorVersion>=45?classFileMajorVersion-44:classFileMajorVersion;}
}
