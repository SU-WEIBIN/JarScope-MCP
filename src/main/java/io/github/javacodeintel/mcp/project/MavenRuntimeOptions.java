package io.github.javacodeintel.mcp.project;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;

/** Configuration for the Maven process started by the indexer. */
public record MavenRuntimeOptions(String executable, Duration timeout) {
    public MavenRuntimeOptions {
        if (executable == null || executable.isBlank()) {
            throw new IllegalArgumentException("Maven executable must not be blank");
        }
        Objects.requireNonNull(timeout, "timeout");
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("Maven timeout must be positive");
        }
    }

    public static MavenRuntimeOptions defaults() {
        return new MavenRuntimeOptions(isWindows() ? "mvn.cmd" : "mvn", Duration.ofMinutes(2));
    }

    /**
     * Reads service-level Maven settings. System properties take precedence over
     * environment variables so an MCP launch configuration can be self-contained.
     */
    public static MavenRuntimeOptions fromEnvironment() {
        MavenRuntimeOptions defaults = defaults();
        String executable = firstNonBlank(
                System.getProperty("jci.maven.command"),
                System.getenv("JCI_MAVEN_COMMAND"));
        if (executable == null) {
            String mavenHome = firstNonBlank(System.getProperty("maven.home"), System.getenv("MAVEN_HOME"));
            executable = mavenHome == null ? defaults.executable() :
                    Path.of(mavenHome, "bin", isWindows() ? "mvn.cmd" : "mvn").toString();
        }

        String timeoutValue = firstNonBlank(
                System.getProperty("jci.maven.timeoutSeconds"),
                System.getenv("JCI_MAVEN_TIMEOUT_SECONDS"));
        Duration timeout = defaults.timeout();
        if (timeoutValue != null) {
            try {
                timeout = Duration.ofSeconds(Long.parseLong(timeoutValue));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("jci.maven.timeoutSeconds must be a positive integer", e);
            }
        }
        return new MavenRuntimeOptions(executable, timeout);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value.trim();
        }
        return null;
    }

    static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }
}
