package io.github.javacodeintel.mcp.domain;
import java.util.List;
public record ClassInfo(String name,String internalName,String packageName,String kind,int accessFlags,String superName,List<String> interfaces,String signature,String sourceFile,int classFileMajorVersion,JarArtifact jar) { public ClassInfo { interfaces=interfaces==null?List.of():List.copyOf(interfaces); } public String fullyQualifiedName(){return name;} public String superClass(){return superName;} public int classFileJavaRelease(){return classFileMajorVersion>=45?classFileMajorVersion-44:classFileMajorVersion;} }
