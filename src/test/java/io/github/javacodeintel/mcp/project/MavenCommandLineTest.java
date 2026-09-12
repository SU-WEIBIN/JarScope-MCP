package io.github.javacodeintel.mcp.project;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MavenCommandLineTest {
    @Test
    void effectivePomCommandOnlyRunsTheHelpGoal() {
        Path output = Path.of("temp/effective.xml");
        List<String> command = MavenCommandLine.effectivePom(
                "mvn", Path.of("project/pom.xml"), output);

        assertThat(command).contains("help:effective-pom", "-Doutput=" + output);
        assertThat(command).noneMatch(argument -> List.of("compile", "package", "install", "test").contains(argument));
    }

    @Test
    void classpathCommandOnlyRunsTheDependencyGoal() {
        Path output = Path.of("temp/classpath.txt");
        List<String> command = MavenCommandLine.classpath(
                "mvn", Path.of("probe/pom.xml"), output, true);

        assertThat(command).contains(
                "dependency:build-classpath",
                "-Dmdep.outputAbsoluteArtifactFilename=true",
                "-Dmdep.outputFile=" + output,
                "-Dmdep.includeScope=test");
        assertThat(command).noneMatch(argument -> List.of("compile", "package", "install", "test").contains(argument));
    }
}
