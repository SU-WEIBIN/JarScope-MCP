package io.github.javacodeintel.mcp.project;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public record ProjectCacheLayout(Path root, Path database, Path jars, Path decompiled) {
    public static ProjectCacheLayout forProject(Path project) { return forProject(project, Path.of(System.getProperty("user.home"), ".java-code-intelligence")); }
    public static ProjectCacheLayout forProject(Path project, Path cacheRoot) { String id=hex(project.toAbsolutePath().normalize().toString().getBytes(StandardCharsets.UTF_8)); Path root=cacheRoot.resolve(id); return new ProjectCacheLayout(root,root.resolve("index.db"),root.resolve("jars"),root.resolve("decompiled")); }
    public void createDirectories() throws java.io.IOException { Files.createDirectories(root); Files.createDirectories(jars); Files.createDirectories(decompiled); }
    private static String hex(byte[] data){try{byte[] hash=MessageDigest.getInstance("SHA-256").digest(data);StringBuilder b=new StringBuilder();for(byte v:hash)b.append(String.format("%02x",v));return b.toString();}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
}
