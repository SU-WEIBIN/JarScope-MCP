package io.github.javacodeintel.mcp.project;

import java.io.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

@FunctionalInterface
public interface MavenProcessRunner {
    Result run(Path pom) throws IOException;

    record Result(int exitCode,String stdout,String stderr) { }

    static MavenProcessRunner system() { return system(Duration.ofMinutes(2)); }
    static MavenProcessRunner system(Duration timeout) { return pom -> {
        Path outputFile=Files.createTempFile("jci-maven-classpath-", ".txt");
        Path stdoutFile=Files.createTempFile("jci-maven-stdout-", ".txt");
        Path stderrFile=Files.createTempFile("jci-maven-stderr-", ".txt");
        List<String> command=List.of("mvn","-q","-f",pom.toString(),"dependency:build-classpath","-Dmdep.outputAbsoluteArtifactFilename=true","-Dmdep.outputFile="+outputFile);
        try {
            Process process=new ProcessBuilder(command).redirectOutput(stdoutFile.toFile()).redirectError(stderrFile.toFile()).start();
            boolean finished;
            try { finished=process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); process.destroyForcibly(); throw new IOException("Interrupted while resolving Maven dependencies", interrupted); }
            if(!finished){process.destroyForcibly();throw new IOException("Maven dependency resolution timed out: "+pom);}
            String classpath=Files.exists(outputFile)?Files.readString(outputFile):Files.readString(stdoutFile);
            return new Result(process.exitValue(),classpath,Files.readString(stderrFile));
        } finally { Files.deleteIfExists(outputFile); Files.deleteIfExists(stdoutFile); Files.deleteIfExists(stderrFile); }
    }; }
}
