package io.ahad.rechnungflow.core;
import java.util.*;
public record Actor(String id,String tenant,String name,Set<String> roles,boolean apiKey) {
    public Actor{roles=Set.copyOf(roles);}
    public boolean has(String... allowed){return Arrays.stream(allowed).anyMatch(roles::contains);}
    public void require(String... allowed){Problem.require(has(allowed),403,"FORBIDDEN");}
    public Map<String,Object> publicView(){return Values.map("id",id,"tenant",tenant,"name",name,"roles",new ArrayList<>(new TreeSet<>(roles)),"apiKey",apiKey);}
}
