package io.github.javacodeintel.mcp.project;

import io.github.javacodeintel.mcp.config.ProjectOptions;
import io.github.javacodeintel.mcp.domain.*;
import io.github.javacodeintel.mcp.index.JarClassScanner;
import io.github.javacodeintel.mcp.ports.*;
import io.github.javacodeintel.mcp.storage.SqliteIndexStore;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

public final class IndexCoordinator {
    private final DependencyProvider dependencies; private final JarClassScanner scanner; private final IndexStore store; private final ProjectCacheLayout layout;
    public IndexCoordinator(DependencyProvider dependencies,JarClassScanner scanner,IndexStore store,ProjectCacheLayout layout){this.dependencies=Objects.requireNonNull(dependencies);this.scanner=Objects.requireNonNull(scanner);this.store=Objects.requireNonNull(store);this.layout=Objects.requireNonNull(layout);}
    public static IndexCoordinator open(Path project,ProjectOptions options) throws IOException {ProjectCacheLayout layout=ProjectCacheLayout.forProject(project);layout.createDirectories();SqliteIndexStore store=SqliteIndexStore.open(layout.database());store.initialize();return new IndexCoordinator(new MavenDependencyProvider(),new JarClassScanner(new io.github.javacodeintel.mcp.index.AsmBytecodeParser()),store,layout);}
    public IndexSummary index(Path project,ProjectOptions options,boolean force)throws IOException{long start=System.currentTimeMillis();layout.createDirectories();store.initialize();try(ProjectWriteLock ignored=ProjectWriteLock.acquire(layout.root().resolve("index.lock"))){List<JarArtifact> jars=dependencies.resolve(project,options);long indexed=0,reused=0;for(JarArtifact jar:jars){if(!force&&options.changeDetection()==ChangeDetectionMode.NONE&&store.containsJar(jar)){reused++;continue;}ScanResult result=scanner.scan(jar,options);store.replaceJarIndex(jar,result.classes());store.invalidateDecompiledForJar(jar);indexed++;}IndexSummary current=store.summary();return new IndexSummary(0,jars.size(),indexed,reused,current.classes(),current.methods(),current.fields(),current.calls(),current.errors(),System.currentTimeMillis()-start);}}
    public IndexStore store(){return store;}
}
