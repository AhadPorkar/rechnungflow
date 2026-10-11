package io.ahad.rechnungflow.core;
import java.util.*;
public final class Audit {
    private Audit(){}
    public static void append(Map<String,Object> state,String tenant,String actor,String action,String entity,Map<String,Object> detail){
        Map<String,Object> all=Store.table(state,"audit");
        @SuppressWarnings("unchecked") List<Object> rows=(List<Object>)all.computeIfAbsent(tenant,k->new ArrayList<>());
        String previous=rows.isEmpty()?"GENESIS":Values.text(Json.object(rows.get(rows.size()-1)),"hash");
        var event=Values.map("sequence",rows.size()+1,"at",Values.now(),"actor",actor,"action",action,"entity",entity,"detail",detail,"previous",previous);
        event.put("hash",Crypto.sha(Json.canonical(event)));rows.add(event);
    }
    public static boolean verify(List<Object> rows){
        String previous="GENESIS";int sequence=0;
        for(Object o:rows){var row=Json.object(Json.copy(o));String hash=Values.text(row,"hash");row.remove("hash");
            if(Json.integer(row,"sequence",0)!=++sequence||!previous.equals(Values.text(row,"previous"))||!Crypto.equal(hash,Crypto.sha(Json.canonical(row))))return false;previous=hash;}
        return true;
    }
}
