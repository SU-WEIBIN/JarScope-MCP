package io.github.javacodeintel.mcp.decompile;

import org.jetbrains.java.decompiler.main.extern.IResultSaver;
import java.io.IOException;
import java.util.jar.Manifest;

final class DecompileResultSaver implements IResultSaver {
    private final String requested; private String source;
    DecompileResultSaver(String requested){this.requested=requested;}
    String source(){return source;}
    private boolean matches(String qualified,String entry){String normalized=requested.replace('.','/');return requested.equals(qualified)||normalized.equals(entry==null?"":entry.replace(".java",""))||normalized.equals(qualified==null?"":qualified.replace('.','/'));}
    @Override public void saveClassFile(String path,String qualifiedName,String entryName,String content,int[] mapping){if(matches(qualifiedName,entryName))source=content;}
    @Override public void saveClassEntry(String path,String archiveName,String qualifiedName,String entryName,String content){if(matches(qualifiedName,entryName))source=content;}
    @Override public void saveFolder(String path){}
    @Override public void copyFile(String source,String path,String entryName){}
    @Override public void createArchive(String path,String archiveName,Manifest manifest){}
    @Override public void saveDirEntry(String path,String archiveName,String entryName){}
    @Override public void copyEntry(String source,String path,String archiveName,String entryName){}
    @Override public void closeArchive(String path,String archiveName){}
    @Override public void close() throws IOException{}
}
