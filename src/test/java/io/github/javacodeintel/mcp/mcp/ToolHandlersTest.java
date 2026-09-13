package io.github.javacodeintel.mcp.mcp;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

class ToolHandlersTest {
    @Test
    void exposesNavigationToolsAndMapsMissingArgumentsToStableError() {
        assertThat(ToolSchemas.tools()).extracting(t -> t.name()).containsExactlyInAnyOrder(
                "index_project","get_index_status","search_classes","get_class","get_method","get_method_overview","get_field","find_subtypes","find_callers","find_callees","decompile_class");
        ToolHandlers.ToolResult result=new ToolHandlers().handle("get_class",Map.of());
        assertThat(result.error()).isTrue(); assertThat(result.code()).isEqualTo("INVALID_ARGUMENT");
    }

    @Test
    void validatesCalleeNavigationArgumentsBeforeOpeningAProject() {
        ToolHandlers.ToolResult missingDescriptor=new ToolHandlers().handle("find_callees",Map.of(
                "projectPath", "does-not-exist", "owner", "demo.Service", "method", "run"));
        assertThat(missingDescriptor.error()).isTrue();
        assertThat(missingDescriptor.code()).isEqualTo("INVALID_ARGUMENT");
        assertThat(missingDescriptor.text()).contains("descriptor");

        ToolHandlers.ToolResult invalidCategory=new ToolHandlers().handle("find_callees",Map.of(
                "projectPath", "does-not-exist", "owner", "demo.Service", "method", "run",
                "descriptor", "()V", "category", "not-a-category"));
        assertThat(invalidCategory.error()).isTrue();
        assertThat(invalidCategory.code()).isEqualTo("INVALID_ARGUMENT");
    }

    @Test
    void describesProgressiveCallGraphNavigationToTheAgent() {
        var tools=ToolSchemas.tools();
        var overview=tools.stream().filter(t -> t.name().equals("get_method_overview")).findFirst().orElseThrow();
        var callees=tools.stream().filter(t -> t.name().equals("find_callees")).findFirst().orElseThrow();
        assertThat(overview.description()).containsIgnoringCase("bounded");
        assertThat(callees.description()).containsIgnoringCase("page");
        assertThat(callees.description()).containsIgnoringCase("category");
    }
}
