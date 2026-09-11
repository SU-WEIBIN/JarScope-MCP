package io.github.javacodeintel.mcp.project;

import io.github.javacodeintel.mcp.config.ProjectOptions;
import io.github.javacodeintel.mcp.domain.*;
import io.github.javacodeintel.mcp.ports.DependencyProvider;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

public final class MavenDependencyProvider implements DependencyProvider {
    private final MavenProcessRunner runner; private final PomModuleDiscovery modules;
    public MavenDependencyProvider(){this(MavenProcessRunner.system());}
    public MavenDependencyProvider(MavenProcessRunner runner){this.runner=Objects.requireNonNull(runner);this.modules=new PomModuleDiscovery();}
    @Override public List<JarArtifact> resolve(Path projectPath, ProjectOptions options) throws IOException {
        Path root=projectPath.toAbsolutePath().normalize(); Path rootPom=Files.isDirectory(root)?root.resolve("pom.xml"):root; List<Path> poms=new ArrayList<>();poms.add(rootPom);poms.addAll(modules.discover(rootPom)); LinkedHashMap<Path,JarArtifact> result=new LinkedHashMap<>();
        for(Path pom:poms){MavenProcessRunner.Result execution=runner.run(pom);if(execution.exitCode()!=0)throw new IOException("Maven dependency resolution failed for "+pom+": "+execution.stderr());String cp=execution.stdout();Path output=pom.getParent().resolve("target/jci-classpath.txt");if(Files.exists(output)){cp=Files.readString(output);try{Files.deleteIfExists(output);}catch(IOException ignored){}}for(Path jar:MavenClasspathParser.parse(cp)){if(Files.isRegularFile(jar)&&jar.toString().endsWith(".jar"))result.putIfAbsent(jar, new JarArtifact(jar,inferCoordinate(jar)));}}
        return List.copyOf(result.values());
    }
    private static MavenCoordinate inferCoordinate(Path jar){String file=jar.getFileName().toString();String base=file.substring(0,file.length()-4);Path version=jar.getParent();Path artifact=version==null?null:version.getParent();if(version==null||artifact==null)return new MavenCoordinate(null,null,null);String versionName=version.getFileName().toString();String artifactName=artifact.getFileName().toString();String artifactPrefix=artifactName+"-";if(base.startsWith(artifactPrefix+versionName))return new MavenCoordinate(null,artifactName,versionName,null);return new MavenCoordinate(null,artifactName,versionName,null);}
}
