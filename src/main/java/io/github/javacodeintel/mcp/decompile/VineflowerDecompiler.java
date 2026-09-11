package io.github.javacodeintel.mcp.decompile;

import io.github.javacodeintel.mcp.domain.*;
import io.github.javacodeintel.mcp.ports.ClassDecompiler;
import io.github.javacodeintel.mcp.ports.IndexStore;
import org.jetbrains.java.decompiler.main.decompiler.BaseDecompiler;
import org.jetbrains.java.decompiler.main.extern.IFernflowerLogger;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

public final class VineflowerDecompiler implements ClassDecompiler {
    public static final String VERSION="1.12.0";
    private final DecompileCache cache;
    public VineflowerDecompiler(IndexStore store,Path cacheRoot){this.cache=new DecompileCache(store,cacheRoot,VERSION);}
    @Override public DecompiledSource decompile(Path projectPath,JarArtifact jar,String className,int targetJavaRelease,boolean force)throws IOException{if(!force){Optional<DecompiledSource> hit=cache.find(jar,className,targetJavaRelease);if(hit.isPresent())return hit.get();}DecompileResultSaver saver=new DecompileResultSaver(className);BaseDecompiler decompiler=new BaseDecompiler(saver,Map.of(),IFernflowerLogger.NO_OP);decompiler.addSource(jar.path().toFile());decompiler.decompileContext();String source=saver.source();if(source==null)throw new IOException("Vineflower did not produce source for "+className);return cache.save(jar,className,targetJavaRelease,source,false);}
}
