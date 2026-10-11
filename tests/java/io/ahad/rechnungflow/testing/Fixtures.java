package io.ahad.rechnungflow.testing;
import io.ahad.rechnungflow.core.*;
import io.ahad.rechnungflow.ingest.*;
import io.ahad.rechnungflow.workflow.*;
import java.nio.file.*;import java.util.*;import java.nio.charset.StandardCharsets;
public final class Fixtures implements AutoCloseable {
    public static final String PASSWORD="Synthetic-test-passphrase-2026!";
    private static final Map<String,Object> BASE;
    static {MemoryStore s=new MemoryStore();new AuthService(s).bootstrap("test","Test workspace",PASSWORD,true);BASE=s.read(x->x);}
    public final MemoryStore store=new MemoryStore(); public final Path directory;
    public final InvoiceService service;public final Map<String,Actor> actors=new HashMap<>();
    public Fixtures()throws Exception{this(false,ValidationEngine.unavailable());}
    public Fixtures(boolean strict,ValidationEngine engine)throws Exception{
        store.write(s->{s.clear();s.putAll(Json.object(Json.copy(BASE)));if(strict)Json.object(Store.table(s,"tenants").get("test")).put("mode","STRICT");return null;});
        directory=Files.createTempDirectory("rf-unit-");service=new InvoiceService(store,new BlobVault(directory,store.key()),engine);
        store.read(s->{for(Object o:Store.table(s,"users").values()){var u=Json.object(o);actors.put(Values.text(u,"login"),new Actor(Values.text(u,"id"),"test",Values.text(u,"name"),new HashSet<>(Json.list(u,"roles").stream().map(String::valueOf).toList()),false));}return null;});
    }
    public Actor a(String login){return actors.get(login);}
    public Actor withRoles(String login,String...roles){Actor a=a(login);return new Actor(a.id(),a.tenant(),a.name(),Set.of(roles),false);}
    public static byte[] xml(String file)throws Exception{return Files.readAllBytes(Path.of("examples/invoices/"+file));}
    public static byte[] changed(String file,String before,String after)throws Exception{return new String(xml(file),StandardCharsets.UTF_8).replace(before,after).getBytes(StandardCharsets.UTF_8);}
    public String intake(String file)throws Exception{return intakeBytes(xml(file),a("intake"));}
    public String intakeBytes(byte[] bytes,Actor actor){return Values.text(service.intake(actor,bytes,"invoice.xml",Values.id(),null),"id");}
    public Map<String,Object> get(String id){return service.get(a("admin"),id);}
    public Map<String,Object> version(String id,Object...more){var b=Values.map("version",get(id).get("version"));b.putAll(Values.map(more));return b;}
    public Map<String,Object> supplier(String file)throws Exception{
        var d=new InvoiceParser().parse(xml(file));var s=service.createSupplier(a("reviewer"),Values.map("name",Values.text(Json.map(d,"seller"),"name"),"identity",InvoiceService.sellerKey(d),"iban",d.get("iban")));
        return service.verifySupplier(a("approver"),Values.text(s,"id"),Values.map("version",s.get("version"),"reason","Synthetic verification only","confirmedOutOfBand",true));
    }
    public String ready(String file)throws Exception{supplier(file);return intake(file);}
    public Map<String,Object> review(String id){return service.review(a("reviewer"),id,version(id,"costCenter","IT-100","glAccount","4930","note","Invoice checked in test."));}
    public Map<String,Object> approve(String id,String login){return service.approve(a(login),id,version(id,"note","Approval test."));}
    public String approved(String file)throws Exception{String id=ready(file);review(id);approve(id,"approver");if(Values.text(get(id),"stage").equals("APPROVAL"))approve(id,"approver2");return id;}
    public Map<String,Object> queue(String id){return service.export(a("finance"),id,version(id),Values.id());}
    public static ValidationEngine stub(String status){return new ValidationEngine(){public Map<String,Object>capabilities(){return Values.map("configured",true,"engine","SYNTHETIC_TEST_DOUBLE");}public Map<String,Object>validate(byte[] x){return Values.map("status",status,"engine","SYNTHETIC_TEST_DOUBLE","report","<report synthetic=\"true\"/>");}};}
    public void close()throws Exception{try(var walk=Files.walk(directory)){for(Path p:walk.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(p);}store.close();}
}
