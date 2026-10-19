package io.github.javacodeintel.mcp.mcp;

import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapperSupplier;
import io.modelcontextprotocol.server.*;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;

import java.io.*;
import java.util.*;

public final class McpServerFactory {
    private McpServerFactory(){}
    public static McpSyncServer create(ToolHandlers handlers){return create(handlers,System.in,System.out);}
    public static McpSyncServer create(ToolHandlers handlers,InputStream input,OutputStream output){McpJsonMapper mapper=new JacksonMcpJsonMapperSupplier().get();StdioServerTransportProvider transport=new StdioServerTransportProvider(mapper,input,output);List<McpServerFeatures.SyncToolSpecification> specifications=new ArrayList<>();for(McpSchema.Tool tool:ToolSchemas.tools()){specifications.add(McpServerFeatures.SyncToolSpecification.builder().tool(tool).callHandler((exchange,request)->{ToolHandlers.ToolResult result=handlers.handle(request.name(),request.arguments()==null?Map.of():request.arguments());return McpSchema.CallToolResult.builder().structuredContent(result.data()).addTextContent(result.text()).isError(result.error()).build();}).build());}return McpServer.sync(transport).serverInfo("java-code-intelligence-mcp","0.1.0").instructions("Java bytecode MCP. Use JDK 21; set targetJavaRelease to the project version (usually 8). Start with get_index_status; if missing/stale, run one index_project (do not duplicate while INDEX_IN_PROGRESS). Use absolute Maven project paths. For methods: search_classes -> get_method to choose descriptor -> get_method_overview -> paged find_callers/find_callees -> decompile_class last. Check page.nextOffset when truncated. Results are static approximations; verify reflection, proxies, polymorphism, and runtime behavior in source/logs. Maven settings come from normal user/install config; use a wrapper with -s for project-specific settings. Diagnose UNC path errors as MCP/Windows path handling issues. Avoid force=true unless cache is invalid; do not run concurrent index writes.").tools(specifications).build();}
}
