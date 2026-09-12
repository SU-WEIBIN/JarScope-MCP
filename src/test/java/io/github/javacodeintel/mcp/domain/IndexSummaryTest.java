package io.github.javacodeintel.mcp.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IndexSummaryTest {
    @Test
    void retainsReactorModuleCountAndResolutionDiagnostics() {
        IndexDiagnostic diagnostic = new IndexDiagnostic(
                "MAVEN_PARTIAL_RESOLUTION", "one module was incomplete", "WARNING", null, true);

        IndexSummary summary = new IndexSummary(
                3, 12, 4, 8, 100, 200, 300, 400, 1, 500, List.of(diagnostic));

        assertThat(summary.modules()).isEqualTo(3);
        assertThat(summary.diagnostics()).containsExactly(diagnostic);
    }
}
