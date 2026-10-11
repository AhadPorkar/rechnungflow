package io.ahad.rechnungflow.core;
import java.nio.file.*;
import java.io.*;
/** Tenant-bound content-addressed originals. AAD prevents cross-tenant or digest substitution. */
public final class BlobVault {
    private final Path root; private final byte[] key;
    public BlobVault(Path root,byte[] key)throws IOException{this.root=root.toAbsolutePath().normalize();this.key=key.clone();Files.createDirectories(this.root);FileStore.restrict(this.root,true);}
    private Path path(String tenant,String hash){Problem.require(tenant.matches("[a-z0-9-]{1,50}")&&hash.matches("[a-f0-9]{64}"),400,"INVALID_INPUT");return root.resolve(tenant).resolve(hash+".enc");}
    public synchronized String put(String tenant,byte[] bytes){
        String hash=Crypto.sha(bytes);Path p=path(tenant,hash);
        try{
            if(Files.exists(p)){get(tenant,hash);return hash;}
            Files.createDirectories(p.getParent());FileStore.restrict(p.getParent(),true);
            FileStore.atomic(p,Crypto.encrypt(key,bytes,tenant+":"+hash));return hash;
        }catch(IOException e){throw Problem.of(503,"STORAGE_WRITE_FAILED");}
    }
    public byte[] get(String tenant,String hash){
        Path p=path(tenant,hash);
        try{
            Problem.require(Files.size(p)<=10_485_792,500,"STORAGE_INTEGRITY");
            byte[] b=Crypto.decrypt(key,Files.readAllBytes(p),tenant+":"+hash);
            Problem.require(Crypto.equal(hash,Crypto.sha(b)),500,"STORAGE_INTEGRITY");return b;
        }catch(IOException e){throw Problem.of(500,"ORIGINAL_UNAVAILABLE");}
    }
}
