package io.github.javacodeintel.mcp.acceptance;

import io.github.javacodeintel.mcp.domain.*;
import io.github.javacodeintel.mcp.index.*;
import io.github.javacodeintel.mcp.query.*;
import io.github.javacodeintel.mcp.storage.SqliteIndexStore;
import io.github.javacodeintel.mcp.testing.JavaFixtureCompiler;
import org.junit.jupiter.api.Test;

import java.nio.file.*;
import java.util.*;
import java.util.jar.*;

import static org.assertj.core.api.Assertions.assertThat;

class JavaCodeIntelligenceAcceptanceTest {
    @Test
    void indexesQueriesAndPersistsAThirdPartyJarWithoutLoadingItsClasses() throws Exception {
        Path root=Files.createTempDirectory("jci-acceptance-");byte[] bytes=JavaFixtureCompiler.compileBytes(root,"demo.PaymentService","package demo; public class PaymentService { public String pay(){ return \"ok\"; } }");Path jarPath=root.resolve("payment-1.jar");try(JarOutputStream out=new JarOutputStream(Files.newOutputStream(jarPath))){out.putNextEntry(new JarEntry("demo/PaymentService.class"));out.write(bytes);out.closeEntry();}JarArtifact jar=new JarArtifact(jarPath,new MavenCoordinate("demo","payment","1"));
        try(SqliteIndexStore store=SqliteIndexStore.open(root.resolve("index.db"))){store.initialize();ScanResult scan=new JarClassScanner(new AsmBytecodeParser()).scan(jar,io.github.javacodeintel.mcp.config.ProjectOptions.defaults());store.replaceJarIndex(jar,scan.classes());PageResult<ClassInfo> classes=new ClassQueryService(store).search("PaymentService",PageRequest.defaults());assertThat(classes.items()).singleElement().extracting(ClassInfo::name).isEqualTo("demo.PaymentService");assertThat(new MethodQueryService(store).find("demo.PaymentService","pay",null,PageRequest.defaults()).items()).singleElement().extracting(MethodInfo::name).isEqualTo("pay");assertThat(store.summary().classes()).isEqualTo(1);}
    }
}
