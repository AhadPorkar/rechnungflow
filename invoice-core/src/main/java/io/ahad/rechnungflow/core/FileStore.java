package io.ahad.rechnungflow.core;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.nio.channels.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.io.*;
import java.util.*;
import java.util.function.Function;
/** Encrypted, single-process transactional snapshot store. Not a distributed database or WORM archive. */
public final class FileStore implements Store {
    private final Path root,stateFile; private final byte[] key;
    private final FileChannel channel; private final FileLock lock;
    private Map<String,Object> state;
    private boolean closed;
    public FileStore(Path directory,byte[] suppliedKey) throws IOException {
        root=directory.toAbsolutePath().normalize();Files.createDirectories(root);restrict(root,true);
        stateFile=root.resolve("state.enc");channel=FileChannel.open(root.resolve("instance.lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE);
        FileLock acquired=null;
        try{acquired=channel.tryLock();}catch(OverlappingFileLockException e){channel.close();throw Problem.of(409,"INSTANCE_LOCKED");}
        if(acquired==null){channel.close();throw Problem.of(409,"INSTANCE_LOCKED");}lock=acquired;
        try {
            Path keyFile=root.resolve("master.key");
            if(suppliedKey!=null){Problem.require(suppliedKey.length==32,500,"KEY_LENGTH");key=suppliedKey.clone();}
            else if(Files.exists(keyFile)){key=Files.readAllBytes(keyFile);Problem.require(key.length==32,500,"KEY_LENGTH");}
            else {
                Problem.require(!Files.exists(stateFile),500,"KEY_MISSING"); key=Crypto.random(32);
                Files.write(keyFile,key,StandardOpenOption.CREATE_NEW);restrict(keyFile,false);
            }
            if(Files.exists(stateFile)) {
                Problem.require(Files.size(stateFile)<=67_108_896,500,"STORAGE_LIMIT");
                state=Json.object(Json.parseStored(new String(Crypto.decrypt(key,Files.readAllBytes(stateFile),"rechnungflow-state-v1"),StandardCharsets.UTF_8)));
                Problem.require(Json.integer(state,"schema",0)==1,500,"SCHEMA_UNSUPPORTED");
            }else{state=Store.initial();persist(state);}
        }catch(RuntimeException|IOException e){lock.release();channel.close();throw e;}
    }
    @Override public synchronized <T>T read(Function<Map<String,Object>,T> operation){ensure();return operation.apply(Json.object(Json.copy(state)));}
    @Override public synchronized <T>T write(Function<Map<String,Object>,T> operation){
        ensure();Map<String,Object> next=Json.object(Json.copy(state));T result=operation.apply(next);
        try{persist(next);}catch(IOException e){throw Problem.of(503,"STORAGE_WRITE_FAILED");}
        state=Json.object(Json.copy(next));return result;
    }
    private void persist(Map<String,Object> next) throws IOException {
        byte[] plain=Json.stringify(next).getBytes(StandardCharsets.UTF_8);
        Problem.require(plain.length<=67_108_864,507,"STORAGE_LIMIT");
        atomic(stateFile,Crypto.encrypt(key,plain,"rechnungflow-state-v1"));
    }
    public static void atomic(Path path,byte[] bytes) throws IOException {
        Files.createDirectories(path.getParent());Path temp=Files.createTempFile(path.getParent(),".rf-", ".tmp");restrict(temp,false);
        try{
            try(FileChannel c=FileChannel.open(temp,StandardOpenOption.WRITE)){ByteBuffer b=ByteBuffer.wrap(bytes);while(b.hasRemaining())c.write(b);c.force(true);}
            // Refuse a non-atomic fallback: the old state remains intact on unsupported filesystems.
            Files.move(temp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
            restrict(path,false);
            try(FileChannel directory=FileChannel.open(path.getParent(),StandardOpenOption.READ)){directory.force(true);}catch(IOException|UnsupportedOperationException ignored){}
        }finally{Files.deleteIfExists(temp);}
    }
    public static void restrict(Path path,boolean directory) throws IOException {
        if(Files.getFileStore(path).supportsFileAttributeView(PosixFileAttributeView.class))
            Files.setPosixFilePermissions(path,PosixFilePermissions.fromString(directory?"rwx------":"rw-------"));
    }
    private void ensure(){if(closed)throw new IllegalStateException("Store closed");}
    @Override public byte[] key(){return key.clone();}
    @Override public synchronized void close(){if(!closed){closed=true;try{lock.release();channel.close();}catch(IOException e){throw new UncheckedIOException(e);}}}
}
