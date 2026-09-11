package io.github.javacodeintel.mcp.index;

import io.github.javacodeintel.mcp.domain.*;
import io.github.javacodeintel.mcp.testing.JavaFixtureCompiler;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AsmBytecodeParserTest {
    @Test
    void extractsJava8ClassMembersAnnotationsAndCalls() throws Exception {
        Path root = Files.createTempDirectory("jci-fixture-");
        String source = "package demo;" +
                "@interface Marker { String value(); }" +
                "interface Port { void send(String value); }" +
                "@Marker(\"dto\") public class OrderDTO implements Port {" +
                " private Long id; public Long getId(){ return id; }" +
                " public void send(String value){ value.toString(); }" +
                "}";
        Path classFile = JavaFixtureCompiler.compile(root, "demo.OrderDTO", source);
        JarArtifact jar = new JarArtifact(root.resolve("order-api-1.0.jar"), new MavenCoordinate("demo", "order-api", "1.0"));
        ParsedClass parsed = new AsmBytecodeParser().parse(Files.readAllBytes(classFile), jar);

        assertThat(parsed.classInfo().name()).isEqualTo("demo.OrderDTO");
        assertThat(parsed.classInfo().classFileJavaRelease()).isEqualTo(8);
        assertThat(parsed.classInfo().interfaces()).containsExactly("demo.Port");
        assertThat(parsed.classInfo().annotations()).extracting(AnnotationInfo::type).contains("demo.Marker");
        assertThat(parsed.fields()).anyMatch(field -> field.name().equals("id") && field.type().equals("java.lang.Long"));
        assertThat(parsed.methods()).extracting(MethodInfo::name).contains("getId", "send");
        assertThat(parsed.callEdges()).anyMatch(edge -> edge.targetOwner().equals("java.lang.String") && edge.targetName().equals("toString"));
    }
}
