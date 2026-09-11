package io.github.javacodeintel.mcp.index;

import io.github.javacodeintel.mcp.config.ProjectOptions;
import io.github.javacodeintel.mcp.domain.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.*;
import java.util.jar.*;

public final class JarClassScanner {
    private final AsmBytecodeParser parser;
    public JarClassScanner(AsmBytecodeParser parser){this.parser=Objects.requireNonNull(parser,"parser");}
    public ScanResult scan(JarArtifact jar, ProjectOptions options) throws IOException {
        List<ParsedClass> classes=new ArrayList<>(); List<IndexDiagnostic> diagnostics=new ArrayList<>(); List<String> entries=new ArrayList<>();
        try(JarFile file=new JarFile(jar.path().toFile())) { Enumeration<JarEntry> enumeration=file.entries(); while(enumeration.hasMoreElements())entries.add(enumeration.nextElement().getName()); for(String entry:MultiReleaseClassSelector.select(entries,options.targetJavaRelease())) { JarEntry jarEntry=file.getJarEntry(entry); try(InputStream input=file.getInputStream(jarEntry)){classes.add(parser.parse(input.readAllBytes(),jar));}catch(Exception e){diagnostics.add(new IndexDiagnostic("CLASS_PARSE_FAILED",e.getMessage()==null?e.getClass().getSimpleName():e.getMessage(),"warning",entry,true));} } }
        return new ScanResult(classes,diagnostics);
    }
}
