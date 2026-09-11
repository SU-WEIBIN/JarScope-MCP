package io.github.javacodeintel.mcp.project;

import java.io.*;
import java.nio.channels.*;
import java.nio.file.*;

public final class ProjectWriteLock implements AutoCloseable {
    private final FileChannel channel; private final FileLock lock;
    private ProjectWriteLock(FileChannel channel,FileLock lock){this.channel=channel;this.lock=lock;}
    public static ProjectWriteLock acquire(Path lockFile)throws IOException{if(lockFile.getParent()!=null)Files.createDirectories(lockFile.getParent());FileChannel channel=FileChannel.open(lockFile,StandardOpenOption.CREATE,StandardOpenOption.WRITE);try{FileLock lock=channel.lock();return new ProjectWriteLock(channel,lock);}catch(IOException e){channel.close();throw e;}}
    @Override public void close() throws IOException {try{lock.release();}finally{channel.close();}}
}
