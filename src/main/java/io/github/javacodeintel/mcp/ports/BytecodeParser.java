package io.github.javacodeintel.mcp.ports;
import io.github.javacodeintel.mcp.domain.JarArtifact; import io.github.javacodeintel.mcp.domain.ParsedClass;
public interface BytecodeParser { ParsedClass parse(byte[] bytecode, JarArtifact source); }
