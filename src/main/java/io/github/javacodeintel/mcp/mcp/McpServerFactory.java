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
    public static McpSyncServer create(ToolHandlers handlers,InputStream input,OutputStream output){McpJsonMapper mapper=new JacksonMcpJsonMapperSupplier().get();StdioServerTransportProvider transport=new StdioServerTransportProvider(mapper,input,output);List<McpServerFeatures.SyncToolSpecification> specifications=new ArrayList<>();for(McpSchema.Tool tool:ToolSchemas.tools()){specifications.add(McpServerFeatures.SyncToolSpecification.builder().tool(tool).callHandler((exchange,request)->{ToolHandlers.ToolResult result=handlers.handle(request.name(),request.arguments()==null?Map.of():request.arguments());return McpSchema.CallToolResult.builder().structuredContent(result.data()).addTextContent(result.text()).isError(result.error()).build();}).build());}return McpServer.sync(transport).serverInfo("java-code-intelligence-mcp","0.1.0").instructions("Java third-party bytecode intelligence for AI agents").tools(specifications).build();}
}
