package io.github.javacodeintel.mcp;

import io.github.javacodeintel.mcp.mcp.McpServerFactory;
import io.github.javacodeintel.mcp.mcp.ToolHandlers;
import io.modelcontextprotocol.server.McpSyncServer;
import java.util.concurrent.CountDownLatch;

public final class Main {
    private Main(){}
    public static void main(String[] args) throws InterruptedException {McpSyncServer server=McpServerFactory.create(new ToolHandlers());Runtime.getRuntime().addShutdownHook(new Thread(server::close));System.err.println("java-code-intelligence-mcp stdio server started");new CountDownLatch(1).await();}
}
