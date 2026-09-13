package io.github.javacodeintel.mcp.query;

import io.github.javacodeintel.mcp.domain.CallEdge;
import io.github.javacodeintel.mcp.domain.ResolutionKind;
import org.junit.jupiter.api.Test;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class CallEdgeClassifierTest {
    private final CallEdgeClassifier classifier = new CallEdgeClassifier();

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.MethodSource("categories")
    void classifiesNavigationCategoriesByTransparentRules(String owner, String name, CallCategory category) {
        CallEdgeView result = classifier.classify(edge(owner, name));
        assertThat(result.category()).isEqualTo(category);
        assertThat(result.reason()).isNotBlank();
    }

    static Stream<org.junit.jupiter.params.provider.Arguments> categories() {
        return Stream.of(
                arguments("java.util.ArrayList", "add", CallCategory.FRAMEWORK_OR_UTILITY),
                arguments("demo.PartMapper", "queryChildPartByFatherOids", CallCategory.DATA_ACCESS),
                arguments("demo.BomService", "expandTree", CallCategory.BOM_EXPANSION),
                arguments("demo.BomExportService", "writeExcel", CallCategory.EXPORT_OR_BATCH),
                arguments("demo.OrderService", "load", CallCategory.BUSINESS));
    }

    @Test
    void marksIncompleteTargetAsUnknown() {
        CallEdgeView result = classifier.classify(edge(null, "call"));
        assertThat(result.category()).isEqualTo(CallCategory.UNKNOWN);
    }

    @Test
    void validatesCalleeQueryAndCallSiteCount() {
        assertThatThrownBy(() -> new CalleeQuery(null, CallCategory.ALL, null, null, true))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new CallEdgeView(edge("demo.Service", "call"), CallCategory.BUSINESS, 0, "reason"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static CallEdge edge(String owner, String name) {
        return new CallEdge("demo.Service", "run", "()V", owner, name, "()V", "invokevirtual", ResolutionKind.DIRECT, null);
    }
}
