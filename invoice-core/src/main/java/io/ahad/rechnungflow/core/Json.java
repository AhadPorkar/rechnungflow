package io.ahad.rechnungflow.core;
import java.math.BigDecimal;
import java.util.*;
/** Dependency-free bounded JSON codec. Rejects duplicate keys, non-finite numbers and lone surrogates. */
public final class Json {
    private Json() {
    }
    public static Object parse(String text) {
        return parseBounded(text,4_194_304);
    }
    public static Object parseStored(String text) {
        return parseBounded(text,67_108_864);
    }
    private static Object parseBounded(String text,int limit) {
        if(text==null || text.length()>limit) throw Problem.invalid();
        var p=new Parser(text);
        Object value=p.value(0);
        p.ws();
        if(p.i!=text.length()) throw Problem.invalid();
        return value;
    }
    @SuppressWarnings("unchecked") public static Map<String,Object> object(Object value) {
        if(!(value instanceof Map<?,?>)) throw Problem.invalid();
        return (Map<String,Object>)value;
    }
    public static Map<String,Object> obj(String text) {
        return object(parse(text));
    }
    @SuppressWarnings("unchecked") public static List<Object> array(Object value) {
        if(!(value instanceof List<?>)) throw Problem.invalid();
        return (List<Object>)value;
    }
    public static String text(Map<String,Object> m,String key,String fallback) {
        Object v=m.get(key);
        return v==null?fallback:String.valueOf(v);
    }
    public static int integer(Map<String,Object> m,String key,int fallback) {
        Object v=m.get(key);
        if(v==null) return fallback;
        try {
            return new BigDecimal(v.toString()).intValueExact();
        }
        catch(Exception e) {
            throw Problem.invalid();
        }
    }
    public static long number(Map<String,Object> m,String key,long fallback) {
        Object v=m.get(key);
        if(v==null) return fallback;
        try {
            return new BigDecimal(v.toString()).longValueExact();
        }
        catch(Exception e) {
            throw Problem.invalid();
        }
    }
    public static boolean bool(Map<String,Object> m,String key,boolean fallback) {
        Object v=m.get(key);
        if(v==null)return fallback;
        if(v instanceof Boolean b)return b;
        throw Problem.invalid();
    }
    public static Map<String,Object> map(Map<String,Object> m,String key) {
        Object v=m.get(key);
        return v==null?new LinkedHashMap<>():object(v);
    }
    public static List<Object> list(Map<String,Object> m,String key) {
        Object v=m.get(key);
        return v==null?new ArrayList<>():array(v);
    }
    public static String stringify(Object v) {
        StringBuilder b=new StringBuilder();
        write(v,b,0,false);
        return b.toString();
    }
    public static String canonical(Object v) {
        StringBuilder b=new StringBuilder();
        write(v,b,0,true);
        return b.toString();
    }
    public static Object copy(Object v) {
        return parseStored(stringify(v));
    }
    private static void write(Object v,StringBuilder b,int depth,boolean sorted) {
        if(depth>64)throw Problem.invalid();
        if(v==null) {
            b.append("null");
            return;
        }
        if(v instanceof String s) {
            quote(s,b);
            return;
        }
        if(v instanceof Boolean) {
            b.append(v);
            return;
        }
        if(v instanceof Number n) {
            if(n instanceof Double d&&!Double.isFinite(d)||n instanceof Float f&&!Float.isFinite(f))throw Problem.invalid();
            b.append(new BigDecimal(n.toString()).stripTrailingZeros().toPlainString());
            return;
        }
        if(v instanceof Map<?,?> m) {
            b.append('{');
            boolean first=true;
            List<String> keys=new ArrayList<>();
            for(Object k:m.keySet()) {
                if(!(k instanceof String))throw Problem.invalid();
                keys.add((String)k);
            }
            if(sorted)Collections.sort(keys);
            for(String k:keys) {
                if(!first)b.append(',');
                first=false;
                quote(k,b);
                b.append(':');
                write(m.get(k),b,depth+1,sorted);
            }
            b.append('}');
            return;
        }
        if(v instanceof Iterable<?> it) {
            b.append('[');
            boolean first=true;
            for(Object x:it) {
                if(!first)b.append(',');
                first=false;
                write(x,b,depth+1,sorted);
            }
            b.append(']');
            return;
        }
        throw new IllegalArgumentException("Unsupported JSON type: "+v.getClass().getName());
    }
    private static void quote(String s,StringBuilder b) {
        b.append('"');
        for(int i=0; i<s.length(); i++) {
            char c=s.charAt(i);
            switch(c) {
                case '"'->b.append("\\\"");
                case '\\'->b.append("\\\\");
                case '\b'->b.append("\\b");
                case '\f'->b.append("\\f");
                case '\n'->b.append("\\n");
                case '\r'->b.append("\\r");
                case '\t'->b.append("\\t");
                default-> {
                    if(c<32)b.append(String.format("\\u%04x",(int)c));
                    else if(Character.isHighSurrogate(c)) {
                        if(i+1>=s.length()||!Character.isLowSurrogate(s.charAt(i+1)))throw Problem.invalid();
                        b.append(c).append(s.charAt(++i));
                    }
                    else if(Character.isLowSurrogate(c))throw Problem.invalid();
                    else b.append(c);
                }
            }
        }
        b.append('"');
    }
    private static final class Parser {
        final String s;
        int i;
        Parser(String s) {
            this.s=s;
        }
        void ws() {
            while(i<s.length()&&" \t\r\n".indexOf(s.charAt(i))>=0)i++;
        }
        boolean eat(char c) {
            ws();
            if(i<s.length()&&s.charAt(i)==c) {
                i++;
                return true;
            }
            return false;
        }
        void expect(char c) {
            if(!eat(c))throw Problem.invalid();
        }
        Object value(int d) {
            if(d>64)throw Problem.invalid();
            ws();
            if(i>=s.length())throw Problem.invalid();
            char c=s.charAt(i);
            if(c=='"')return string();
            if(c=='{') {
                i++;
                var m=new LinkedHashMap<String,Object>();
                if(eat('}'))return m;
                do {
                    ws();
                    String k=string();
                    expect(':');
                    if(m.containsKey(k))throw Problem.invalid();
                    m.put(k,value(d+1));
                    if(m.size()>10_000)throw Problem.invalid();
                }
                while(eat(','));
                expect('}');
                return m;
            }
            if(c=='[') {
                i++;
                var a=new ArrayList<Object>();
                if(eat(']'))return a;
                do {
                    a.add(value(d+1));
                    if(a.size()>100_000)throw Problem.invalid();
                }
                while(eat(','));
                expect(']');
                return a;
            }
            for(String word:List.of("true","false","null"))if(s.startsWith(word,i)) {
                i+=word.length();
                return switch(word) {
                    case "true"->true;
                    case "false"->false;
                    default->null;
                }
                ;
            }
            int start=i;
            if(s.charAt(i)=='-')i++;
            if(i>=s.length())throw Problem.invalid();
            if(s.charAt(i)=='0')i++;
            else {
                if(s.charAt(i)<'1'||s.charAt(i)>'9')throw Problem.invalid();
                while(i<s.length()&&digit(s.charAt(i)))i++;
            }
            if(i<s.length()&&s.charAt(i)=='.') {
                i++;
                int n=i;
                while(i<s.length()&&digit(s.charAt(i)))i++;
                if(i==n)throw Problem.invalid();
            }
            if(i<s.length()&&(s.charAt(i)=='e'||s.charAt(i)=='E')) {
                i++;
                if(i<s.length()&&(s.charAt(i)=='+'||s.charAt(i)=='-'))i++;
                int n=i;
                while(i<s.length()&&digit(s.charAt(i)))i++;
                if(n==i)throw Problem.invalid();
            }
            if(i-start>256)throw Problem.invalid();
            try {
                var n=new BigDecimal(s.substring(start,i));
                if(Math.abs((long)n.scale())>10_000)throw Problem.invalid();
                return n;
            }
            catch(NumberFormatException e) {
                throw Problem.invalid();
            }
        }
        boolean digit(char c) {
            return c>='0'&&c<='9';
        }
        String string() {
            ws();
            if(i>=s.length()||s.charAt(i++)!='"')throw Problem.invalid();
            var b=new StringBuilder();
            boolean ended=false;
            while(i<s.length()) {
                char c=s.charAt(i++);
                if(c=='"') {
                    ended=true;
                    break;
                }
                if(c<32)throw Problem.invalid();
                if(c=='\\') {
                    if(i>=s.length())throw Problem.invalid();
                    c=s.charAt(i++);
                    switch(c) {
                        case '"','\\','/'->b.append(c);
                        case 'b'->b.append('\b');
                        case 'f'->b.append('\f');
                        case 'n'->b.append('\n');
                        case 'r'->b.append('\r');
                        case 't'->b.append('\t');
                        case 'u'-> {
                            if(i+4>s.length())throw Problem.invalid();
                            try {
                                b.append((char)Integer.parseInt(s.substring(i,i+4),16));
                            }
                            catch(Exception e) {
                                throw Problem.invalid();
                            }
                            i+=4;
                        }
                        default->throw Problem.invalid();
                    }
                }
                else b.append(c);
                if(b.length()>2_097_152)throw Problem.invalid();
            }
            if(!ended)throw Problem.invalid();
            for(int j=0; j<b.length(); j++) {
                char c=b.charAt(j);
                if(Character.isHighSurrogate(c)) {
                    if(j+1>=b.length()||!Character.isLowSurrogate(b.charAt(++j)))throw Problem.invalid();
                }
                else if(Character.isLowSurrogate(c))throw Problem.invalid();
            }
            return b.toString();
        }
    }
}
