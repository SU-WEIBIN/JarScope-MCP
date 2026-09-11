package io.github.javacodeintel.mcp.decompile;

import io.github.javacodeintel.mcp.domain.*;
import io.github.javacodeintel.mcp.storage.SqliteIndexStore;
import io.github.javacodeintel.mcp.testing.JavaFixtureCompiler;
import org.junit.jupiter.api.Test;

import java.nio.file.*;
import java.util.jar.*;

import static org.assertj.core.api.Assertions.assertThat;

class VineflowerDecompilerTest {
    @Test
    void decompilesRequestedClassAndReusesPersistentCache() throws Exception {
        Path root=Files.createTempDirectory("jci-decompile-");byte[] bytes=JavaFixtureCompiler.compileBytes(root,"demo.Hello","package demo; public class Hello { public String greet(){ return \"hi\"; } }");Path jarPath=root.resolve("hello-1.jar");try(JarOutputStream out=new JarOutputStream(Files.newOutputStream(jarPath))){out.putNextEntry(new JarEntry("demo/Hello.class"));out.write(bytes);out.closeEntry();}
        JarArtifact jar=new JarArtifact(jarPath,new MavenCoordinate("demo","hello","1"));
        try(SqliteIndexStore store=SqliteIndexStore.open(root.resolve("index.db"))){store.initialize();VineflowerDecompiler decompiler=new VineflowerDecompiler(store,root.resolve("decompiled"));DecompiledSource first=decompiler.decompile(root,jar,"demo.Hello",8,false);assertThat(first.source()).contains("class Hello","greet");assertThat(first.cached()).isFalse();DecompiledSource second=decompiler.decompile(root,jar,"demo.Hello",8,false);assertThat(second.cached()).isTrue();assertThat(second.source()).isEqualTo(first.source());}
    }
}
