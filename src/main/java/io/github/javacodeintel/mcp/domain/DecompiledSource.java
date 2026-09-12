package io.github.javacodeintel.mcp.domain;
import java.nio.file.Path;
import java.util.List;
public record DecompiledSource(String className,String source,boolean cached,Path sourcePath,String decompilerVersion,List<DecompiledResource> resources) {
    public DecompiledSource { resources = resources == null ? List.of() : List.copyOf(resources); }
    public DecompiledSource(String className,String source,boolean cached,Path sourcePath,String decompilerVersion) { this(className,source,cached,sourcePath,decompilerVersion,List.of()); }
    public DecompiledSource(String className,String source){this(className,source,false,null,null,List.of());}
}
