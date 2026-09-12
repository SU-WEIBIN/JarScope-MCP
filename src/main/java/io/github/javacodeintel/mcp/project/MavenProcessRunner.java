package io.github.javacodeintel.mcp.project;

import java.io.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

@FunctionalInterface
public interface MavenProcessRunner {
    Result run(Path pom) throws IOException;

    /** Allows production runners to add options derived from the index request. */
    default Result run(Path pom, boolean includeTestDependencies) throws IOException {
        return run(pom);
    }

    /** Reads the effective project model without running a build lifecycle phase. */
    default Result effectivePom(Path pom) throws IOException {
        return new Result(0, Files.readString(pom), "");
    }

    record Result(int exitCode,String stdout,String stderr) { }

    static MavenProcessRunner system() { return system(MavenRuntimeOptions.fromEnvironment()); }

    static MavenProcessRunner system(Duration timeout) {
        MavenRuntimeOptions configured = MavenRuntimeOptions.fromEnvironment();
        return system(new MavenRuntimeOptions(configured.executable(), timeout));
    }

    static MavenProcessRunner system(MavenRuntimeOptions options) {
        Objects.requireNonNull(options, "options");
        return new MavenProcessRunner() {
            @Override
            public Result run(Path pom) throws IOException {
                return executeClasspath(pom, false);
            }

            @Override
            public Result run(Path pom, boolean includeTestDependencies) throws IOException {
                return executeClasspath(pom, includeTestDependencies);
            }

            @Override
            public Result effectivePom(Path pom) throws IOException {
                Path outputFile = Files.createTempFile("jci-maven-effective-pom-", ".xml");
                return execute(pom, outputFile, MavenCommandLine.effectivePom(
                        options.executable(), pom, outputFile));
            }

            private Result executeClasspath(Path pom, boolean includeTestDependencies) throws IOException {
                Path outputFile=Files.createTempFile("jci-maven-classpath-", ".txt");
                return execute(pom, outputFile, MavenCommandLine.classpath(
                        options.executable(), pom, outputFile, includeTestDependencies));
            }

            private Result execute(Path pom, Path outputFile, List<String> command) throws IOException {
                Path stdoutFile=Files.createTempFile("jci-maven-stdout-", ".txt");
                Path stderrFile=Files.createTempFile("jci-maven-stderr-", ".txt");
                try {
                    Process process=new ProcessBuilder(command).redirectOutput(stdoutFile.toFile()).redirectError(stderrFile.toFile()).start();
                    boolean finished;
                    try { finished=process.waitFor(options.timeout().toMillis(), TimeUnit.MILLISECONDS); }
                    catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); process.destroyForcibly(); throw new IOException("Interrupted while resolving Maven dependencies", interrupted); }
                    if(!finished){process.destroyForcibly();throw new IOException("Maven dependency resolution timed out: "+pom);}
                    String output = Files.exists(outputFile) ? Files.readString(outputFile) : "";
                    if (output.isBlank()) {
                        output = Files.readString(stdoutFile);
                    }
                    return new Result(process.exitValue(),output,Files.readString(stderrFile));
                } finally { Files.deleteIfExists(outputFile); Files.deleteIfExists(stdoutFile); Files.deleteIfExists(stderrFile); }
            }
        };
    }
}
