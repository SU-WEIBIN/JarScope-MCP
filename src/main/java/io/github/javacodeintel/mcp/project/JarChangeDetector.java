package io.github.javacodeintel.mcp.project;

import io.github.javacodeintel.mcp.domain.ChangeDetectionMode;

import java.io.*;
import java.nio.file.*;
import java.security.*;

public final class JarChangeDetector {
    public record Snapshot(long size,long lastModified,String sha256) { }
    public record Decision(boolean changed,String reason,Snapshot current) { }
    public Snapshot snapshot(Path jar, ChangeDetectionMode mode) throws IOException { long size=Files.size(jar), modified=Files.getLastModifiedTime(jar).toMillis(); String digest=mode==ChangeDetectionMode.SHA256?sha256(jar):null; return new Snapshot(size,modified,digest); }
    public Decision detect(Path jar, ChangeDetectionMode mode, Snapshot previous) throws IOException { if(previous==null)return new Decision(true,"missing_previous_snapshot",snapshot(jar,mode)); if(mode==ChangeDetectionMode.NONE)return new Decision(false,"change_detection_disabled",previous); Snapshot current=snapshot(jar,mode); if(mode==ChangeDetectionMode.METADATA)return new Decision(current.size()!=previous.size()||current.lastModified()!=previous.lastModified(),"metadata",current); return new Decision(!java.util.Objects.equals(current.sha256(),previous.sha256()),"sha256",current); }
    private static String sha256(Path path)throws IOException{try(InputStream in=Files.newInputStream(path)){MessageDigest digest=MessageDigest.getInstance("SHA-256");byte[] buffer=new byte[8192];for(int n;(n=in.read(buffer))>=0;)if(n>0)digest.update(buffer,0,n);StringBuilder b=new StringBuilder();for(byte v:digest.digest())b.append(String.format("%02x",v));return b.toString();}catch(NoSuchAlgorithmException e){throw new IOException("SHA-256 unavailable",e);}}
}
