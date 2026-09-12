package io.github.javacodeintel.mcp.project;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Builds Maven import commands without invoking a build lifecycle phase. */
final class MavenCommandLine {
    private MavenCommandLine() {
    }

    static List<String> effectivePom(String executable, Path pom, Path output) {
        List<String> command = base(executable);
        command.addAll(List.of(
                "-q",
                "-f",
                pom.toString(),
                "help:effective-pom",
                "-Doutput=" + output));
        return List.copyOf(command);
    }

    static List<String> classpath(String executable, Path pom, Path output, boolean includeTests) {
        List<String> command = base(executable);
        command.addAll(List.of(
                "-q",
                "-f",
                pom.toString(),
                "dependency:build-classpath",
                "-Dmdep.outputAbsoluteArtifactFilename=true",
                "-Dmdep.outputFile=" + output));
        if (includeTests) {
            command.add("-Dmdep.includeScope=test");
        }
        return List.copyOf(command);
    }

    private static List<String> base(String executable) {
        List<String> command = new ArrayList<>();
        String lower = executable.toLowerCase(Locale.ROOT);
        boolean windowsScript = MavenRuntimeOptions.isWindows()
                && (lower.endsWith(".cmd") || lower.endsWith(".bat"));
        if (windowsScript) {
            command.add("cmd.exe");
            command.add("/c");
            command.add('"' + executable + '"');
        } else {
            command.add(executable);
        }
        return command;
    }
}
