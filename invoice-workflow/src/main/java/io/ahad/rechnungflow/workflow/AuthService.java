package io.ahad.rechnungflow.workflow;
import io.ahad.rechnungflow.core.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
/** Tenant-scoped local identities, revocable sessions and intake-only API keys. */
public final class AuthService {
    public static final Set<String> ROLES=Set.of("ADMIN","INTAKE","REVIEWER","APPROVER","FINANCE","AUDITOR");
    private final Store store;private final Clock clock;private final String dummy=Crypto.password(Crypto.token());
    private final Semaphore loginSlots=new Semaphore(4);private final Map<String,Bucket> attempts=new HashMap<>();
    private record Bucket(long start,int count){}
    public AuthService(Store store){this(store,Clock.systemUTC());}
    public AuthService(Store store,Clock clock){this.store=store;this.clock=clock;}
    public void bootstrap(String tenant,String name,String password,boolean demo){
        String hash=Crypto.password(password);
        store.write(s->{if(Store.table(s,"tenants").containsKey(tenant))return null;
            var t=Values.map("id",tenant,"name",name,"mode",demo?"DEMO":"STRICT","currency","EUR","secondApprovalThreshold","5000.00","policyVersion",1,"retentionYears",8,"createdAt",Values.now());
            Store.table(s,"tenants").put(tenant,t);
            addUser(s,tenant,"admin","Workspace administrator",hash,List.of("ADMIN","INTAKE","REVIEWER","APPROVER","FINANCE","AUDITOR"),false);
            if(demo){
                addUser(s,tenant,"intake","Lea Fischer",hash,List.of("INTAKE"),false);
                addUser(s,tenant,"reviewer","Jonas Weber",hash,List.of("REVIEWER"),false);
                addUser(s,tenant,"approver","Mina Berger",hash,List.of("APPROVER"),false);
                addUser(s,tenant,"approver2","Omid Rezaei",hash,List.of("APPROVER"),false);
                addUser(s,tenant,"finance","Sofia Klein",hash,List.of("FINANCE","AUDITOR"),false);
                addUser(s,tenant,"auditor","Read-only auditor",hash,List.of("AUDITOR"),false);
            }
            Audit.append(s,tenant,"bootstrap","WORKSPACE_CREATED",tenant,Values.map("mode",demo?"DEMO":"STRICT"));return null;
        });
    }
    private static Map<String,Object> addUser(Map<String,Object>s,String tenant,String login,String name,String hash,List<String> roles,boolean mustChange){
        var u=Values.map("id",Values.id(),"tenant",tenant,"login",login.toLowerCase(Locale.ROOT),"name",name,"passwordHash",hash,"roles",roles,"active",true,"authVersion",1,"mustChangePassword",mustChange,"createdAt",Values.now());
        Store.table(s,"users").put(Values.text(u,"id"),u);return u;
    }
    private synchronized void throttle(String key){
        long now=clock.millis();attempts.entrySet().removeIf(e->now-e.getValue().start>60_000);
        if(attempts.size()>=10_000&&!attempts.containsKey(key))throw Problem.of(429,"RATE_LIMITED");
        Bucket b=attempts.getOrDefault(key,new Bucket(now,0));if(b.count>=12)throw Problem.of(429,"RATE_LIMITED");attempts.put(key,new Bucket(b.start,b.count+1));
    }
    public Map<String,Object> login(String tenant,String login,String password,String ip){
        if(tenant==null||login==null||password==null||tenant.length()>50||login.length()>80||password.length()>128)throw Problem.of(401,"INVALID_CREDENTIALS");
        throttle("ip:"+ip);throttle("account:"+tenant+":"+login.toLowerCase(Locale.ROOT));
        if(!loginSlots.tryAcquire())throw Problem.of(429,"RATE_LIMITED");
        try{
            Map<String,Object> user=store.read(s->Store.table(s,"users").values().stream().map(Json::object).filter(u->tenant.equals(Values.text(u,"tenant"))&&login.equalsIgnoreCase(Values.text(u,"login"))).findFirst().orElse(null));
            boolean valid=Crypto.verify(password,user==null?dummy:Values.text(user,"passwordHash"));
            if(!valid||user==null||!Json.bool(user,"active",false))throw Problem.of(401,"INVALID_CREDENTIALS");
            String token=Crypto.token(),hash=Crypto.sha(token);long now=clock.millis();
            store.write(s->{
                var current=Json.object(Store.table(s,"users").get(Values.text(user,"id")));
                Problem.require(Json.bool(current,"active",false)&&Values.text(current,"passwordHash").equals(Values.text(user,"passwordHash")),401,"INVALID_CREDENTIALS");
                var sessions=Store.table(s,"sessions");sessions.entrySet().removeIf(e->Json.number(Json.object(e.getValue()),"expiresAt",0)<now);
                long existing=sessions.values().stream().map(Json::object).filter(x->Values.text(x,"userId").equals(Values.text(user,"id"))).count();
                if(existing>=20)throw Problem.of(429,"SESSION_LIMIT");
                sessions.put(hash,Values.map("userId",Values.text(user,"id"),"tenant",tenant,"authVersion",Json.integer(current,"authVersion",1),"createdAt",now,"lastSeen",now,"expiresAt",now+28_800_000));
                Audit.append(s,tenant,Values.text(user,"id"),"LOGIN",Values.text(user,"id"),Map.of());return null;
            });
            return Values.map("token",token,"csrf",csrf(token),"user",publicUser(user));
        }finally{loginSlots.release();}
    }
    public static String csrf(String token){return Crypto.sha("rechnungflow-csrf:"+token);}
    public Actor session(String token){
        if(token==null||!token.matches("[A-Za-z0-9_-]{43}"))throw Problem.of(401,"AUTHENTICATION_REQUIRED");
        String hash=Crypto.sha(token);long now=clock.millis();
        return store.read(s->{var raw=Store.table(s,"sessions").get(hash);if(raw==null)throw Problem.of(401,"SESSION_EXPIRED");
            var session=Json.object(raw);Problem.require(Json.number(session,"expiresAt",0)>now&&now-Json.number(session,"lastSeen",0)<1_800_000,401,"SESSION_EXPIRED");
            var u=Json.object(Store.table(s,"users").get(Values.text(session,"userId")));
            Problem.require(Json.bool(u,"active",false)&&Json.integer(u,"authVersion",1)==Json.integer(session,"authVersion",0),401,"SESSION_EXPIRED");
            return actor(u,false);
        });
    }
    public void touch(String token){String hash=Crypto.sha(token);long now=clock.millis();
        boolean needed=store.read(s->{Object o=Store.table(s,"sessions").get(hash);return o!=null&&now-Json.number(Json.object(o),"lastSeen",0)>60_000;});
        if(needed)store.write(s->{Object o=Store.table(s,"sessions").get(hash);if(o!=null)Json.object(o).put("lastSeen",now);return null;});
    }
    public boolean mustChange(Actor a){return store.read(s->Json.bool(Json.object(Store.table(s,"users").get(a.id())),"mustChangePassword",false));}
    public void logout(String token){store.write(s->{Store.table(s,"sessions").remove(Crypto.sha(token));return null;});}
    public Map<String,Object> changePassword(Actor a,String current,String next){
        String oldHash=store.read(s->Values.text(Json.object(Store.table(s,"users").get(a.id())),"passwordHash"));
        Problem.require(Crypto.verify(current,oldHash),403,"CURRENT_PASSWORD_INVALID");String nextHash=Crypto.password(next);
        return store.write(s->{var u=Json.object(Store.table(s,"users").get(a.id()));Problem.require(oldHash.equals(Values.text(u,"passwordHash")),409,"VERSION_CONFLICT");
            u.put("passwordHash",nextHash);u.put("authVersion",Json.integer(u,"authVersion",1)+1);u.put("mustChangePassword",false);
            Store.table(s,"sessions").entrySet().removeIf(e->a.id().equals(Values.text(Json.object(e.getValue()),"userId")));
            Audit.append(s,a.tenant(),a.id(),"PASSWORD_CHANGED",a.id(),Map.of());return Values.map("changed",true);});
    }
    public List<Object> users(Actor a){a.require("ADMIN");return store.read(s->Store.table(s,"users").values().stream().map(Json::object).filter(u->a.tenant().equals(Values.text(u,"tenant"))).map(u->(Object)publicUser(u)).toList());}
    public Map<String,Object> createUser(Actor a,Map<String,Object> body){
        a.require("ADMIN");String login=Values.required(body,"login",80).toLowerCase(Locale.ROOT),name=Values.required(body,"name",120);
        Problem.require(login.matches("[a-z0-9][a-z0-9@._-]{2,79}"),400,"INVALID_INPUT");String hash=Crypto.password(Values.required(body,"password",128));
        List<String> roles=roles(body);
        return store.write(s->{boolean exists=Store.table(s,"users").values().stream().map(Json::object).anyMatch(u->a.tenant().equals(Values.text(u,"tenant"))&&login.equals(Values.text(u,"login")));
            Problem.require(!exists,409,"USER_EXISTS");var u=addUser(s,a.tenant(),login,name,hash,roles,true);
            Audit.append(s,a.tenant(),a.id(),"USER_CREATED",Values.text(u,"id"),Values.map("roles",roles));return publicUser(u);});
    }
    public Map<String,Object> updateUser(Actor a,String id,Map<String,Object> body){
        a.require("ADMIN");List<String> roles=roles(body);boolean active=Json.bool(body,"active",true);String name=Values.required(body,"name",120);
        return store.write(s->{var u=owned(s,"users",id,a.tenant());
            if(!active||!roles.contains("ADMIN")){
                long admins=Store.table(s,"users").values().stream().map(Json::object).filter(v->a.tenant().equals(Values.text(v,"tenant"))&&Json.bool(v,"active",false)&&Json.list(v,"roles").contains("ADMIN")&&!id.equals(Values.text(v,"id"))).count();
                Problem.require(admins>0,409,"LAST_ADMIN");
            }
            u.put("name",name);u.put("active",active);u.put("roles",roles);u.put("authVersion",Json.integer(u,"authVersion",1)+1);
            Audit.append(s,a.tenant(),a.id(),"USER_UPDATED",id,Values.map("active",active,"roles",roles));return publicUser(u);});
    }
    private List<String> roles(Map<String,Object> body){List<String> roles=Json.list(body,"roles").stream().map(String::valueOf).distinct().sorted().toList();Problem.require(!roles.isEmpty()&&ROLES.containsAll(roles),400,"INVALID_ROLE");return roles;}
    public Map<String,Object> createKey(Actor a,Map<String,Object> body){a.require("ADMIN");String name=Values.required(body,"name",100);int days=Json.integer(body,"days",30);Problem.require(days>=1&&days<=90,400,"INVALID_INPUT");
        String token="rf_"+Crypto.token();return store.write(s->{var key=Values.map("id",Values.id(),"tenant",a.tenant(),"name",name,"hash",Crypto.sha(token),"active",true,"createdAt",Values.now(),"expiresAt",clock.millis()+days*86_400_000L);
            Store.table(s,"keys").put(Values.text(key,"id"),key);Audit.append(s,a.tenant(),a.id(),"API_KEY_CREATED",Values.text(key,"id"),Map.of());
            var v=new LinkedHashMap<>(key);v.remove("hash");v.put("token",token);return v;});
    }
    public List<Object> keys(Actor a){a.require("ADMIN");return store.read(s->Store.table(s,"keys").values().stream().map(Json::object).filter(k->a.tenant().equals(Values.text(k,"tenant"))).map(k->{var v=new LinkedHashMap<>(k);v.remove("hash");return (Object)v;}).toList());}
    public void revokeKey(Actor a,String id){a.require("ADMIN");store.write(s->{var k=owned(s,"keys",id,a.tenant());k.put("active",false);Audit.append(s,a.tenant(),a.id(),"API_KEY_REVOKED",id,Map.of());return null;});}
    public Actor apiKey(String token){if(token==null||!token.matches("rf_[A-Za-z0-9_-]{43}"))throw Problem.of(401,"INVALID_API_KEY");String hash=Crypto.sha(token);
        return store.read(s->Store.table(s,"keys").values().stream().map(Json::object).filter(k->Crypto.equal(hash,Values.text(k,"hash"))&&Json.bool(k,"active",false)&&Json.number(k,"expiresAt",0)>clock.millis()).findFirst().map(k->new Actor("key:"+Values.text(k,"id"),Values.text(k,"tenant"),Values.text(k,"name"),Set.of("INTAKE"),true)).orElseThrow(()->Problem.of(401,"INVALID_API_KEY")));}
    public Actor findActor(String tenant,String login){return store.read(s->Store.table(s,"users").values().stream().map(Json::object).filter(u->tenant.equals(Values.text(u,"tenant"))&&login.equals(Values.text(u,"login"))).findFirst().map(u->actor(u,false)).orElseThrow());}
    public static Map<String,Object> owned(Map<String,Object>s,String table,String id,String tenant){Object o=Store.table(s,table).get(id);if(!(o instanceof Map<?,?>))throw Problem.of(404,"NOT_FOUND");var m=Json.object(o);Problem.require(tenant.equals(Values.text(m,"tenant")),404,"NOT_FOUND");return m;}
    private static Actor actor(Map<String,Object> u,boolean key){return new Actor(Values.text(u,"id"),Values.text(u,"tenant"),Values.text(u,"name"),new HashSet<>(Json.list(u,"roles").stream().map(String::valueOf).toList()),key);}
    public static Map<String,Object> publicUser(Map<String,Object> u){var m=new LinkedHashMap<>(u);m.remove("passwordHash");m.remove("authVersion");return m;}
}
