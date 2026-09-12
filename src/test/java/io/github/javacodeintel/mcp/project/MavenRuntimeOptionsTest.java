package io.github.javacodeintel.mcp.project;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class MavenRuntimeOptionsTest {
    @Test
    void systemPropertiesOverrideDefaults() {
        String previousCommand = System.getProperty("jci.maven.command");
        String previousTimeout = System.getProperty("jci.maven.timeoutSeconds");
        try {
            System.setProperty("jci.maven.command", "custom-mvn");
            System.setProperty("jci.maven.timeoutSeconds", "17");

            MavenRuntimeOptions options = MavenRuntimeOptions.fromEnvironment();

            assertThat(options.executable()).isEqualTo("custom-mvn");
            assertThat(options.timeout()).isEqualTo(Duration.ofSeconds(17));
        } finally {
            restore("jci.maven.command", previousCommand);
            restore("jci.maven.timeoutSeconds", previousTimeout);
        }
    }

    @Test
    void rejectsNonPositiveTimeout() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new MavenRuntimeOptions("mvn", Duration.ZERO));
    }

    @Test
    void defaultsToWindowsBatchCommandWhenRunningOnWindows() {
        String osName = System.getProperty("os.name");
        try {
            System.setProperty("os.name", "Windows 10");
            assertThat(MavenRuntimeOptions.defaults().executable()).isEqualTo("mvn.cmd");
        } finally {
            if (osName == null) System.clearProperty("os.name");
            else System.setProperty("os.name", osName);
        }
    }

    private static void restore(String key, String value) {
        if (value == null) System.clearProperty(key);
        else System.setProperty(key, value);
    }
}
