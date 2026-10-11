package io.ahad.rechnungflow.ingest;
import io.ahad.rechnungflow.core.*;
import java.math.*;
import java.time.*;
import java.util.*;
/** Application plausibility checks, explicitly NOT a substitute for EN16931/XRechnung Schematron. */
public final class BasicRules {
    public List<Object> check(Map<String,Object> invoice){
        List<Object> issues=new ArrayList<>();
        for(String k:List.of("number","issueDate","currency","type"))if(Values.text(invoice,k).isBlank())add(issues,"REQUIRED_VALUE",k);
        for(String p:List.of("seller","buyer"))if(Values.text(Json.map(invoice,p),"name").isBlank())add(issues,"REQUIRED_VALUE",p+".name");
        for(String k:List.of("issueDate","dueDate")){String s=Values.text(invoice,k);if(!s.isBlank())try{if(!s.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}"))throw new IllegalArgumentException();LocalDate date=LocalDate.parse(s);if(date.getYear()<1)throw new IllegalArgumentException();}catch(Exception e){add(issues,"INVALID_DATE",k);}}
        try{Currency.getInstance(Values.text(invoice,"currency"));}catch(Exception e){add(issues,"UNKNOWN_CURRENCY","currency");}
        String type=Values.text(invoice,"type");if(!Set.of("380","381","384","389").contains(type))add(issues,"UNSUPPORTED_TYPE","type");
        if(Values.text(invoice,"syntax").equals("UBL_CREDIT_NOTE")&&!type.equals("381"))add(issues,"TYPE_ROOT_MISMATCH","type");
        if(Values.text(invoice,"syntax").equals("UBL_INVOICE")&&type.equals("381"))add(issues,"TYPE_ROOT_MISMATCH","type");
        String profile=Values.text(invoice,"profile").toLowerCase(Locale.ROOT);
        if(profile.contains("minimum")||profile.contains("basicwl")||profile.contains("basic-wl"))add(issues,"INSUFFICIENT_PROFILE","profile");
        Map<String,Object> totals=Json.map(invoice,"totals");Map<String,BigDecimal> money=new HashMap<>();
        for(String k:List.of("lineNet","net","tax","gross","allowances","charges","prepaid","rounding","payable")){
            try{BigDecimal d=amount(Values.text(totals,k));money.put(k,d);if(d.scale()>2)add(issues,"AMOUNT_PRECISION","totals."+k);}
            catch(Exception e){add(issues,"AMOUNT_FORMAT","totals."+k);}
        }
        if(money.size()==9){
            match(issues,money.get("net").add(money.get("tax")),money.get("gross"),"gross");
            match(issues,money.get("gross").subtract(money.get("prepaid")).add(money.get("rounding")),money.get("payable"),"payable");
            match(issues,money.get("lineNet").subtract(money.get("allowances")).add(money.get("charges")),money.get("net"),"net");
        }
        List<Object> lines=Json.list(invoice,"lines");if(lines.isEmpty())add(issues,"LINES_MISSING","lines");
        BigDecimal sum=BigDecimal.ZERO;boolean all=true;Set<String> ids=new HashSet<>();int index=0;
        for(Object o:lines){var l=Json.object(o);String field="lines."+(index++);String id=Values.text(l,"id");
            if(id.isBlank()||!ids.add(id))add(issues,"LINE_ID_INVALID",field+".id");
            if(Values.text(l,"description").isBlank())add(issues,"REQUIRED_VALUE",field+".description");
            try{BigDecimal net=amount(Values.text(l,"net"));sum=sum.add(net);if(net.scale()>2)add(issues,"AMOUNT_PRECISION",field+".net");}
            catch(Exception e){all=false;add(issues,"AMOUNT_FORMAT",field+".net");}
            for(String k:List.of("quantity","unitPrice"))try{amount(Values.text(l,k));}catch(Exception e){add(issues,"AMOUNT_FORMAT",field+"."+k);}
        }
        if(all&&money.containsKey("lineNet")&&sum.compareTo(money.get("lineNet"))!=0)add(issues,"LINE_SUM_MISMATCH","totals.lineNet");
        BigDecimal tax=BigDecimal.ZERO;boolean allTax=true;
        for(Object o:Json.list(invoice,"taxes")){var t=Json.object(o);try{tax=tax.add(amount(Values.text(t,"amount")));}catch(Exception e){allTax=false;add(issues,"AMOUNT_FORMAT","taxes.amount");}}
        if(allTax&&!Json.list(invoice,"taxes").isEmpty()&&money.containsKey("tax")&&tax.compareTo(money.get("tax"))!=0)add(issues,"TAX_SUM_MISMATCH","totals.tax");
        String iban=Values.text(invoice,"iban");if(!iban.isBlank()&&!ibanValid(iban))add(issues,"IBAN_FORMAT","iban");
        return issues;
    }
    public static BigDecimal amount(String s){
        if(s==null||!s.trim().matches("[+-]?(?:[0-9]{1,15}(?:\\.[0-9]{0,8})?|\\.[0-9]{1,8})"))throw Problem.of(422,"INVALID_DECIMAL");
        return new BigDecimal(s.trim());
    }
    private void match(List<Object> issues,BigDecimal expected,BigDecimal actual,String field){if(expected.compareTo(actual)!=0)add(issues,"TOTAL_MISMATCH","totals."+field);}
    public static void add(List<Object> issues,String code,String field){issues.add(Values.map("code",code,"severity","ERROR","field",field));}
    public static boolean ibanValid(String raw){
        String s=raw.replace(" ","").toUpperCase(Locale.ROOT);if(!s.matches("[A-Z]{2}[0-9]{2}[A-Z0-9]{11,30}"))return false;
        Map<String,Integer> lengths=Map.ofEntries(Map.entry("DE",22),Map.entry("GB",22),Map.entry("FR",27),Map.entry("NL",18),Map.entry("AT",20),Map.entry("CH",21),Map.entry("BE",16),Map.entry("ES",24),Map.entry("IT",27),Map.entry("IR",26));
        if(lengths.containsKey(s.substring(0,2))&&s.length()!=lengths.get(s.substring(0,2)))return false;
        s=s.substring(4)+s.substring(0,4);int mod=0;
        for(char c:s.toCharArray()){if(c>='A'&&c<='Z'){int v=c-'A'+10;mod=(mod*100+v)%97;}else mod=(mod*10+c-'0')%97;}return mod==1;
    }
}
