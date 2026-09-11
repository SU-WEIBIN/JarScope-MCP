package io.github.javacodeintel.mcp.domain;
import java.nio.file.Path;
public record DecompiledSource(String className,String source,boolean cached,Path sourcePath,String decompilerVersion) { public DecompiledSource(String className,String source){this(className,source,false,null,null);} }
