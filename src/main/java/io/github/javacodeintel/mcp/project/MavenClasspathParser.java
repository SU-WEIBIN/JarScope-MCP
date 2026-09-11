package io.github.javacodeintel.mcp.project;

import java.nio.file.Path;
import java.util.*;

public final class MavenClasspathParser {
    private MavenClasspathParser() { }
    public static List<Path> parse(String output) {
        if (output == null || output.isBlank()) return List.of();
        if (output.indexOf(';') < 0 && output.matches("[A-Za-z]:[\\\\/].*")) {
            return List.of(Path.of(output.trim()).normalize());
        }
        String separator = output.indexOf(';') >= 0 ? ";" : ":";
        LinkedHashSet<Path> result = new LinkedHashSet<>();
        for (String token : output.trim().split(java.util.regex.Pattern.quote(separator))) {
            String value = token.trim();
            if (!value.isEmpty()) result.add(Path.of(value).normalize());
        }
        return List.copyOf(result);
    }
}
