package io.ahad.rechnungflow.testing;
import io.ahad.rechnungflow.core.*;import io.ahad.rechnungflow.workflow.*;
import java.time.*;import java.util.*;
import static io.ahad.rechnungflow.testing.TestKit.*;
public final class AuthTest {
    static class MutableClock extends Clock{long ms=Instant.parse("2026-10-10T12:00:00Z").toEpochMilli();public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId z){return this;}public Instant instant(){return Instant.ofEpochMilli(ms);}}
    @FunctionalInterface interface Case{void run(Fixtures f,AuthService auth,MutableClock clock)throws Exception;}
    static void test(TestKit t,String n,Case c){t.test(n,()->{try(Fixtures f=new Fixtures()){MutableClock clock=new MutableClock();AuthService auth=new AuthService(f.store,clock);c.run(f,auth,clock);}});}
    static Map<String,Object>login(AuthService a,String user){return a.login("test",user,Fixtures.PASSWORD,"test-ip");}
    public static void main(String[]args)throws Exception{
        TestKit t=new TestKit("auth");
        test(t,"Valid password establishes a session",(f,a,c)->{var x=login(a,"admin");eq(f.a("admin").id(),a.session(Values.text(x,"token")).id());});
        test(t,"Wrong password is denied",(f,a,c)->error("INVALID_CREDENTIALS",()->a.login("test","admin","wrong password","ip")));
        test(t,"Unknown identity is denied",(f,a,c)->error("INVALID_CREDENTIALS",()->a.login("test","does-not-exist",Fixtures.PASSWORD,"ip")));
        test(t,"Cross-tenant login is denied",(f,a,c)->error("INVALID_CREDENTIALS",()->a.login("other","admin",Fixtures.PASSWORD,"ip")));
        test(t,"Login is case insensitive",(f,a,c)->eq(f.a("admin").id(),a.session(Values.text(login(a,"ADMIN"),"token")).id()));
        test(t,"Login response never returns password hash",(f,a,c)->yes(!Json.map(login(a,"admin"),"user").containsKey("passwordHash")));
        test(t,"Session stored as hash rather than bearer secret",(f,a,c)->{var x=login(a,"admin");String token=Values.text(x,"token");yes(!f.store.read(s->Store.table(s,"sessions").containsKey(token)));yes(f.store.read(s->Store.table(s,"sessions").containsKey(Crypto.sha(token))));});
        test(t,"Missing session rejected",(f,a,c)->error("AUTHENTICATION_REQUIRED",()->a.session(null)));
        test(t,"Malformed session rejected",(f,a,c)->error("AUTHENTICATION_REQUIRED",()->a.session("../token")));
        test(t,"Unknown session rejected",(f,a,c)->error("SESSION_EXPIRED",()->a.session(Crypto.token())));
        test(t,"Logout immediately revokes session",(f,a,c)->{String s=Values.text(login(a,"admin"),"token");a.logout(s);error("SESSION_EXPIRED",()->a.session(s));});
        test(t,"Idle timeout is thirty minutes",(f,a,c)->{String s=Values.text(login(a,"admin"),"token");c.ms+=1_800_000;error("SESSION_EXPIRED",()->a.session(s));});
        test(t,"Activity refreshes idle deadline",(f,a,c)->{String s=Values.text(login(a,"admin"),"token");c.ms+=1_700_000;a.touch(s);c.ms+=1_000_000;eq(f.a("admin").id(),a.session(s).id());});
        test(t,"Absolute expiry survives activity",(f,a,c)->{String s=Values.text(login(a,"admin"),"token");c.ms+=28_800_001;a.touch(s);error("SESSION_EXPIRED",()->a.session(s));});
        test(t,"CSRF token is bound to session secret",(f,a,c)->{String s=Values.text(login(a,"admin"),"token");yes(!s.equals(AuthService.csrf(s)));yes(!AuthService.csrf(s).equals(AuthService.csrf(Crypto.token())));});
        test(t,"Password change revokes sessions",(f,a,c)->{String s=Values.text(login(a,"admin"),"token");a.changePassword(f.a("admin"),Fixtures.PASSWORD,"Different-strong-test-password!");error("SESSION_EXPIRED",()->a.session(s));error("INVALID_CREDENTIALS",()->a.login("test","admin",Fixtures.PASSWORD,"new-ip"));});
        test(t,"Password change requires current password",(f,a,c)->error("CURRENT_PASSWORD_INVALID",()->a.changePassword(f.a("admin"),"wrong",Fixtures.PASSWORD)));
        test(t,"New user receives forced password change",(f,a,c)->{var u=a.createUser(f.a("admin"),Values.map("login","new-user","name","New User","password",Fixtures.PASSWORD,"roles",List.of("INTAKE")));yes(Json.bool(u,"mustChangePassword",false));yes(!u.containsKey("passwordHash"));});
        test(t,"Username uniqueness is tenant scoped",(f,a,c)->error("USER_EXISTS",()->a.createUser(f.a("admin"),Values.map("login","admin","name","Duplicate","password",Fixtures.PASSWORD,"roles",List.of("INTAKE")))));
        test(t,"Unknown roles cannot be assigned",(f,a,c)->error("INVALID_ROLE",()->a.createUser(f.a("admin"),Values.map("login","new-user","name","New User","password",Fixtures.PASSWORD,"roles",List.of("ROOT")))));
        test(t,"Non-admin cannot enumerate users",(f,a,c)->error("FORBIDDEN",()->a.users(f.a("intake"))));
        test(t,"Last administrator cannot be deactivated",(f,a,c)->error("LAST_ADMIN",()->a.updateUser(f.a("admin"),f.a("admin").id(),Values.map("name","Admin","roles",List.of("ADMIN"),"active",false))));
        test(t,"Last administrator role cannot be removed",(f,a,c)->error("LAST_ADMIN",()->a.updateUser(f.a("admin"),f.a("admin").id(),Values.map("name","Admin","roles",List.of("INTAKE"),"active",true))));
        test(t,"Deactivation invalidates active session",(f,a,c)->{String s=Values.text(login(a,"intake"),"token");a.updateUser(f.a("admin"),f.a("intake").id(),Values.map("name","Inactive","roles",List.of("INTAKE"),"active",false));error("SESSION_EXPIRED",()->a.session(s));});
        test(t,"Role change invalidates active session",(f,a,c)->{String s=Values.text(login(a,"intake"),"token");a.updateUser(f.a("admin"),f.a("intake").id(),Values.map("name","Changed","roles",List.of("AUDITOR"),"active",true));error("SESSION_EXPIRED",()->a.session(s));});
        test(t,"API key is intake-only",(f,a,c)->{var k=a.createKey(f.a("admin"),Values.map("name","Integration","days",1));Actor actor=a.apiKey(Values.text(k,"token"));yes(actor.apiKey());eq(Set.of("INTAKE"),actor.roles());});
        test(t,"API key list omits hashes and tokens",(f,a,c)->{a.createKey(f.a("admin"),Values.map("name","Integration","days",1));var k=Json.object(a.keys(f.a("admin")).getFirst());yes(!k.containsKey("token"));yes(!k.containsKey("hash"));});
        test(t,"API key revocation immediate",(f,a,c)->{var k=a.createKey(f.a("admin"),Values.map("name","Integration","days",1));a.revokeKey(f.a("admin"),Values.text(k,"id"));error("INVALID_API_KEY",()->a.apiKey(Values.text(k,"token")));});
        test(t,"API key expiry enforced",(f,a,c)->{var k=a.createKey(f.a("admin"),Values.map("name","Integration","days",1));c.ms+=86_400_001;error("INVALID_API_KEY",()->a.apiKey(Values.text(k,"token")));});
        test(t,"API key lifetime bound enforced",(f,a,c)->error("INVALID_INPUT",()->a.createKey(f.a("admin"),Values.map("name","Integration","days",91))));
        test(t,"Non-admin cannot create API keys",(f,a,c)->error("FORBIDDEN",()->a.createKey(f.a("reviewer"),Values.map("name","Integration","days",1))));
        test(t,"Token prefix and length validated",(f,a,c)->error("INVALID_API_KEY",()->a.apiKey("rf_invalid")));
        test(t,"User updates cannot cross tenant",(f,a,c)->{Actor other=new Actor("other","other","Other",Set.of("ADMIN"),false);error("NOT_FOUND",()->a.updateUser(other,f.a("intake").id(),Values.map("name","Intruder","roles",List.of("ADMIN"),"active",true)));});
        test(t,"Login throttling rejects thirteenth attempt",(f,a,c)->{for(int i=0;i<12;i++){String token=Values.text(login(a,"intake"),"token");a.logout(token);}error("RATE_LIMITED",()->login(a,"intake"));c.ms+=60_001;yes(login(a,"intake").containsKey("token"));});
        t.finish("build/test-results/auth.xml");
    }
}
