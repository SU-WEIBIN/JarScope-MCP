package io.github.javacodeintel.mcp.project;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MavenClasspathParserTest {
    @Test
    void parsesWindowsAndUnixSeparatorsWithoutSplittingDriveLetters() {
        assertThat(MavenClasspathParser.parse("C:\\repo\\a.jar;D:\\repo\\b.jar;C:\\repo\\a.jar"))
                .containsExactly(Path.of("C:/repo/a.jar").normalize(), Path.of("D:/repo/b.jar").normalize());
        assertThat(MavenClasspathParser.parse("/repo/a.jar:/repo/b.jar:/repo/a.jar"))
                .containsExactly(Path.of("/repo/a.jar").normalize(), Path.of("/repo/b.jar").normalize());
    }
}
