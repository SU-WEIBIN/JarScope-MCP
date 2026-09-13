package io.github.javacodeintel.mcp.storage;

import io.github.javacodeintel.mcp.domain.AnnotationInfo;
import io.github.javacodeintel.mcp.domain.CallEdge;
import io.github.javacodeintel.mcp.domain.ClassInfo;
import io.github.javacodeintel.mcp.domain.DecompileCacheKey;
import io.github.javacodeintel.mcp.domain.DecompiledSource;
import io.github.javacodeintel.mcp.domain.FieldInfo;
import io.github.javacodeintel.mcp.domain.JarArtifact;
import io.github.javacodeintel.mcp.domain.MavenCoordinate;
import io.github.javacodeintel.mcp.domain.MethodInfo;
import io.github.javacodeintel.mcp.domain.ParsedClass;
import io.github.javacodeintel.mcp.domain.ResolutionKind;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SqliteIndexStoreTest {
    @Test
    void persistsClassMembersCallsAndDecompiledSourceAcrossReopen() throws Exception {
        Path database = Files.createTempFile("jci-index-", ".db");
        JarArtifact jar = new JarArtifact(
                Path.of("/path/m2/com/example/order-api/1.0/order-api-1.0.jar"),
                new MavenCoordinate("com.example", "order-api", "1.0"));
        ClassInfo classInfo = new ClassInfo(
                "com.example.OrderDTO", "com/example/OrderDTO", "com.example", "class",
                1, "java.lang.Object", List.of("java.io.Serializable"), null,
                "OrderDTO.java", 52, jar);
        FieldInfo field = new FieldInfo(
                classInfo.name(), "id", "java.lang.Long", null, 2, false, false,
                List.of(new AnnotationInfo("com.example.Id", Map.of("value", "id"))));
        MethodInfo method = new MethodInfo(
                classInfo.name(), "getId", "()Ljava/lang/Long;", "java.lang.Long",
                List.of(), List.of("java.io.IOException", "java.io.IOException"), 1, null, List.of());
        CallEdge edge = new CallEdge(
                classInfo.name(), "getId", "()Ljava/lang/Long;",
                "java.lang.Object", "toString", "()Ljava/lang/String;",
                "invokevirtual", ResolutionKind.DIRECT, jar);

        try (SqliteIndexStore store = SqliteIndexStore.open(database)) {
            store.initialize();
            store.replaceJarIndex(jar, List.of(new ParsedClass(classInfo, List.of(field), List.of(method), List.of(edge))));

            assertThat(store.findClass(classInfo.name())).get()
                    .extracting(ClassInfo::name, ClassInfo::superName)
                    .containsExactly(classInfo.name(), "java.lang.Object");
            assertThat(store.findFields(classInfo.name(), "id")).singleElement()
                    .extracting(FieldInfo::type).isEqualTo("java.lang.Long");
            assertThat(store.findMethods(classInfo.name(), "getId", "()Ljava/lang/Long;")).singleElement()
                    .extracting(MethodInfo::returnType).isEqualTo("java.lang.Long");
            assertThat(store.findMethods(classInfo.name(), "getId", "()Ljava/lang/Long;")).singleElement()
                    .extracting(MethodInfo::exceptions)
                    .isEqualTo(List.of("java.io.IOException"));
            assertThat(store.findCallers("java.lang.Object", "toString", "()Ljava/lang/String;", false, 10, 0))
                    .singleElement().extracting(CallEdge::callerOwner).isEqualTo(classInfo.name());
            assertThat(store.findCallees(classInfo.name(), "getId", "()Ljava/lang/Long;", 1, 0))
                    .singleElement().extracting(CallEdge::targetOwner).isEqualTo("java.lang.Object");
            assertThat(store.countCallees(classInfo.name(), "getId", "()Ljava/lang/Long;")).isEqualTo(1);

            DecompileCacheKey key = new DecompileCacheKey("com.example:order-api:1.0", "com/example/OrderDTO.class", 8, "vf-1");
            DecompiledSource source = new DecompiledSource(classInfo.name(), "class OrderDTO {}", false, null, "vf-1");
            store.saveDecompiled(key, source);
            assertThat(store.findDecompiled(key)).get().extracting(DecompiledSource::source).isEqualTo("class OrderDTO {}");
        }

        try (SqliteIndexStore reopened = SqliteIndexStore.open(database)) {
            reopened.initialize();
            assertThat(reopened.findClass(classInfo.name())).isPresent();
            assertThat(reopened.summary().classes()).isEqualTo(1);
            assertThat(reopened.summary().methods()).isEqualTo(1);
            assertThat(reopened.summary().fields()).isEqualTo(1);
            assertThat(reopened.summary().calls()).isEqualTo(1);
        }
    }
}
