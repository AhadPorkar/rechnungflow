package io.ahad.rechnungflow.workflow;
import io.ahad.rechnungflow.core.*;
import io.ahad.rechnungflow.ingest.*;
import java.math.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.zip.*;
import java.io.*;
/** Business boundaries: immutable originals, explicit review, separated approvals, transactional export outbox. */
public final class InvoiceService {
    private final Store store;private final BlobVault vault;private final ValidationEngine validator;
    private final InvoiceParser parser=new InvoiceParser();private final BasicRules basicRules=new BasicRules();
    public InvoiceService(Store store,BlobVault vault,ValidationEngine validator){this.store=store;this.vault=vault;this.validator=validator;}
    public Map<String,Object> policy(Actor actor){return store.read(s->new LinkedHashMap<>(tenant(s,actor.tenant())));}
    private static Map<String,Object> tenant(Map<String,Object>s,String id){Object o=Store.table(s,"tenants").get(id);if(o==null)throw Problem.of(404,"NOT_FOUND");return Json.object(o);}
    public Map<String,Object> updatePolicy(Actor a,Map<String,Object> b){a.require("ADMIN");
        String name=Values.required(b,"name",160),currency=Values.required(b,"currency",3);try{Currency.getInstance(currency);}catch(Exception e){throw Problem.of(400,"UNKNOWN_CURRENCY");}
        BigDecimal threshold=Values.decimal(Values.required(b,"secondApprovalThreshold",20));Problem.require(threshold.signum()>=0&&threshold.scale()<=2,400,"INVALID_INPUT");
        int years=Json.integer(b,"retentionYears",8);Problem.require(years>=8&&years<=30,400,"INVALID_INPUT");String tax=Json.text(b,"buyerTaxId","").trim();Problem.require(tax.length()<=60,400,"INVALID_INPUT");
        return store.write(s->{var t=tenant(s,a.tenant());Problem.require(Json.integer(b,"policyVersion",0)==Json.integer(t,"policyVersion",1),409,"VERSION_CONFLICT");
            t.put("name",name);t.put("currency",currency);t.put("secondApprovalThreshold",Values.money(threshold));t.put("retentionYears",years);t.put("buyerTaxId",tax);
            t.put("requirePurchaseOrder",Json.bool(b,"requirePurchaseOrder",false));t.put("policyVersion",Json.integer(t,"policyVersion",1)+1);
            Audit.append(s,a.tenant(),a.id(),"POLICY_UPDATED",a.tenant(),Values.map("version",t.get("policyVersion")));return t;});
    }
    public Map<String,Object> capabilities(){return validator.capabilities();}
    private Map<String,Object> parseAndValidate(byte[] bytes){
        Map<String,Object> data=new LinkedHashMap<>();List<Object> issues;Map<String,Object> official;
        try{data=parser.parse(bytes);issues=basicRules.check(data);}catch(Problem e){issues=new ArrayList<>();BasicRules.add(issues,e.code(),"document");}
        if(data.isEmpty())official=Values.map("status","NOT_RUN","engine","KoSIT","issues",List.of(),"report","");
        else official=validator.validate(bytes);
        return Values.map("data",data,"validation",Values.map("checkedAt",Values.now(),"basic",issues.isEmpty()?"PASS":"FAIL","issues",issues,"official",official));
    }
    public Map<String,Object> intake(Actor a,byte[] bytes,String filename,String idempotencyKey,String supersedes){
        a.require("INTAKE","ADMIN");Problem.require(bytes!=null&&bytes.length>0&&bytes.length<=SafeXml.MAX_BYTES,413,"FILE_TOO_LARGE");
        checkIdempotencyKey(idempotencyKey);String hash=Crypto.sha(bytes);String requestHash=Crypto.sha(hash+"|"+(supersedes==null?"":supersedes));
        String dedupKey=a.tenant()+":intake:"+idempotencyKey;
        Map<String,Object> replay=store.read(s->{Object o=Store.table(s,"idempotency").get(dedupKey);if(o==null)return null;var r=Json.object(o);
            Problem.require(requestHash.equals(Values.text(r,"requestHash")),409,"IDEMPOTENCY_CONFLICT");return Values.map("id",Values.text(r,"invoiceId"),"replayed",true,"duplicate",false);});
        if(replay!=null)return replay;
        Map<String,Object> parsed=parseAndValidate(bytes);archiveReport(a.tenant(),parsed);String storedHash=vault.put(a.tenant(),bytes);String safeName=Values.safeFileName(filename);
        return store.write(s->{
            var idems=Store.table(s,"idempotency");Object already=idems.get(dedupKey);if(already!=null){var r=Json.object(already);Problem.require(requestHash.equals(Values.text(r,"requestHash")),409,"IDEMPOTENCY_CONFLICT");return Values.map("id",Values.text(r,"invoiceId"),"replayed",true,"duplicate",false);}
            Optional<Map<String,Object>> exact=Store.table(s,"invoices").values().stream().map(Json::object).filter(i->a.tenant().equals(Values.text(i,"tenant"))&&hash.equals(Values.text(i,"sha256"))).findFirst();
            if(exact.isPresent()){
                String id=Values.text(exact.get(),"id");idems.put(dedupKey,Values.map("requestHash",requestHash,"invoiceId",id));
                Audit.append(s,a.tenant(),a.id(),"EXACT_DUPLICATE_RECEIVED",id,Map.of());return Values.map("id",id,"replayed",false,"duplicate",true);
            }
            Map<String,Object> old=null;
            if(supersedes!=null&&!supersedes.isBlank()){
                old=AuthService.owned(s,"invoices",supersedes,a.tenant());Problem.require(Set.of("EXCEPTION","REJECTED","REVIEW").contains(Values.text(old,"stage"))&&Json.list(old,"approvals").isEmpty(),409,"CORRECTION_NOT_ALLOWED");
            }
            var t=tenant(s,a.tenant());var data=Json.map(parsed,"data");var validation=Json.map(parsed,"validation");
            var i=Values.map("id",Values.id(),"tenant",a.tenant(),"version",1,"createdAt",Values.now(),"createdBy",a.id(),"createdByName",a.name(),
                "filename",safeName,"sha256",storedHash,"size",bytes.length,"data",data,"validation",validation,"stage",initialStage(validation,t),
                "policy",snapshotPolicy(t),"validationHistory",new ArrayList<>(List.of(Json.copy(validation))),"approvals",new ArrayList<>(),"comments",new ArrayList<>(),"metadata",new LinkedHashMap<>(),"legalHold",false,
                "retainUntil",retentionDate(data,Json.integer(t,"retentionYears",8)),"supersedes",supersedes==null?"":supersedes,"supplierId","","supplierVersion",0,"reviewedBy","","exportJobId","");
            String id=Values.text(i,"id");Store.table(s,"invoices").put(id,i);idems.put(dedupKey,Values.map("requestHash",requestHash,"invoiceId",id));
            if(old!=null){old.put("stage","SUPERSEDED");old.put("supersededBy",id);bump(old);Audit.append(s,a.tenant(),a.id(),"INVOICE_SUPERSEDED",supersedes,Values.map("replacement",id));}
            Audit.append(s,a.tenant(),a.id(),"INVOICE_RECEIVED",id,Values.map("sha256",hash,"size",bytes.length,"stage",i.get("stage")));
            return Values.map("id",id,"replayed",false,"duplicate",false);
        });
    }
    private void archiveReport(String tenant,Map<String,Object> parsed){var official=Json.map(Json.map(parsed,"validation"),"official");String report=Values.text(official,"report");if(!report.isBlank()){official.put("reportSha256",vault.put(tenant,report.getBytes(StandardCharsets.UTF_8)));}official.remove("report");}
    private Map<String,Object> snapshotPolicy(Map<String,Object> t){var p=new LinkedHashMap<String,Object>();for(String k:List.of("mode","currency","secondApprovalThreshold","policyVersion","buyerTaxId","requirePurchaseOrder"))p.put(k,t.get(k));return p;}
    private String retentionDate(Map<String,Object>d,int years){int y=LocalDate.now(ZoneOffset.UTC).getYear();try{int candidate=LocalDate.parse(Values.text(d,"issueDate")).getYear();if(candidate>=1&&candidate<=9999)y=candidate;}catch(Exception ignored){}return LocalDate.of(Math.min(9999,y+years),12,31).toString();}
    private String initialStage(Map<String,Object>v,Map<String,Object>t){if(!Values.text(v,"basic").equals("PASS"))return "EXCEPTION";
        return Values.text(t,"mode").equals("DEMO")||Values.text(Json.map(v,"official"),"status").equals("PASS")?"REVIEW":"EXCEPTION";}
    public static void checkIdempotencyKey(String key){Problem.require(key!=null&&key.matches("[A-Za-z0-9._:-]{8,120}"),400,"IDEMPOTENCY_KEY_REQUIRED");}
    public Map<String,Object> get(Actor a,String id){return store.read(s->detail(s,a,AuthService.owned(s,"invoices",id,a.tenant())));}
    public Map<String,Object> list(Actor a,String query,String stage,int page,int size){
        Problem.require(page>=0&&page<=100_000&&size>=1&&size<=100,400,"INVALID_PAGINATION");String q=(query==null?"":query).toLowerCase(Locale.ROOT);Problem.require(q.length()<=120,400,"INVALID_INPUT");
        return store.read(s->{var rows=Store.table(s,"invoices").values().stream().map(Json::object).filter(i->a.tenant().equals(Values.text(i,"tenant")))
            .filter(i->stage==null||stage.isBlank()||stage.equals(Values.text(i,"stage")))
            .filter(i->{var d=Json.map(i,"data");return (Values.text(d,"number")+" "+Values.text(Json.map(d,"seller"),"name")+" "+Values.text(i,"filename")).toLowerCase(Locale.ROOT).contains(q);})
            .sorted(Comparator.comparing((Map<String,Object>i)->Values.text(i,"createdAt")).reversed()).toList();
            return Values.map("total",rows.size(),"page",page,"pageSize",size,"items",rows.stream().skip((long)page*size).limit(size).map(i->summary(s,i)).toList());});
    }
    private Map<String,Object> summary(Map<String,Object>s,Map<String,Object>i){var d=Json.map(i,"data");return Values.map("id",i.get("id"),"version",i.get("version"),"number",d.getOrDefault("number",Values.text(i,"filename")),
        "seller",Values.text(Json.map(d,"seller"),"name"),"currency",d.getOrDefault("currency",""),"payable",Json.map(d,"totals").getOrDefault("payable","0"),"credit",d.getOrDefault("credit",false),
        "stage",i.get("stage"),"createdAt",i.get("createdAt"),"dueDate",d.getOrDefault("dueDate",""),"syntax",d.getOrDefault("syntax","UNKNOWN"),"validation",validationLabel(i),
        "approvalCount",Json.list(i,"approvals").size(),"requiredApprovals",requiredApprovals(i),"blockers",blockers(s,i),"legalHold",i.get("legalHold"));}
    private Map<String,Object> detail(Map<String,Object>s,Actor a,Map<String,Object>i){Problem.require(!a.apiKey()||a.id().equals(Values.text(i,"createdBy")),404,"NOT_FOUND");var out=Json.object(Json.copy(i));Json.map(Json.map(out,"validation"),"official").remove("report");
        out.put("blockers",blockers(s,i));out.put("requiredApprovals",requiredApprovals(i));out.put("validationLabel",validationLabel(i));
        Map<String,Object> supplier=findSupplier(s,a.tenant(),Json.map(i,"data"));out.put("supplier",supplier==null?null:supplier);
        out.put("history",Json.list(Store.table(s,"audit"),a.tenant()).stream().map(Json::object).filter(e->Values.text(i,"id").equals(Values.text(e,"entity"))).toList());return out;}
    private String validationLabel(Map<String,Object>i){var v=Json.map(i,"validation");if(!Values.text(v,"basic").equals("PASS"))return "BASIC_FAIL";
        if(Values.text(Json.map(v,"official"),"status").equals("PASS"))return "KOSIT_PASS";return Values.text(Json.map(i,"policy"),"mode").equals("DEMO")?"DEMO_ONLY":"NOT_VALIDATED";}
    public List<String> blockers(Map<String,Object>s,Map<String,Object>i){
        var d=Json.map(i,"data");var v=Json.map(i,"validation");var p=Json.map(i,"policy");List<String> b=new ArrayList<>();
        if(!Values.text(v,"basic").equals("PASS"))b.add("BASIC_VALIDATION_FAILED");
        if(!Values.text(p,"mode").equals("DEMO")&&!Values.text(Json.map(v,"official"),"status").equals("PASS"))b.add("OFFICIAL_VALIDATION_REQUIRED");
        if(d.isEmpty())return b;
        if(!Values.text(d,"currency").equals(Values.text(p,"currency")))b.add("CURRENCY_REVIEW_REQUIRED");
        String buyer=Values.text(p,"buyerTaxId");if(!buyer.isBlank()&&!Values.identity(buyer).equals(Values.identity(Values.text(Json.map(d,"buyer"),"taxId"))))b.add("BUYER_IDENTITY_MISMATCH");
        if(Json.bool(p,"requirePurchaseOrder",false)&&Values.text(d,"purchaseOrder").isBlank())b.add("PURCHASE_ORDER_REQUIRED");
        Map<String,Object> supplier=findSupplier(s,Values.text(i,"tenant"),d);
        if(supplier==null)b.add("SUPPLIER_NOT_FOUND");else{
            if(!Values.text(supplier,"status").equals("VERIFIED"))b.add("SUPPLIER_NOT_VERIFIED");
            if(!Values.identity(Values.text(supplier,"iban")).replace(" ","").equals(Values.identity(Values.text(d,"iban")).replace(" ","")))b.add("BANK_ACCOUNT_CHANGED");
        }
        String identity=businessKey(d);boolean duplicate=Store.table(s,"invoices").values().stream().map(Json::object).anyMatch(other->
            Values.text(i,"tenant").equals(Values.text(other,"tenant"))&&!Values.text(i,"id").equals(Values.text(other,"id"))&&
            !Set.of("SUPERSEDED","REJECTED").contains(Values.text(other,"stage"))&&identity.equals(businessKey(Json.map(other,"data"))));
        if(duplicate)b.add("DUPLICATE_BUSINESS");return b;
    }
    public static String sellerKey(Map<String,Object>d){var seller=Json.map(d,"seller");for(String key:List.of("taxId","registration","endpoint")){String value=Values.text(seller,key);if(!value.isBlank())return key+":"+Values.identity(value);}
        return "name:"+Values.identity(Values.text(seller,"name"))+"|"+Values.identity(Values.text(seller,"country"));}
    private String businessKey(Map<String,Object>d){return sellerKey(d)+"|"+Values.identity(Values.text(d,"number"))+"|"+Values.text(d,"type");}
    private Map<String,Object> findSupplier(Map<String,Object>s,String tenant,Map<String,Object>d){String key=sellerKey(d);return Store.table(s,"suppliers").values().stream().map(Json::object).filter(x->tenant.equals(Values.text(x,"tenant"))&&key.equals(Values.text(x,"identity"))).findFirst().orElse(null);}
    private int requiredApprovals(Map<String,Object>i){try{return BasicRules.amount(Values.text(Json.map(Json.map(i,"data"),"totals"),"payable")).abs().compareTo(BasicRules.amount(Values.text(Json.map(i,"policy"),"secondApprovalThreshold")))>=0?2:1;}catch(Exception e){return 2;}}
    private static void version(Map<String,Object>i,Map<String,Object>b){Problem.require(Json.integer(i,"version",1)==Json.integer(b,"version",0),409,"VERSION_CONFLICT");}
    private static void bump(Map<String,Object>i){i.put("version",Json.integer(i,"version",1)+1);i.put("updatedAt",Values.now());}
    private void ready(Map<String,Object>s,Map<String,Object>i){List<String>b=blockers(s,i);if(!b.isEmpty())throw Problem.of(409,b.getFirst());}
    public Map<String,Object> review(Actor a,String id,Map<String,Object>b){a.require("REVIEWER","ADMIN");String cost=Values.required(b,"costCenter",40),account=Values.required(b,"glAccount",40),note=Values.required(b,"note",2000);
        Problem.require(cost.matches("[\\p{L}\\p{N}_.-]{1,40}")&&account.matches("[\\p{L}\\p{N}_.-]{1,40}"),400,"INVALID_INPUT");
        return store.write(s->{var i=AuthService.owned(s,"invoices",id,a.tenant());version(i,b);Problem.require(Values.text(i,"stage").equals("REVIEW"),409,"INVALID_TRANSITION");
            Problem.require(!a.id().equals(Values.text(i,"createdBy")),403,"SEPARATION_OF_DUTIES");ready(s,i);
            var supplier=findSupplier(s,a.tenant(),Json.map(i,"data"));i.put("supplierId",supplier.get("id"));i.put("supplierVersion",supplier.get("version"));
            i.put("reviewedBy",a.id());i.put("reviewedByName",a.name());i.put("metadata",Values.map("costCenter",cost,"glAccount",account,"note",note));i.put("stage","APPROVAL");bump(i);
            Audit.append(s,a.tenant(),a.id(),"REVIEW_COMPLETED",id,Values.map("costCenter",cost,"glAccount",account,"policyVersion",Json.map(i,"policy").get("policyVersion")));return detail(s,a,i);});
    }
    public Map<String,Object> approve(Actor a,String id,Map<String,Object>b){a.require("APPROVER","ADMIN");String note=Values.required(b,"note",2000);
        return store.write(s->{var i=AuthService.owned(s,"invoices",id,a.tenant());version(i,b);Problem.require(Values.text(i,"stage").equals("APPROVAL"),409,"INVALID_TRANSITION");
            Problem.require(!Set.of(Values.text(i,"createdBy"),Values.text(i,"reviewedBy")).contains(a.id()),403,"SEPARATION_OF_DUTIES");ready(s,i);
            var supplier=AuthService.owned(s,"suppliers",Values.text(i,"supplierId"),a.tenant());Problem.require(Json.integer(supplier,"version",0)==Json.integer(i,"supplierVersion",0),409,"SUPPLIER_CHANGED");
            List<Object> approvals=Json.list(i,"approvals");Problem.require(approvals.stream().map(Json::object).noneMatch(x->a.id().equals(Values.text(x,"userId"))),409,"ALREADY_APPROVED");
            approvals.add(Values.map("userId",a.id(),"name",a.name(),"at",Values.now(),"note",note));
            if(approvals.size()>=requiredApprovals(i))i.put("stage","APPROVED");bump(i);
            Audit.append(s,a.tenant(),a.id(),"INVOICE_APPROVED",id,Values.map("approvalCount",approvals.size(),"required",requiredApprovals(i),"stage",i.get("stage")));return detail(s,a,i);});
    }
    public Map<String,Object> reject(Actor a,String id,Map<String,Object>b){a.require("REVIEWER","APPROVER","ADMIN");String reason=Values.required(b,"reason",2000);
        return store.write(s->{var i=AuthService.owned(s,"invoices",id,a.tenant());version(i,b);Problem.require(Set.of("EXCEPTION","REVIEW","APPROVAL","APPROVED").contains(Values.text(i,"stage")),409,"INVALID_TRANSITION");
            i.put("stage","REJECTED");i.put("rejectionReason",reason);bump(i);Audit.append(s,a.tenant(),a.id(),"INVOICE_REJECTED",id,Values.map("reason",reason));return detail(s,a,i);});
    }
    public Map<String,Object> comment(Actor a,String id,Map<String,Object>b){a.require("INTAKE","REVIEWER","APPROVER","FINANCE","ADMIN");String note=Values.required(b,"note",2000);
        return store.write(s->{var i=AuthService.owned(s,"invoices",id,a.tenant());version(i,b);Problem.require(Json.list(i,"comments").size()<500,409,"COMMENT_LIMIT");
            Json.list(i,"comments").add(Values.map("id",Values.id(),"userId",a.id(),"name",a.name(),"at",Values.now(),"note",note));bump(i);
            Audit.append(s,a.tenant(),a.id(),"COMMENT_ADDED",id,Map.of());return detail(s,a,i);});
    }
    public Map<String,Object> hold(Actor a,String id,Map<String,Object>b){a.require("FINANCE","ADMIN");boolean hold=Json.bool(b,"legalHold",true);String reason=Values.required(b,"reason",2000);
        return store.write(s->{var i=AuthService.owned(s,"invoices",id,a.tenant());version(i,b);i.put("legalHold",hold);bump(i);Audit.append(s,a.tenant(),a.id(),"RETENTION_HOLD_CHANGED",id,Values.map("held",hold,"reason",reason));return detail(s,a,i);});
    }
    public Map<String,Object> revalidate(Actor a,String id,Map<String,Object>b){a.require("REVIEWER","ADMIN");
        var current=store.read(s->AuthService.owned(s,"invoices",id,a.tenant()));version(current,b);Problem.require(Set.of("EXCEPTION","REVIEW").contains(Values.text(current,"stage")),409,"INVALID_TRANSITION");
        Problem.require(Json.list(current,"validationHistory").size()<100,409,"VALIDATION_HISTORY_LIMIT");
        var parsed=parseAndValidate(vault.get(a.tenant(),Values.text(current,"sha256")));archiveReport(a.tenant(),parsed);
        return store.write(s->{var i=AuthService.owned(s,"invoices",id,a.tenant());version(i,b);var history=Json.list(i,"validationHistory");if(history.isEmpty()){history.add(Json.copy(i.get("validation")));i.put("validationHistory",history);}history.add(Json.copy(parsed.get("validation")));i.put("validation",parsed.get("validation"));
            i.put("stage",initialStage(Json.map(parsed,"validation"),Json.map(i,"policy")));bump(i);Audit.append(s,a.tenant(),a.id(),"INVOICE_REVALIDATED",id,Values.map("label",validationLabel(i)));return detail(s,a,i);});
    }
    public List<Object> suppliers(Actor a){return store.read(s->Store.table(s,"suppliers").values().stream().map(Json::object).filter(x->a.tenant().equals(Values.text(x,"tenant"))).sorted(Comparator.comparing(x->Values.text(x,"name"))).map(x->(Object)x).toList());}
    public Map<String,Object> createSupplier(Actor a,Map<String,Object>b){a.require("REVIEWER","ADMIN");String name=Values.required(b,"name",180),identity=Values.required(b,"identity",300),iban=Json.text(b,"iban","").replace(" ","").toUpperCase(Locale.ROOT);
        Problem.require(identity.matches("(?:taxId|registration|endpoint|name):.+"),400,"INVALID_INPUT");Problem.require(iban.isEmpty()||BasicRules.ibanValid(iban),400,"IBAN_FORMAT");
        return store.write(s->{boolean exists=Store.table(s,"suppliers").values().stream().map(Json::object).anyMatch(x->a.tenant().equals(Values.text(x,"tenant"))&&identity.equals(Values.text(x,"identity")));Problem.require(!exists,409,"SUPPLIER_EXISTS");
            var v=Values.map("id",Values.id(),"tenant",a.tenant(),"name",name,"identity",identity,"iban",iban,"status","PENDING","version",1,"changedBy",a.id(),"createdAt",Values.now());Store.table(s,"suppliers").put(Values.text(v,"id"),v);
            Audit.append(s,a.tenant(),a.id(),"SUPPLIER_CREATED",Values.text(v,"id"),Map.of());return v;});
    }
    public Map<String,Object> changeSupplier(Actor a,String id,Map<String,Object>b){a.require("REVIEWER","ADMIN");String name=Values.required(b,"name",180),iban=Json.text(b,"iban","").replace(" ","").toUpperCase(Locale.ROOT);Problem.require(iban.isEmpty()||BasicRules.ibanValid(iban),400,"IBAN_FORMAT");
        return store.write(s->{var v=AuthService.owned(s,"suppliers",id,a.tenant());version(v,b);
            for(Object o:Store.table(s,"invoices").values()){var inv=Json.object(o);if(a.tenant().equals(Values.text(inv,"tenant"))&&id.equals(Values.text(inv,"supplierId"))&&Values.text(inv,"stage").equals("EXPORT_PENDING")){
                Object raw=Store.table(s,"jobs").get(Values.text(inv,"exportJobId"));if(raw!=null)Problem.require(!Set.of("IN_FLIGHT","UNKNOWN").contains(Values.text(Json.object(raw),"status")),409,"SUPPLIER_EXPORT_IN_FLIGHT");}}
            v.put("name",name);v.put("iban",iban);v.put("status","PENDING");v.put("changedBy",a.id());bump(v);
            for(Object o:Store.table(s,"invoices").values()){var i=Json.object(o);if(a.tenant().equals(Values.text(i,"tenant"))&&id.equals(Values.text(i,"supplierId"))&&Set.of("APPROVAL","APPROVED","EXPORT_PENDING").contains(Values.text(i,"stage"))){
                Object pending=Store.table(s,"jobs").get(Values.text(i,"exportJobId"));if(pending!=null)Json.object(pending).put("status","CANCELLED");i.put("exportJobId","");
                i.put("stage","REVIEW");i.put("approvals",new ArrayList<>());i.put("reviewedBy","");bump(i);Audit.append(s,a.tenant(),a.id(),"SUPPLIER_CHANGE_INVALIDATED_APPROVAL",Values.text(i,"id"),Values.map("supplierId",id));}}
            Audit.append(s,a.tenant(),a.id(),"SUPPLIER_CHANGED",id,Values.map("version",v.get("version")));return v;});
    }
    public Map<String,Object> verifySupplier(Actor a,String id,Map<String,Object>b){a.require("APPROVER","ADMIN");String reason=Values.required(b,"reason",2000);Problem.require(Json.bool(b,"confirmedOutOfBand",false),400,"VERIFICATION_CONFIRMATION_REQUIRED");
        return store.write(s->{var v=AuthService.owned(s,"suppliers",id,a.tenant());version(v,b);Problem.require(!a.id().equals(Values.text(v,"changedBy")),403,"SEPARATION_OF_DUTIES");
            Problem.require(Values.text(v,"status").equals("PENDING"),409,"INVALID_TRANSITION");v.put("status","VERIFIED");v.put("verifiedBy",a.id());v.put("verifiedAt",Values.now());bump(v);
            Audit.append(s,a.tenant(),a.id(),"SUPPLIER_VERIFIED",id,Values.map("reason",reason));return v;});
    }
    public Map<String,Object> export(Actor a,String id,Map<String,Object>b,String key){a.require("FINANCE","ADMIN");checkIdempotencyKey(key);String scope=a.tenant()+":export:"+key;
        return store.write(s->{var idems=Store.table(s,"idempotency");Object cached=idems.get(scope);if(cached!=null){var c=Json.object(cached);Problem.require(id.equals(Values.text(c,"invoiceId")),409,"IDEMPOTENCY_CONFLICT");return AuthService.owned(s,"jobs",Values.text(c,"jobId"),a.tenant());}
            var i=AuthService.owned(s,"invoices",id,a.tenant());version(i,b);Problem.require(Values.text(i,"stage").equals("APPROVED"),409,"INVALID_TRANSITION");ready(s,i);
            var supplier=AuthService.owned(s,"suppliers",Values.text(i,"supplierId"),a.tenant());Problem.require(Json.integer(supplier,"version",0)==Json.integer(i,"supplierVersion",0),409,"SUPPLIER_CHANGED");
            String jobId=Values.id();boolean demo=Values.text(Json.map(i,"policy"),"mode").equals("DEMO");
            var job=Values.map("id",jobId,"tenant",a.tenant(),"invoiceId",id,"status","PENDING","attempts",0,"createdAt",Values.now(),"createdBy",a.id(),"sandbox",demo,
                "idempotencyKey","rf:"+a.tenant()+":"+id,"payload",accountingPayload(i),"receipt","","lastError","","nextAttemptAt",0L);
            Store.table(s,"jobs").put(jobId,job);idems.put(scope,Values.map("invoiceId",id,"jobId",jobId));i.put("stage","EXPORT_PENDING");i.put("exportJobId",jobId);bump(i);
            Audit.append(s,a.tenant(),a.id(),"EXPORT_QUEUED",id,Values.map("jobId",jobId,"sandbox",demo));return job;});
    }
    private Map<String,Object> accountingPayload(Map<String,Object>i){var d=Json.map(i,"data");boolean demo=Values.text(Json.map(i,"policy"),"mode").equals("DEMO");return Values.map(
        "schemaVersion","1.0","invoiceId",i.get("id"),"documentSha256",i.get("sha256"),"invoiceNumber",d.get("number"),"issueDate",d.get("issueDate"),"dueDate",d.get("dueDate"),
        "supplier",d.get("seller"),"currency",d.get("currency"),"creditNote",d.get("credit"),"totals",d.get("totals"),"lines",d.get("lines"),"taxes",d.get("taxes"),
        "coding",i.get("metadata"),"approvals",i.get("approvals"),"policyVersion",Json.map(i,"policy").get("policyVersion"),"sandboxOnly",demo,"validation",validationLabel(i),"validationEvidence",Values.map("checkedAt",Json.map(i,"validation").get("checkedAt"),"official",Json.map(Json.map(i,"validation"),"official")));}
    public byte[] original(Actor a,String id){var i=store.read(s->AuthService.owned(s,"invoices",id,a.tenant()));return vault.get(a.tenant(),Values.text(i,"sha256"));}
    public byte[] report(Actor a,String id){return report(a,id,-1);}
    public byte[] report(Actor a,String id,int attempt){return store.read(s->{var i=AuthService.owned(s,"invoices",id,a.tenant());Map<String,Object> validation=Json.map(i,"validation");
        if(attempt>=0){var history=Json.list(i,"validationHistory");Problem.require(attempt<history.size(),404,"REPORT_UNAVAILABLE");validation=Json.object(history.get(attempt));}
        String hash=Values.text(Json.map(validation,"official"),"reportSha256");Problem.require(!hash.isBlank(),404,"REPORT_UNAVAILABLE");return vault.get(a.tenant(),hash);});}
    public byte[] evidence(Actor a,String id){a.require("ADMIN","FINANCE","AUDITOR","REVIEWER","APPROVER");var i=store.read(s->detail(s,a,AuthService.owned(s,"invoices",id,a.tenant())));
        try{ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(ZipOutputStream zip=new ZipOutputStream(bytes,StandardCharsets.UTF_8)){
            entry(zip,"original.xml",original(a,id));entry(zip,"case.json",Json.stringify(i).getBytes(StandardCharsets.UTF_8));
            try{entry(zip,"validation-report.xml",report(a,id));}catch(Problem e){if(e.status()!=404)throw e;}
            for(int n=0;n<Json.list(i,"validationHistory").size();n++)try{entry(zip,"validation-history/report-"+(n+1)+".xml",report(a,id,n));}catch(Problem e){if(e.status()!=404)throw e;}
            entry(zip,"manifest.json",Json.stringify(Values.map("application","RechnungFlow","version","0.1.0","originalSha256",i.get("sha256"),"exportedAt",Values.now(),"notAWormArchive",true,"mode",Json.map(i,"policy").get("mode"))).getBytes(StandardCharsets.UTF_8));
        }return bytes.toByteArray();}catch(IOException e){throw new UncheckedIOException(e);}
    }
    private void entry(ZipOutputStream zip,String name,byte[] b)throws IOException{ZipEntry e=new ZipEntry(name);e.setTime(0);zip.putNextEntry(e);zip.write(b);zip.closeEntry();}
    public Map<String,Object> dashboard(Actor a){return store.read(s->{List<Map<String,Object>> all=Store.table(s,"invoices").values().stream().map(Json::object).filter(i->a.tenant().equals(Values.text(i,"tenant"))).toList();
        Map<String,Object>counts=new LinkedHashMap<>(),amounts=new LinkedHashMap<>();for(String stage:List.of("EXCEPTION","REVIEW","APPROVAL","APPROVED","EXPORT_PENDING","EXPORTED","REJECTED","SUPERSEDED"))counts.put(stage,all.stream().filter(i->stage.equals(Values.text(i,"stage"))).count());
        int overdue=0;LocalDate today=LocalDate.now(ZoneOffset.UTC);
        for(var i:all){var d=Json.map(i,"data");if(Set.of("REJECTED","SUPERSEDED","EXPORTED").contains(Values.text(i,"stage")))continue;
            try{BigDecimal value=BasicRules.amount(Values.text(Json.map(d,"totals"),"payable"));if(Json.bool(d,"credit",false))value=value.negate();String c=Values.text(d,"currency");amounts.put(c,new BigDecimal(String.valueOf(amounts.getOrDefault(c,"0"))).add(value).toPlainString());}catch(Exception ignored){}
            try{if(LocalDate.parse(Values.text(d,"dueDate")).isBefore(today))overdue++;}catch(Exception ignored){}
        }
        return Values.map("total",all.size(),"counts",counts,"openAmounts",amounts,"overdue",overdue,"recent",all.stream().sorted(Comparator.comparing((Map<String,Object>x)->Values.text(x,"createdAt")).reversed()).limit(7).map(i->summary(s,i)).toList(),
            "policy",tenant(s,a.tenant()),"validator",validator.capabilities(),"jobsPending",Store.table(s,"jobs").values().stream().map(Json::object).filter(j->a.tenant().equals(Values.text(j,"tenant"))&&!Values.text(j,"status").equals("DELIVERED")).count());});}
    public Map<String,Object> audit(Actor a,int page,int size){a.require("AUDITOR","ADMIN","FINANCE");Problem.require(page>=0&&page<=100_000&&size>=1&&size<=100,400,"INVALID_PAGINATION");
        return store.read(s->{var rows=Json.list(Store.table(s,"audit"),a.tenant());List<Object>reverse=new ArrayList<>(rows);Collections.reverse(reverse);return Values.map("total",rows.size(),"integrityValid",Audit.verify(rows),"items",reverse.stream().skip((long)page*size).limit(size).toList());});}
    public byte[] csv(Actor a){a.require("FINANCE","AUDITOR","ADMIN");return store.read(s->{StringBuilder b=new StringBuilder("\uFEFF\"Invoice\";\"Supplier\";\"Currency\";\"Payable\";\"CreditNote\";\"Stage\";\"SHA256\"\r\n");
        for(Object o:Store.table(s,"invoices").values()){var i=Json.object(o);if(!a.tenant().equals(Values.text(i,"tenant")))continue;var d=Json.map(i,"data");
            b.append(String.join(";",List.of(Values.csv(Values.text(d,"number")),Values.csv(Values.text(Json.map(d,"seller"),"name")),Values.csv(Values.text(d,"currency")),Values.csv(Values.text(Json.map(d,"totals"),"payable")),Values.csv(String.valueOf(d.getOrDefault("credit",false))),Values.csv(Values.text(i,"stage")),Values.csv(Values.text(i,"sha256"))))).append("\r\n");}
        return b.toString().getBytes(StandardCharsets.UTF_8);});}
}
