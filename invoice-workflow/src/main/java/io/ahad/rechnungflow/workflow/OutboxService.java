package io.ahad.rechnungflow.workflow;
import io.ahad.rechnungflow.core.*;
import java.util.*;
import java.util.concurrent.*;
/** Durable outbox: claim before delivery, never silently repeat an uncertain write. */
public final class OutboxService implements AutoCloseable {
    private final Store store;private final InvoiceService invoices;private final AccountingClient client;
    private ScheduledExecutorService executor;
    public OutboxService(Store store,InvoiceService invoices,AccountingClient client){this.store=store;this.invoices=invoices;this.client=client;}
    public boolean configured(){return client.configured();}
    public void recover(){store.write(s->{for(Object o:Store.table(s,"jobs").values()){var j=Json.object(o);if(Values.text(j,"status").equals("IN_FLIGHT")){
        j.put("status","UNKNOWN");j.put("lastError","RESTART_DURING_DELIVERY");Audit.append(s,Values.text(j,"tenant"),"system","EXPORT_RECOVERY_REQUIRED",Values.text(j,"invoiceId"),Values.map("jobId",j.get("id")));}}return null;});}
    public synchronized void start(){if(executor!=null)return;recover();executor=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"accounting-outbox");t.setDaemon(true);return t;});
        executor.scheduleWithFixedDelay(()->{try{tick();}catch(Exception e){System.err.println("Outbox attempt failed; inspect encrypted job state.");}},1,2,TimeUnit.SECONDS);}
    public boolean tick(){
        Map<String,Object> claimed=store.write(s->{var selected=Store.table(s,"jobs").values().stream().map(Json::object).filter(j->Values.text(j,"status").equals("PENDING")&&Json.number(j,"nextAttemptAt",0)<=System.currentTimeMillis()).findFirst();
            if(selected.isEmpty())return null;var j=selected.get();var i=AuthService.owned(s,"invoices",Values.text(j,"invoiceId"),Values.text(j,"tenant"));
            List<String>b=invoices.blockers(s,i);Object sup=Store.table(s,"suppliers").get(Values.text(i,"supplierId"));
            if(sup==null||Json.integer(Json.object(sup),"version",0)!=Json.integer(i,"supplierVersion",0))b.add("SUPPLIER_CHANGED");
            if(!Values.text(i,"stage").equals("EXPORT_PENDING")||!Values.text(i,"exportJobId").equals(Values.text(j,"id")))b.add("EXPORT_STATE_CHANGED");
            if(!b.isEmpty()){j.put("status","BLOCKED");j.put("lastError",b.getFirst());return null;}
            j.put("status","IN_FLIGHT");j.put("attempts",Json.integer(j,"attempts",0)+1);j.put("startedAt",Values.now());return j;});
        if(claimed==null)return false;
        AccountingClient.Delivery delivery;
        if(Json.bool(claimed,"sandbox",false)){
            delivery=store.write(s->{String key=Values.text(claimed,"idempotencyKey");var ledger=Store.table(s,"sandbox");Object old=ledger.get(key);String receipt;
                if(old!=null)receipt=Values.text(Json.object(old),"receiptId");else{receipt="SIM-"+Crypto.sha(key).substring(0,16);ledger.put(key,Values.map("receiptId",receipt,"invoiceId",claimed.get("invoiceId"),"tenant",claimed.get("tenant"),"payloadHash",Crypto.sha(Json.canonical(claimed.get("payload")))));}
                return new AccountingClient.Delivery("DELIVERED",receipt,"");});
        }else {
            try{delivery=client.deliver(Values.text(claimed,"idempotencyKey"),Json.map(claimed,"payload"));}
            catch(RuntimeException unexpected){delivery=new AccountingClient.Delivery("UNKNOWN","","DELIVERY_UNCERTAIN");}
        }
        if(delivery==null||!Set.of("DELIVERED","FAILED","BLOCKED","UNKNOWN","RETRY").contains(delivery.outcome())||
            (delivery.outcome().equals("DELIVERED")&&(delivery.receipt()==null||delivery.receipt().isBlank()||delivery.receipt().length()>200)))
            delivery=new AccountingClient.Delivery("UNKNOWN","","ACKNOWLEDGEMENT_INVALID");
        final AccountingClient.Delivery result=delivery;
        store.write(s->{var j=AuthService.owned(s,"jobs",Values.text(claimed,"id"),Values.text(claimed,"tenant"));if(!Values.text(j,"status").equals("IN_FLIGHT"))return null;
            String status=result.outcome();if(status.equals("RETRY")){status=Json.integer(j,"attempts",0)<5?"PENDING":"FAILED";j.put("nextAttemptAt",System.currentTimeMillis()+Math.min(300_000L,1000L<<(Json.integer(j,"attempts",1)+1)));}
            j.put("status",status);j.put("receipt",result.receipt());j.put("lastError",result.code());j.put("updatedAt",Values.now());
            if(status.equals("DELIVERED")){var i=AuthService.owned(s,"invoices",Values.text(j,"invoiceId"),Values.text(j,"tenant"));i.put("stage","EXPORTED");i.put("version",Json.integer(i,"version",1)+1);i.put("exportedAt",Values.now());}
            Audit.append(s,Values.text(j,"tenant"),"system","EXPORT_"+status,Values.text(j,"invoiceId"),Values.map("jobId",j.get("id"),"code",result.code()));return null;});return true;
    }
    public List<Object> jobs(Actor a){a.require("FINANCE","AUDITOR","ADMIN");return store.read(s->Store.table(s,"jobs").values().stream().map(Json::object).filter(j->a.tenant().equals(Values.text(j,"tenant"))).sorted(Comparator.comparing((Map<String,Object>j)->Values.text(j,"createdAt")).reversed()).map(j->{var v=new LinkedHashMap<>(j);v.remove("payload");return (Object)v;}).toList());}
    public Map<String,Object> reconcile(Actor a,String id,Map<String,Object>b){a.require("FINANCE","ADMIN");String reason=Values.required(b,"reason",2000),decision=Values.required(b,"decision",30);
        Problem.require(Set.of("CONFIRM_DELIVERED","CONFIRM_NOT_DELIVERED","RETRY_FAILED").contains(decision),400,"INVALID_INPUT");
        return store.write(s->{var j=AuthService.owned(s,"jobs",id,a.tenant());String status=Values.text(j,"status");
            if(decision.equals("RETRY_FAILED"))Problem.require(Set.of("FAILED","BLOCKED").contains(status),409,"INVALID_TRANSITION");
            else Problem.require(status.equals("UNKNOWN"),409,"INVALID_TRANSITION");
            var i=AuthService.owned(s,"invoices",Values.text(j,"invoiceId"),a.tenant());
            if(decision.equals("CONFIRM_DELIVERED")){
                String receipt=Values.required(b,"receipt",200);j.put("receipt",receipt);j.put("status","DELIVERED");i.put("stage","EXPORTED");i.put("version",Json.integer(i,"version",1)+1);i.put("exportedAt",Values.now());
            }else{
                Problem.require(invoices.blockers(s,i).isEmpty(),409,"EXPORT_BLOCKED");j.put("status","PENDING");j.put("nextAttemptAt",0);j.put("lastError","");
            }
            Audit.append(s,a.tenant(),a.id(),"EXPORT_RECONCILED",Values.text(j,"invoiceId"),Values.map("jobId",id,"decision",decision,"reason",reason));var v=new LinkedHashMap<>(j);v.remove("payload");return v;});
    }
    @Override public synchronized void close(){if(executor!=null){executor.shutdownNow();try{executor.awaitTermination(10,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}}}
}
