package io.github.javacodeintel.mcp.decompile;
import io.github.javacodeintel.mcp.domain.*;
import io.github.javacodeintel.mcp.ports.ClassDecompiler;
import io.github.javacodeintel.mcp.ports.IndexStore;
import org.jetbrains.java.decompiler.main.decompiler.BaseDecompiler;
import org.jetbrains.java.decompiler.main.extern.IFernflowerLogger;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
public final class VineflowerDecompiler implements ClassDecompiler {
 public static final String VERSION="1.12.0"; private final DecompileCache cache; private final Path cacheRoot;
 public VineflowerDecompiler(IndexStore store,Path cacheRoot){this.cache=new DecompileCache(store,cacheRoot,VERSION);this.cacheRoot=cacheRoot;}
 @Override public DecompiledSource decompile(Path projectPath,JarArtifact jar,String className,int release,boolean force)throws IOException{
  DecompiledSource source=null;if(!force)source=cache.find(jar,className,release).orElse(null);
  if(source==null){DecompileResultSaver saver=new DecompileResultSaver(className);BaseDecompiler d=new BaseDecompiler(saver,Map.of(),IFernflowerLogger.NO_OP);d.addSource(jar.path().toFile());d.decompileContext();String text=saver.source();if(text==null)throw new IOException("Vineflower did not produce source for "+className);source=cache.save(jar,className,release,text,false);}
  List<DecompiledResource> resources=JarResourceExtractor.extract(jar.path(),cacheRoot,jarId(jar));
  return new DecompiledSource(source.className(),source.source(),source.cached(),source.sourcePath(),source.decompilerVersion(),resources);
 }
 private static String jarId(JarArtifact jar){return Integer.toHexString(jar.path().toAbsolutePath().normalize().toString().hashCode());}
}
