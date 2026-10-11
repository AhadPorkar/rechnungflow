package io.ahad.rechnungflow.server;
import io.ahad.rechnungflow.core.*;
import io.ahad.rechnungflow.ingest.*;
import io.ahad.rechnungflow.workflow.*;
import java.util.*;
import java.io.*;
/** Fictional examples run through real business services. No fabricated official PASS result. */
public final class DemoData {
    public static void seed(Store store,AuthService auth,InvoiceService invoices,OutboxService outbox,String tenant)throws IOException{
        boolean any=store.read(s->Store.table(s,"invoices").values().stream().map(Json::object).anyMatch(i->tenant.equals(Values.text(i,"tenant"))));if(any)return;
        Actor admin=auth.findActor(tenant,"admin"),intake=auth.findActor(tenant,"intake"),reviewer=auth.findActor(tenant,"reviewer"),approver=auth.findActor(tenant,"approver"),finance=auth.findActor(tenant,"finance");
        for(String name:List.of("01-ubl-office.xml","07-cii-consulting.xml")){
            var data=new InvoiceParser().parse(sample(name));var supplier=invoices.createSupplier(admin,Values.map("name",Values.text(Json.map(data,"seller"),"name"),"identity",InvoiceService.sellerKey(data),"iban",data.get("iban")));
            invoices.verifySupplier(approver,Values.text(supplier,"id"),Values.map("version",supplier.get("version"),"reason","Synthetic demo supplier. No real bank verification has taken place.","confirmedOutOfBand",true));
        }
        for(String name:List.of("01-ubl-office.xml","02-ubl-machinery.xml","03-ubl-approved.xml","04-ubl-export.xml","05-ubl-bad-total.xml","06-ubl-bank-change.xml","07-cii-consulting.xml","08-ubl-credit-note.xml")){
            var receipt=invoices.intake(intake,sample(name),name,"demo:"+name,null);String id=Values.text(receipt,"id");var i=invoices.get(admin,id);
            if(name.startsWith("02")||name.startsWith("03")||name.startsWith("04")){
                i=invoices.review(reviewer,id,Values.map("version",i.get("version"),"costCenter","OPS-100","glAccount","4930","note","Demo review of fictional service delivery."));
                i=invoices.approve(approver,id,Values.map("version",i.get("version"),"note","Demo approval only; do not pay."));
            }
            if(name.startsWith("04")){invoices.export(finance,id,Values.map("version",i.get("version")),"demo-export-004");outbox.tick();}
        }
    }
    private static byte[] sample(String name)throws IOException{try(InputStream in=DemoData.class.getResourceAsStream("/samples/"+name)){if(in==null)throw new IOException("Missing packaged sample "+name);return in.readAllBytes();}}
}
