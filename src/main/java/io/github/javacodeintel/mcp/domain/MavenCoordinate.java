package io.github.javacodeintel.mcp.domain;
public record MavenCoordinate(String groupId, String artifactId, String version, String classifier) { public MavenCoordinate(String groupId, String artifactId, String version) { this(groupId, artifactId, version, null); } }
