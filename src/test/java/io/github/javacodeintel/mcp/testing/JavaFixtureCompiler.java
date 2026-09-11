package io.github.javacodeintel.mcp.testing;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class JavaFixtureCompiler {
    private JavaFixtureCompiler() { }

    public static Path compile(Path root, String className, String source) throws IOException {
        Path sourceFile = root.resolve("src").resolve(className.replace('.', '/') + ".java");
        Files.createDirectories(sourceFile.getParent());
        Files.writeString(sourceFile, source);
        Path output = root.resolve("classes");
        Files.createDirectories(output);
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) throw new IOException("No system Java compiler");
        int result = compiler.run(null, null, null, "--release", "8", "-d", output.toString(), sourceFile.toString());
        if (result != 0) throw new IOException("Fixture compilation failed: " + result);
        return output.resolve(className.replace('.', '/') + ".class");
    }

    public static byte[] compileBytes(Path root, String className, String source) throws IOException {
        return Files.readAllBytes(compile(root, className, source));
    }
}
