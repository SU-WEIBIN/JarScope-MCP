package io.github.javacodeintel.mcp.mcp;

import java.io.IOException;

public final class ToolErrorMapper {
    private ToolErrorMapper() { }
    public static ToolHandlers.ToolResult map(Throwable error) {
        if (error instanceof IllegalArgumentException) return ToolHandlers.ToolResult.error("INVALID_ARGUMENT", error.getMessage());
        if (error instanceof java.nio.file.NoSuchFileException) return ToolHandlers.ToolResult.error("PROJECT_NOT_FOUND", error.getMessage());
        if (error instanceof IOException) return ToolHandlers.ToolResult.error("INDEX_ERROR", error.getMessage());
        return ToolHandlers.ToolResult.error("INTERNAL_ERROR", error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage());
    }
}
