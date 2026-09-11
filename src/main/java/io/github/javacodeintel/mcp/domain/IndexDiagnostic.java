package io.github.javacodeintel.mcp.domain;
public record IndexDiagnostic(String code,String message,String severity,String jarEntry,boolean retryable) { }
