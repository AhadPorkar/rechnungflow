package io.ahad.rechnungflow.core;
import java.util.*;
import java.util.function.Function;
/** Transaction functions must not perform network I/O. A thrown exception rolls back the complete change. */
public interface Store extends AutoCloseable {
    <T> T read(Function<Map<String,Object>,T> operation);
    <T> T write(Function<Map<String,Object>,T> operation);
    byte[] key();
    @Override void close();
    static Map<String,Object> initial() {
        var m=Values.map("schema",1);
        for(String name:List.of("tenants","users","suppliers","invoices","jobs","sessions","keys","idempotency","audit","sandbox"))m.put(name,new LinkedHashMap<>());
        return m;
    }
    static Map<String,Object> table(Map<String,Object> state,String table){return Json.map(state,table);}
}
