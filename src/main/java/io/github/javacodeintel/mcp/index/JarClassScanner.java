package io.github.javacodeintel.mcp.index;

import io.github.javacodeintel.mcp.config.ProjectOptions;
import io.github.javacodeintel.mcp.domain.*;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.jar.*;

public final class JarClassScanner {
    private final AsmBytecodeParser parser;
    public JarClassScanner(AsmBytecodeParser parser){this.parser=Objects.requireNonNull(parser,"parser");}
    public ScanResult scan(JarArtifact jar, ProjectOptions options) throws IOException {
        List<ParsedClass> classes = new ArrayList<>();
        List<IndexDiagnostic> diagnostics = new ArrayList<>();
        List<String> entries = new ArrayList<>();
        try (JarFile file = new JarFile(jar.path().toFile())) {
            Enumeration<JarEntry> enumeration = file.entries();
            while (enumeration.hasMoreElements()) {
                entries.add(enumeration.nextElement().getName());
            }
            for (String entry : MultiReleaseClassSelector.select(entries, options.targetJavaRelease())) {
                JarEntry jarEntry = file.getJarEntry(entry);
                try (InputStream input = file.getInputStream(jarEntry)) {
                    byte[] bytecode = input.readAllBytes();
                    int classFileRelease = classFileRelease(bytecode);
                    if (options.enforceTargetRelease()
                            && classFileRelease > options.targetJavaRelease()) {
                        diagnostics.add(new IndexDiagnostic(
                                "CLASS_VERSION_EXCLUDED",
                                "Skipped " + entry + " compiled for Java " + classFileRelease
                                        + " because targetJavaRelease is " + options.targetJavaRelease(),
                                "WARNING", entry, false));
                        continue;
                    }
                    classes.add(parser.parse(bytecode, jar));
                } catch (Exception e) {
                    diagnostics.add(new IndexDiagnostic(
                            "CLASS_PARSE_FAILED",
                            e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(),
                            "WARNING", entry, true));
                }
            }
        }
        return new ScanResult(classes, diagnostics);
    }

    /** Returns the Java release encoded by a class-file header, or zero when unknown. */
    private static int classFileRelease(byte[] bytecode) {
        if (bytecode == null || bytecode.length < 8
                || (bytecode[0] & 0xFF) != 0xCA
                || (bytecode[1] & 0xFF) != 0xFE
                || (bytecode[2] & 0xFF) != 0xBA
                || (bytecode[3] & 0xFF) != 0xBE) {
            return 0;
        }
        int major = ((bytecode[6] & 0xFF) << 8) | (bytecode[7] & 0xFF);
        return major >= 45 ? major - 44 : 0;
    }
}
