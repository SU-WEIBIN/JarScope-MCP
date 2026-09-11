package io.github.javacodeintel.mcp.mcp;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

class ToolHandlersTest {
    @Test
    void exposesNineToolsAndMapsMissingArgumentsToStableError() {
        assertThat(ToolSchemas.tools()).extracting(t -> t.name()).containsExactlyInAnyOrder(
                "index_project","get_index_status","search_classes","get_class","get_method","get_field","find_subtypes","find_callers","decompile_class");
        ToolHandlers.ToolResult result=new ToolHandlers().handle("get_class",Map.of());
        assertThat(result.error()).isTrue(); assertThat(result.code()).isEqualTo("INVALID_ARGUMENT");
    }
}
