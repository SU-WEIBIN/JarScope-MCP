package io.github.javacodeintel.mcp.decompile;

import io.github.javacodeintel.mcp.domain.*;
import io.github.javacodeintel.mcp.ports.IndexStore;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public final class DecompileCache {
    private final IndexStore store; private final Path root; private final String version;
    public DecompileCache(IndexStore store,Path root,String version){this.store=Objects.requireNonNull(store);this.root=Objects.requireNonNull(root).toAbsolutePath().normalize();this.version=Objects.requireNonNull(version);}
    public Optional<DecompiledSource> find(JarArtifact jar,String className,int targetRelease)throws IOException{return store.findDecompiled(key(jar,className,targetRelease));}
    public DecompiledSource save(JarArtifact jar,String className,int targetRelease,String source,boolean cached)throws IOException{Path target=root.resolve(className.replace('.','/')+".java").normalize();if(!target.startsWith(root.toAbsolutePath().normalize()))throw new IOException("Invalid decompiled class path");Files.createDirectories(target.getParent());Path absolute=target.toAbsolutePath();Path temporary=Files.createTempFile(absolute.getParent(),absolute.getFileName().toString(),".tmp");try{Files.writeString(temporary,source,StandardCharsets.UTF_8,StandardOpenOption.TRUNCATE_EXISTING);try{Files.move(temporary,absolute,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException e){Files.move(temporary,absolute,StandardCopyOption.REPLACE_EXISTING);}}finally{Files.deleteIfExists(temporary);}DecompiledSource value=new DecompiledSource(className,source,cached,absolute,version);store.saveDecompiled(key(jar,className,targetRelease),value);return value;}
    public DecompileCacheKey key(JarArtifact jar,String className,int targetRelease){return new DecompileCacheKey(jar.path().toAbsolutePath().normalize().toString(),className.replace('.','/')+".class",targetRelease,version);}
}
