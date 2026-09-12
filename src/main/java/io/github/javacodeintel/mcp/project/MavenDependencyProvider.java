package io.github.javacodeintel.mcp.project;

import io.github.javacodeintel.mcp.config.ProjectOptions;
import io.github.javacodeintel.mcp.domain.*;
import io.github.javacodeintel.mcp.ports.DependencyProvider;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

public final class MavenDependencyProvider implements DependencyProvider {
    private final MavenProcessRunner runner;
    private final PomModuleDiscovery modules;
    private final MavenEffectivePomParser parser;

    public MavenDependencyProvider() {
        this(MavenProcessRunner.system());
    }

    public MavenDependencyProvider(MavenProcessRunner runner) {
        this.runner = Objects.requireNonNull(runner);
        this.modules = new PomModuleDiscovery();
        this.parser = new MavenEffectivePomParser();
    }

    @Override
    public DependencyResolution resolve(Path projectPath, ProjectOptions options) throws IOException {
        Path root = projectPath.toAbsolutePath().normalize();
        Path rootPom = Files.isDirectory(root) ? root.resolve("pom.xml") : root;
        List<Path> modulePoms = modules.discover(rootPom);
        MavenProcessRunner.Result effective = runner.effectivePom(rootPom);
        List<IndexDiagnostic> diagnostics = new ArrayList<>();
        if (effective.stdout() == null || effective.stdout().isBlank()) {
            throw new IOException("Maven effective POM resolution failed for " + rootPom + ": " + effective.stderr());
        }
        if (effective.exitCode() != 0) {
            diagnostics.add(new IndexDiagnostic(
                    "MAVEN_EFFECTIVE_POM_PARTIAL",
                    "Maven returned exit code " + effective.exitCode() + " while producing an effective POM: " + effective.stderr(),
                    "WARNING", null, true));
        }
        MavenReactorModel reactor = parser.parse(effective.stdout(), rootPom, modulePoms);
        LinkedHashMap<Path, JarArtifact> result = new LinkedHashMap<>();
        int successfulProbes = 0;
        for (MavenModuleModel module : reactor.modules()) {
            Path probeDirectory = Files.createTempDirectory("jci-maven-probe-");
            try {
                Path probePom = probeDirectory.resolve("pom.xml");
                Files.writeString(probePom, MavenExternalProbePom.render(module, reactor, options.includeTestDependencies()));
                MavenProcessRunner.Result execution = runner.run(probePom, options.includeTestDependencies());
                List<Path> jars = MavenClasspathParser.parse(execution.stdout());
                if (execution.exitCode() != 0 && jars.isEmpty()) {
                    diagnostics.add(new IndexDiagnostic(
                            "MAVEN_MODULE_RESOLUTION_FAILED",
                            "Maven dependency resolution failed for " + module.pomPath() + ": " + execution.stderr(),
                            "ERROR", null, true));
                    continue;
                }
                successfulProbes++;
                if (execution.exitCode() != 0) {
                    diagnostics.add(new IndexDiagnostic(
                            "MAVEN_PARTIAL_RESOLUTION",
                            "Maven returned exit code " + execution.exitCode() + " for " + module.pomPath()
                                    + ", but usable classpath entries were returned: " + execution.stderr(),
                            "WARNING", null, true));
                }
                for (Path jar : jars) {
                    Path normalized = jar.toAbsolutePath().normalize();
                    if (Files.isRegularFile(normalized) && normalized.toString().toLowerCase(Locale.ROOT).endsWith(".jar")) {
                        result.putIfAbsent(normalized, new JarArtifact(normalized, inferCoordinate(normalized)));
                    }
                }
            } finally {
                deleteTree(probeDirectory);
            }
        }
        if (successfulProbes == 0 && !reactor.modules().isEmpty()) {
            throw new IOException("Maven dependency resolution produced no usable classpath: " + diagnostics);
        }
        return new DependencyResolution(reactor, List.copyOf(result.values()), List.copyOf(diagnostics));
    }

    private static void deleteTree(Path directory) {
        if (directory == null || !Files.exists(directory)) {
            return;
        }
        try (var paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // A failed cleanup must not invalidate an otherwise usable resolution.
                }
            });
        } catch (IOException ignored) {
            // See comment above.
        }
    }
    private static MavenCoordinate inferCoordinate(Path jar){String file=jar.getFileName().toString();String base=file.substring(0,file.length()-4);Path version=jar.getParent();Path artifact=version==null?null:version.getParent();if(version==null||artifact==null)return new MavenCoordinate(null,null,null);String versionName=version.getFileName().toString();String artifactName=artifact.getFileName().toString();String artifactPrefix=artifactName+"-";if(base.startsWith(artifactPrefix+versionName))return new MavenCoordinate(null,artifactName,versionName,null);return new MavenCoordinate(null,artifactName,versionName,null);}
}
