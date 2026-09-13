package io.github.javacodeintel.mcp.mcp;

import io.modelcontextprotocol.server.McpSyncServer;
import org.junit.jupiter.api.Test;
import java.io.*;
import static org.assertj.core.api.Assertions.assertThat;

class McpStdioIntegrationTest {
    @Test
    void createsStdioServerWithAllTools() {
        McpSyncServer server=McpServerFactory.create(new ToolHandlers(),new ByteArrayInputStream(new byte[0]),new ByteArrayOutputStream());
        try { assertThat(server.listTools()).extracting(t -> t.name()).containsExactlyInAnyOrder("index_project","get_index_status","search_classes","get_class","get_method","get_method_overview","get_field","find_subtypes","find_callers","find_callees","decompile_class"); }
        finally { server.close(); }
    }
}
