package io.ahad.rechnungflow.core;
import java.math.*;
import java.text.Normalizer;
import java.time.*;
import java.util.*;
public final class Values {
    private Values() {}
    public static Map<String,Object> map(Object... pairs) {
        if(pairs.length%2!=0) throw new IllegalArgumentException();
        Map<String,Object> m=new LinkedHashMap<>();
        for(int i=0;i<pairs.length;i+=2) m.put((String)pairs[i],pairs[i+1]); return m;
    }
    public static String text(Map<String,Object> m,String key) { return Json.text(m,key,""); }
    public static String required(Map<String,Object> m,String key,int max) {
        Object v=m.get(key); if(!(v instanceof String s)||s.isBlank()||s.length()>max) throw Problem.invalid();
        if(s.chars().anyMatch(c->c<32&&c!='\n'&&c!='\t'))throw Problem.invalid(); return s.trim();
    }
    public static BigDecimal decimal(String s) {
        if(s==null||!s.matches("-?(?:0|[1-9][0-9]{0,14})(?:\\.[0-9]{1,8})?"))throw Problem.of(422,"INVALID_DECIMAL");
        return new BigDecimal(s);
    }
    public static String money(BigDecimal d) { return d.setScale(2,RoundingMode.HALF_UP).toPlainString(); }
    public static String identity(String s) { return Normalizer.normalize(s==null?"":s,Normalizer.Form.NFKC).trim().replaceAll("\\s+"," ").toUpperCase(Locale.ROOT); }
    public static String id() { return UUID.randomUUID().toString(); }
    public static boolean uuid(String s) { return s!=null&&s.matches("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}"); }
    public static String now() { return Instant.now().toString(); }
    public static String safeFileName(String name) {
        if(name==null)return "invoice.xml";
        String n=name.replace('\\','/'); n=n.substring(n.lastIndexOf('/')+1).replaceAll("[\\p{Cntrl}]", "_");
        return n.isBlank()?"invoice.xml":n.substring(0,Math.min(140,n.length()));
    }
    public static String csv(String s) {
        if(s==null) s="";
        String check=s.stripLeading();
        if(!check.isEmpty() && "=+-@\t\r\n".indexOf(check.charAt(0))>=0)s="'"+s;
        if(!s.isEmpty()&&"\t\r\n".indexOf(s.charAt(0))>=0)s="'"+s;
        return "\""+s.replace("\"","\"\"")+"\"";
    }
    public static String xml(String s) { return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&apos;"); }
}
