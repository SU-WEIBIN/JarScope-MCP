package io.github.javacodeintel.mcp.decompile;

import io.github.javacodeintel.mcp.domain.*;
import io.github.javacodeintel.mcp.storage.SqliteIndexStore;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import static org.assertj.core.api.Assertions.assertThat;

class DecompileCacheTest {
    @Test
    void writesSourceAtomicallyAndReadsItBack() throws Exception {
        Path root=Files.createTempDirectory("jci-cache-");try(SqliteIndexStore store=SqliteIndexStore.open(root.resolve("index.db"))){store.initialize();DecompileCache cache=new DecompileCache(store,root.resolve("decompiled"),"vf-test");JarArtifact jar=new JarArtifact(root.resolve("a.jar"),new MavenCoordinate("g","a","1"));DecompiledSource source=cache.save(jar,"demo.A",8,"class A {}",false);assertThat(source.sourcePath()).exists();assertThat(cache.find(jar,"demo.A",8)).get().extracting(DecompiledSource::source).isEqualTo("class A {}");}}
}
