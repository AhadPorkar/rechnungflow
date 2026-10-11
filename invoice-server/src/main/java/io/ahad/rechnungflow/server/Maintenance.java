package io.ahad.rechnungflow.server;
import io.ahad.rechnungflow.core.*;import io.ahad.rechnungflow.core.FileStore;
import java.nio.file.*;import java.util.*;
/** Offline verification and encrypted snapshot. Refuses a running instance through the exclusive store lock. */
public final class Maintenance {
    public static void main(String[]args)throws Exception{
        if(args.length<2||!Set.of("verify","snapshot").contains(args[0]))throw new IllegalArgumentException("Usage: Maintenance verify DATA | Maintenance snapshot DATA NEW_TARGET");
        Path root=Path.of(args[1]).toAbsolutePath();Problem.require(Files.isRegularFile(root.resolve("state.enc")),404,"STATE_NOT_FOUND");
        String encoded=System.getenv("RF_MASTER_KEY");byte[] key=encoded==null||encoded.isBlank()?null:Base64.getDecoder().decode(encoded);
        try(FileStore store=new FileStore(root,key)){
            var state=store.read(s->s);BlobVault vault=new BlobVault(root.resolve("originals"),store.key());Set<String> blobs=new TreeSet<>();
            for(Object o:Store.table(state,"invoices").values()){
                var i=Json.object(o);String tenant=Values.text(i,"tenant"),sha=Values.text(i,"sha256");vault.get(tenant,sha);blobs.add("originals/"+tenant+"/"+sha+".enc");
                for(Object raw:Json.list(i,"validationHistory")){String prior=Values.text(Json.map(Json.object(raw),"official"),"reportSha256");if(!prior.isBlank()){vault.get(tenant,prior);blobs.add("originals/"+tenant+"/"+prior+".enc");}}
                String report=Values.text(Json.map(Json.map(i,"validation"),"official"),"reportSha256");if(!report.isBlank()){vault.get(tenant,report);blobs.add("originals/"+tenant+"/"+report+".enc");}
            }
            for(Object raw:Store.table(state,"audit").values()){
                @SuppressWarnings("unchecked") List<Object> rows=(List<Object>)raw;Problem.require(Audit.verify(rows),500,"AUDIT_INTEGRITY");
            }
            int referencedBlobs=blobs.size();
            if(args[0].equals("snapshot")){
                if(args.length!=3)throw new IllegalArgumentException("Specify a NEW target directory");Path target=Path.of(args[2]).toAbsolutePath();Problem.require(!Files.exists(target),409,"TARGET_EXISTS");Files.createDirectories(target);FileStore.restrict(target,true);
                blobs.add("state.enc");var hashes=new LinkedHashMap<String,Object>();
                for(String name:blobs){Path source=root.resolve(name),dest=target.resolve(name);Files.createDirectories(dest.getParent());FileStore.restrict(dest.getParent(),true);Files.copy(source,dest);FileStore.restrict(dest,false);hashes.put(name,Crypto.sha(Files.readAllBytes(dest)));}
                FileStore.atomic(target.resolve("snapshot-manifest.json"),Json.stringify(Values.map("application","RechnungFlow","version","0.1.0","createdAt",Values.now(),"masterKeyIncluded",false,"files",hashes)).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                System.out.println("Encrypted snapshot complete. Master key NOT included; escrow it separately. Restore into a new directory, provide its key, then run verify.");
            }
            System.out.println(Json.stringify(Values.map("verified",true,"invoices",Store.table(state,"invoices").size(),"referencedBlobs",referencedBlobs,"auditTenants",Store.table(state,"audit").size(),"notAWormProof",true)));
        }
    }
}
