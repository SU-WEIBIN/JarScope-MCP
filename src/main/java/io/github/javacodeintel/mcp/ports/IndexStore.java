package io.github.javacodeintel.mcp.ports;

import io.github.javacodeintel.mcp.domain.*;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

public interface IndexStore extends AutoCloseable {
    void initialize() throws IOException;
    boolean containsJar(JarArtifact jar) throws IOException;
    default Optional<JarIndexState> findJarState(JarArtifact jar) throws IOException { return Optional.empty(); }
    default void saveJarState(JarArtifact jar, JarIndexState state) throws IOException { }
    void replaceJarIndex(JarArtifact jar, List<ParsedClass> classes) throws IOException;
    void removeJar(JarArtifact jar) throws IOException;
    List<ClassInfo> searchClasses(String query, String packagePrefix, String kind, int limit, int offset) throws IOException;
    long countClasses(String query, String packagePrefix, String kind) throws IOException;
    Optional<ClassInfo> findClass(String fullyQualifiedName) throws IOException;
    List<MethodInfo> findMethods(String owner, String name, String descriptor) throws IOException;
    List<FieldInfo> findFields(String owner, String fieldName) throws IOException;
    List<ClassInfo> findSubtypes(String typeName, boolean recursive, boolean includeInterfaces, int limit, int offset) throws IOException;
    long countSubtypes(String typeName, boolean recursive, boolean includeInterfaces) throws IOException;
    List<CallEdge> findCallers(String owner, String name, String descriptor, boolean includePolymorphic, int limit, int offset) throws IOException;
    long countCallers(String owner, String name, String descriptor, boolean includePolymorphic) throws IOException;
    Optional<DecompiledSource> findDecompiled(DecompileCacheKey key) throws IOException;
    void saveDecompiled(DecompileCacheKey key, DecompiledSource source) throws IOException;
    void invalidateDecompiledForJar(JarArtifact jar) throws IOException;
    IndexSummary summary() throws IOException;
    @Override default void close() throws IOException { }
}
