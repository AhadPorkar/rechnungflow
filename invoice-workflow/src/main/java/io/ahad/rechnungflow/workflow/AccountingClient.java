package io.ahad.rechnungflow.workflow;
import java.util.*;
public interface AccountingClient {
    record Delivery(String outcome,String receipt,String code){}
    Delivery deliver(String key,Map<String,Object> payload);
    default boolean configured(){return true;}
    static AccountingClient unavailable(){return new AccountingClient(){
        public Delivery deliver(String key,Map<String,Object> payload){return new Delivery("BLOCKED","","ACCOUNTING_NOT_CONFIGURED");}
        public boolean configured(){return false;}
    };}
}
