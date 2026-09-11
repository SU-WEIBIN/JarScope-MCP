package io.github.javacodeintel.mcp.domain;
public record CallEdge(String callerOwner,String callerName,String callerDescriptor,String targetOwner,String targetName,String targetDescriptor,String opcode,ResolutionKind resolutionKind,JarArtifact sourceJar) { }
