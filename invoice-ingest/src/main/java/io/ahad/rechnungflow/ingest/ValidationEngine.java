package io.ahad.rechnungflow.ingest;
import java.util.*;
import io.ahad.rechnungflow.core.*;
public interface ValidationEngine {
    Map<String,Object> validate(byte[] original);
    Map<String,Object> capabilities();
    static ValidationEngine unavailable(){return new ValidationEngine(){
        public Map<String,Object> validate(byte[] b){return Values.map("status","UNAVAILABLE","engine","KoSIT","version","1.6.3","configuration","2026-08-31","issues",List.of(),"report","");}
        public Map<String,Object> capabilities(){return Values.map("configured",false,"engine","KoSIT","version","1.6.3","configuration","2026-08-31");}
    };}
}
