package io.github.javacodeintel.mcp.index;

import java.util.*;
import java.util.regex.*;

public final class MultiReleaseClassSelector {
    private static final Pattern VERSIONED = Pattern.compile("META-INF/versions/(\\d+)/(.*\\.class)");
    private MultiReleaseClassSelector() { }

    public static List<String> select(Collection<String> entries, int targetRelease) {
        Map<String, Candidate> selected = new TreeMap<>();
        for (String entry : entries) {
            if (!entry.endsWith(".class") || entry.endsWith("module-info.class") || entry.endsWith("package-info.class")) continue;
            Matcher matcher = VERSIONED.matcher(entry);
            if (matcher.matches()) {
                int version = Integer.parseInt(matcher.group(1));
                if (version <= targetRelease) selected.merge(matcher.group(2), new Candidate(entry, version), (a,b)->a.version>=b.version?a:b);
            } else selected.putIfAbsent(entry, new Candidate(entry, 0));
        }
        return selected.values().stream().sorted(Comparator.comparing(c -> c.entry)).map(c -> c.entry).toList();
    }
    private record Candidate(String entry,int version) { }
}
