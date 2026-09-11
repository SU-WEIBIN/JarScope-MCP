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
        List<String> command=List.of("mvn","-q","-f",pom.toString(),"dependency:build-classpath","-Dmdep.outputAbsoluteArtifactFilename=true","-Dmdep.outputFile="+pom.getParent().resolve("target/jci-classpath.txt"));
        Process process=new ProcessBuilder(command).redirectErrorStream(false).start();
        boolean finished;
        try { finished=process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); process.destroyForcibly(); throw new IOException("Interrupted while resolving Maven dependencies", interrupted); }
        if(!finished){process.destroyForcibly();throw new IOException("Maven dependency resolution timed out: "+pom);}
        String out=new String(process.getInputStream().readAllBytes());String err=new String(process.getErrorStream().readAllBytes()); return new Result(process.exitValue(),out,err);
    }; }
}
