package io.github.javacodeintel.mcp.domain;
import java.util.List;
public record ScanResult(List<ParsedClass> classes, List<IndexDiagnostic> diagnostics) {
    public ScanResult { classes = classes == null ? List.of() : List.copyOf(classes); diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics); }
}
