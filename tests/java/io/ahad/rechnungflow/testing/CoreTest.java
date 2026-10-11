package io.ahad.rechnungflow.testing;
import io.ahad.rechnungflow.core.*;
import io.ahad.rechnungflow.core.FileStore;
import java.nio.file.*;import java.util.*;import java.math.*;import java.nio.charset.StandardCharsets;
import static io.ahad.rechnungflow.testing.TestKit.*;
public final class CoreTest {
    public static void main(String[]args)throws Exception{
        TestKit t=new TestKit("core");
        t.test("JSON nested round trip",()->{var x=Values.map("a",List.of(1,true,"Persian: \u0633\u0644\u0627\u0645"),"null",null);eq(Json.canonical(x),Json.canonical(Json.parse(Json.stringify(x))));});
        t.test("JSON canonical key ordering",()->eq("{\"a\":1,\"b\":2}",Json.canonical(Values.map("b",2,"a",1))));
        t.test("JSON null",()->eq(null,Json.parse("null")));
        t.test("JSON whitespace",()->eq("x",Json.parse(" \n\t\"x\" \r")));
        t.test("JSON escaped unicode",()->eq("\u20ac",Json.parse("\"\\u20ac\"")));
        t.test("JSON surrogate pair",()->eq("\ud83d\ude00",Json.parse("\"\\ud83d\\ude00\"")));
        t.test("JSON exponent parsed precisely",()->eq(new BigDecimal("1000"),new BigDecimal(Json.parse("1e3").toString()).stripTrailingZeros().setScale(0)));
        for(String invalid:List.of("{\"a\":1,\"a\":2}","[1,]","{\"a\":}","{a:1}","01","NaN","Infinity","1 2","\"\\uD800\"","\"\\uDC00\"","\"line\n\"","[","null x"))
            t.test("JSON rejects "+invalid.replace("\n","\\n"),()->reject(()->Json.parse(invalid)));
        t.test("JSON depth limited",()->reject(()->Json.parse("[".repeat(100)+"0"+"]".repeat(100))));
        t.test("JSON input size bounded",()->reject(()->Json.parse(" ".repeat(4_194_305))));
        t.test("JSON does not serialize nonfinite",()->reject(()->Json.stringify(Double.NaN)));
        t.test("required trims surrounding whitespace",()->eq("ABC",Values.required(Values.map("x"," ABC "),"x",10)));
        t.test("required rejects blank",()->error("INVALID_INPUT",()->Values.required(Values.map("x"," "),"x",10)));
        t.test("required rejects wrong type",()->error("INVALID_INPUT",()->Values.required(Values.map("x",1),"x",10)));
        t.test("required rejects long values",()->error("INVALID_INPUT",()->Values.required(Values.map("x","1234"),"x",3)));
        t.test("required rejects NUL",()->error("INVALID_INPUT",()->Values.required(Values.map("x","ab\0"),"x",10)));
        t.test("decimal exact cents",()->eq(new BigDecimal("12.30"),Values.decimal("12.30")));
        t.test("decimal rejects exponent",()->reject(()->Values.decimal("1e2")));
        t.test("identity normalization",()->eq(Values.identity("ABC"),Values.identity("\uff21\uff22\uff23")));
        t.test("filename eliminates path traversal",()->yes(!Values.safeFileName("../../x.xml").contains("/")));
        t.test("filename strips CRLF",()->yes(!Values.safeFileName("x\r\n.xml").contains("\n")));
        t.test("UUID validated",()->yes(Values.uuid(Values.id())));
        t.test("UUID traversal invalid",()->yes(!Values.uuid("../a")));
        t.test("CSV quotes doubled",()->yes(Values.csv("a\"b").contains("\"\"")));
        for(String formula:List.of("=1+1","+SUM(A1)","-1+1","@cmd","\t=cmd","\r=cmd"))
            t.test("CSV neutralizes formula "+formula,()->yes(Values.csv(formula).contains("'")));
        t.test("XML escapes markup",()->eq("&lt;&amp;&quot;&apos;&gt;",Values.xml("<&\"'>")));
        t.test("SHA256 known answer",()->eq("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",Crypto.sha("abc")));
        t.test("tokens random and URL-safe",()->{String a=Crypto.token();yes(a.matches("[A-Za-z0-9_-]{43}"));yes(!a.equals(Crypto.token()));});
        t.test("AES-GCM round trip",()->{byte[] key=Crypto.random(32),p="secret".getBytes();yes(Arrays.equals(p,Crypto.decrypt(key,Crypto.encrypt(key,p,"tenant"),"tenant")));});
        t.test("AES-GCM nonce uniqueness",()->{byte[]k=Crypto.random(32);yes(!Arrays.equals(Crypto.encrypt(k,new byte[0],"x"),Crypto.encrypt(k,new byte[0],"x")));});
        t.test("AES-GCM wrong tenant denied",()->{byte[]k=Crypto.random(32);error("STORAGE_INTEGRITY",()->Crypto.decrypt(k,Crypto.encrypt(k,new byte[10],"a"),"b"));});
        t.test("AES-GCM tampering denied",()->{byte[]k=Crypto.random(32),b=Crypto.encrypt(k,new byte[10],"a");b[b.length-1]^=1;error("STORAGE_INTEGRITY",()->Crypto.decrypt(k,b,"a"));});
        t.test("AES-GCM wrong key denied",()->error("STORAGE_INTEGRITY",()->Crypto.decrypt(Crypto.random(32),Crypto.encrypt(Crypto.random(32),new byte[10],"a"),"a")));
        t.test("AES-GCM short ciphertext denied",()->error("STORAGE_INTEGRITY",()->Crypto.decrypt(Crypto.random(32),new byte[2],"a")));
        t.test("password hashes verify",()->{String h=Crypto.password(Fixtures.PASSWORD);yes(Crypto.verify(Fixtures.PASSWORD,h));yes(!Crypto.verify("wrong password",h));});
        t.test("password salts unique",()->yes(!Crypto.password(Fixtures.PASSWORD).equals(Crypto.password(Fixtures.PASSWORD))));
        t.test("password too short denied",()->error("PASSWORD_POLICY",()->Crypto.password("short")));
        t.test("password too long denied",()->error("PASSWORD_POLICY",()->Crypto.password("x".repeat(129))));
        t.test("malformed hashes fail closed",()->{for(String h:List.of("","foo","pbkdf2-sha256$1$AA==$AA==","pbkdf2-sha256$90000000$a$b"))yes(!Crypto.verify("secret",h));});
        t.test("MemoryStore read isolation",()->{MemoryStore s=new MemoryStore();s.read(x->{x.put("secret",1);return null;});yes(!s.read(x->x.containsKey("secret")));});
        t.test("MemoryStore returned write isolation",()->{MemoryStore s=new MemoryStore();var x=s.write(v->{v.put("value",1);return v;});x.put("value",9);eq(1,s.read(v->Json.integer(v,"value",0)));});
        t.test("MemoryStore rollback",()->{MemoryStore s=new MemoryStore();reject(()->s.write(x->{x.put("bad",1);throw new IllegalStateException();}));yes(!s.read(x->x.containsKey("bad")));});
        t.test("FileStore persists encrypted state",()->{Path p=Files.createTempDirectory("rf-store-");try(FileStore s=new FileStore(p,null)){s.write(v->{v.put("secret","unique-plaintext-marker");return null;});yes(!new String(Files.readAllBytes(p.resolve("state.enc")),StandardCharsets.ISO_8859_1).contains("unique-plaintext-marker"));}try(FileStore s=new FileStore(p,null)){eq("unique-plaintext-marker",s.read(v->Values.text(v,"secret")));}clean(p);});
        t.test("FileStore exclusive lock",()->{Path p=Files.createTempDirectory("rf-lock-");try(FileStore s=new FileStore(p,null)){error("INSTANCE_LOCKED",()->new FileStore(p,null));}clean(p);});
        t.test("FileStore supplied key not written",()->{Path p=Files.createTempDirectory("rf-key-");try(FileStore s=new FileStore(p,Crypto.random(32))){yes(!Files.exists(p.resolve("master.key")));}clean(p);});
        t.test("FileStore invalid key rejected",()->{Path p=Files.createTempDirectory("rf-key-");error("KEY_LENGTH",()->new FileStore(p,new byte[16]));clean(p);});
        t.test("FileStore missing key refuses reset",()->{Path p=Files.createTempDirectory("rf-key-");try(FileStore s=new FileStore(p,null)){}Files.delete(p.resolve("master.key"));error("KEY_MISSING",()->new FileStore(p,null));clean(p);});
        t.test("FileStore corrupt snapshot rejected",()->{Path p=Files.createTempDirectory("rf-corrupt-");try(FileStore s=new FileStore(p,null)){}byte[]b=Files.readAllBytes(p.resolve("state.enc"));b[b.length-1]^=1;Files.write(p.resolve("state.enc"),b);error("STORAGE_INTEGRITY",()->new FileStore(p,null));clean(p);});
        t.test("FileStore rollback remains after reopen",()->{Path p=Files.createTempDirectory("rf-rollback-");try(FileStore s=new FileStore(p,null)){reject(()->s.write(x->{x.put("bad",1);throw new IllegalStateException();}));}try(FileStore s=new FileStore(p,null)){yes(!s.read(x->x.containsKey("bad")));}clean(p);});
        t.test("FileStore close prevents reads",()->{Path p=Files.createTempDirectory("rf-close-");FileStore s=new FileStore(p,null);s.close();reject(()->s.read(x->x));clean(p);});
        t.test("BlobVault preserves exact bytes",()->{Path p=Files.createTempDirectory("rf-blob-");BlobVault v=new BlobVault(p,Crypto.random(32));byte[] b="\ufeff<invoice>\r\n</invoice>".getBytes(StandardCharsets.UTF_8);String hash=v.put("a",b);yes(Arrays.equals(b,v.get("a",hash)));eq(hash,v.put("a",b));clean(p);});
        t.test("BlobVault tenant boundary",()->{Path p=Files.createTempDirectory("rf-blob-");BlobVault v=new BlobVault(p,Crypto.random(32));String h=v.put("a",new byte[]{1});error("ORIGINAL_UNAVAILABLE",()->v.get("b",h));clean(p);});
        t.test("BlobVault path traversal denied",()->{Path p=Files.createTempDirectory("rf-blob-");BlobVault v=new BlobVault(p,Crypto.random(32));error("INVALID_INPUT",()->v.get("../a","0".repeat(64)));clean(p);});
        t.test("BlobVault corrupted blob fails integrity",()->{Path p=Files.createTempDirectory("rf-blob-");BlobVault v=new BlobVault(p,Crypto.random(32));String h=v.put("a",new byte[]{1});Files.write(p.resolve("a/"+h+".enc"),new byte[]{0});error("STORAGE_INTEGRITY",()->v.get("a",h));clean(p);});
        t.test("Audit hash-chain verifies",()->{var s=Store.initial();Audit.append(s,"a","user","CREATED","id",Map.of());Audit.append(s,"a","user","UPDATED","id",Map.of());yes(Audit.verify(Json.list(Store.table(s,"audit"),"a")));});
        t.test("Audit detects changed record",()->{var s=Store.initial();Audit.append(s,"a","user","CREATED","id",Map.of());var rows=Json.list(Store.table(s,"audit"),"a");Json.object(rows.getFirst()).put("action","FAKED");yes(!Audit.verify(rows));});
        t.test("Audit detects removed interior record",()->{var s=Store.initial();for(int n=0;n<3;n++)Audit.append(s,"a","u","X","i",Map.of());var rows=Json.list(Store.table(s,"audit"),"a");rows.remove(1);yes(!Audit.verify(rows));});
        t.test("Audit tenant separation",()->{var s=Store.initial();Audit.append(s,"a","u","A","i",Map.of());Audit.append(s,"b","u","B","i",Map.of());eq(1,Json.list(Store.table(s,"audit"),"a").size());});
        t.finish("build/test-results/core.xml");
    }
    static void reject(TestKit.Check c){try{c.run();}catch(Exception e){return;}throw new AssertionError("Expected rejection");}
    static void clean(Path p)throws Exception{try(var walk=Files.walk(p)){for(Path f:walk.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(f);}}
}
