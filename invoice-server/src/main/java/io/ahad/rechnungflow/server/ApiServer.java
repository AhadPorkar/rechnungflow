package io.ahad.rechnungflow.server;
import io.ahad.rechnungflow.core.*;
import io.ahad.rechnungflow.workflow.*;
import com.sun.net.httpserver.*;
import java.net.*;
import java.nio.charset.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
public final class ApiServer implements AutoCloseable {
    private final HttpServer server;private final AuthService auth;private final InvoiceService invoices;private final OutboxService outbox;
    private final String origin,workspace;private final boolean secure;private final ExecutorService executor=Executors.newVirtualThreadPerTaskExecutor();
    private final Semaphore requests=new Semaphore(64);private final LongAdder requestCount=new LongAdder(),errorCount=new LongAdder();
    public ApiServer(String host,int port,String publicOrigin,String workspace,AuthService auth,InvoiceService invoices,OutboxService outbox)throws IOException{
        this.auth=auth;this.invoices=invoices;this.outbox=outbox;this.workspace=workspace;server=HttpServer.create(new InetSocketAddress(host,port),128);
        origin=publicOrigin==null?"http://"+(host.contains(":")?"["+host+"]":host)+":"+server.getAddress().getPort():publicOrigin;
        secure=origin.startsWith("https://");server.createContext("/",this::handle);server.setExecutor(executor);
    }
    public void start(){server.start();}
    public int port(){return server.getAddress().getPort();}
    private void handle(HttpExchange e)throws IOException{
        String requestId=UUID.randomUUID().toString();security(e);e.getResponseHeaders().set("X-Request-ID",requestId);requestCount.increment();
        if(!requests.tryAcquire()){send(e,503,Values.map("error",Values.map("code","SERVER_BUSY","requestId",requestId)));e.close();return;}
        try{
            Problem.require(e.getRequestURI().toString().length()<=4096,414,"URI_TOO_LONG");
            String path=e.getRequestURI().getPath(),method=e.getRequestMethod();Problem.require(!path.contains("..")&&!path.contains("\\")&&!path.contains("\0"),400,"INVALID_PATH");
            if(!method.equals("GET")&&!method.equals("HEAD"))checkOrigin(e);
            if(path.equals("/health/live")){requireMethod(method,"GET");send(e,200,Values.map("status","UP","application","RechnungFlow"));return;}
            if(path.equals("/api/public/config")){requireMethod(method,"GET");send(e,200,Values.map("version","0.1.0","workspace",workspace,"languages",List.of("en","de","fa")));return;}
            if(path.equals("/api/auth/login")){requireMethod(method,"POST");var b=body(e);var login=auth.login(Values.text(b,"tenant"),Values.text(b,"login"),Values.text(b,"password"),e.getRemoteAddress().getAddress().getHostAddress());
                String token=Values.text(login,"token");login.remove("token");cookie(e,token,28_800);send(e,200,login);return;}
            if(!path.startsWith("/api/")){requireMethod(method,"GET");staticFile(e,path);return;}
            String bearer=e.getRequestHeaders().getFirst("Authorization");String token=sessionCookie(e);Actor actor;
            if(bearer!=null){Problem.require(bearer.startsWith("Bearer "),401,"AUTHENTICATION_REQUIRED");actor=auth.apiKey(bearer.substring(7));
                Problem.require((path.equals("/api/invoices")&&method.equals("POST"))||(path.matches("/api/invoices/[a-f0-9-]{36}")&&method.equals("GET")),403,"API_KEY_SCOPE");
            }else{actor=auth.session(token);if(!method.equals("GET")&&!method.equals("HEAD"))Problem.require(Crypto.equal(auth.csrf(token),e.getRequestHeaders().getFirst("X-CSRF-Token")),403,"CSRF_INVALID");
                if(auth.mustChange(actor)&&!Set.of("/api/auth/me","/api/auth/password","/api/auth/logout").contains(path))throw Problem.of(403,"PASSWORD_CHANGE_REQUIRED");auth.touch(token);}
            if(path.equals("/api/auth/me")){requireMethod(method,"GET");send(e,200,Values.map("user",actor.publicView(),"csrf",AuthService.csrf(token),"mustChangePassword",auth.mustChange(actor)));return;}
            if(path.equals("/api/auth/logout")){requireMethod(method,"POST");auth.logout(token);cookie(e,"",0);send(e,200,Values.map("loggedOut",true));return;}
            if(path.equals("/api/auth/password")){requireMethod(method,"POST");var b=body(e);var changed=auth.changePassword(actor,Values.required(b,"currentPassword",128),Values.required(b,"newPassword",128));cookie(e,"",0);send(e,200,changed);return;}
            if(path.equals("/api/dashboard")){requireMethod(method,"GET");send(e,200,invoices.dashboard(actor));return;}
            if(path.equals("/api/policy")){if(method.equals("GET"))send(e,200,invoices.policy(actor));else{requireMethod(method,"PUT");send(e,200,invoices.updatePolicy(actor,body(e)));}return;}
            if(path.equals("/api/invoices/export.csv")){requireMethod(method,"GET");download(e,"invoice-register.csv","text/csv; charset=utf-8",invoices.csv(actor));return;}
            if(path.equals("/api/invoices")){
                if(method.equals("GET")){var q=query(e);send(e,200,invoices.list(actor,q.get("q"),q.get("stage"),number(q,"page",0),number(q,"pageSize",25)));}
                else{requireMethod(method,"POST");String ct=e.getRequestHeaders().getFirst("Content-Type");Problem.require(ct!=null&&(ct.startsWith("application/xml")||ct.startsWith("text/xml")||ct.startsWith("application/octet-stream")),415,"XML_UPLOAD_REQUIRED");
                    var q=query(e);String filename=e.getRequestHeaders().getFirst("X-Filename");if(filename!=null)try{filename=URLDecoder.decode(filename,StandardCharsets.UTF_8);}catch(IllegalArgumentException ex){throw Problem.invalid();}
                    send(e,201,invoices.intake(actor,read(e,2_097_152),filename,e.getRequestHeaders().getFirst("Idempotency-Key"),q.get("supersedes")));}return;
            }
            String[] p=path.split("/");
            if(p.length>=4&&p[2].equals("invoices")){
                String id=p[3];Problem.require(Values.uuid(id),404,"NOT_FOUND");
                if(p.length==4){requireMethod(method,"GET");send(e,200,invoices.get(actor,id));return;}
                if(p.length!=5)throw Problem.of(404,"NOT_FOUND");String action=p[4];
                if(method.equals("GET")){
                    switch(action){case "original"->download(e,"invoice-"+id+".xml","application/octet-stream",invoices.original(actor,id));
                        case "report"->download(e,"validation-"+id+".xml","application/octet-stream",invoices.report(actor,id,number(query(e),"attempt",-1)));
                        case "evidence"->download(e,"evidence-"+id+".zip","application/zip",invoices.evidence(actor,id));default->throw Problem.of(404,"NOT_FOUND");}return;
                }
                requireMethod(method,"POST");var b=body(e);Map<String,Object> answer=switch(action){
                    case "review"->invoices.review(actor,id,b);case "approve"->invoices.approve(actor,id,b);case "reject"->invoices.reject(actor,id,b);
                    case "comment"->invoices.comment(actor,id,b);case "hold"->invoices.hold(actor,id,b);case "revalidate"->invoices.revalidate(actor,id,b);
                    case "export"->invoices.export(actor,id,b,e.getRequestHeaders().getFirst("Idempotency-Key"));default->throw Problem.of(404,"NOT_FOUND");};send(e,200,answer);return;
            }
            if(path.equals("/api/suppliers")){if(method.equals("GET"))send(e,200,Values.map("items",invoices.suppliers(actor)));else{requireMethod(method,"POST");send(e,201,invoices.createSupplier(actor,body(e)));}return;}
            if(p.length>=4&&p[2].equals("suppliers")){String id=p[3];Problem.require(Values.uuid(id),404,"NOT_FOUND");
                if(p.length==4){requireMethod(method,"PUT");send(e,200,invoices.changeSupplier(actor,id,body(e)));return;}
                if(p.length==5&&p[4].equals("verify")){requireMethod(method,"POST");send(e,200,invoices.verifySupplier(actor,id,body(e)));return;}}
            if(path.equals("/api/users")){if(method.equals("GET"))send(e,200,Values.map("items",auth.users(actor)));else{requireMethod(method,"POST");send(e,201,auth.createUser(actor,body(e)));}return;}
            if(p.length==4&&p[2].equals("users")){requireMethod(method,"PUT");send(e,200,auth.updateUser(actor,p[3],body(e)));return;}
            if(path.equals("/api/keys")){if(method.equals("GET"))send(e,200,Values.map("items",auth.keys(actor)));else{requireMethod(method,"POST");send(e,201,auth.createKey(actor,body(e)));}return;}
            if(p.length==5&&p[2].equals("keys")&&p[4].equals("revoke")){requireMethod(method,"POST");auth.revokeKey(actor,p[3]);send(e,200,Values.map("revoked",true));return;}
            if(path.equals("/api/jobs")){requireMethod(method,"GET");send(e,200,Values.map("items",outbox.jobs(actor),"accountingConfigured",outbox.configured()));return;}
            if(p.length==5&&p[2].equals("jobs")&&p[4].equals("reconcile")){requireMethod(method,"POST");send(e,200,outbox.reconcile(actor,p[3],body(e)));return;}
            if(path.equals("/api/audit")){requireMethod(method,"GET");var q=query(e);send(e,200,invoices.audit(actor,number(q,"page",0),number(q,"pageSize",50)));return;}
            if(path.equals("/api/system")){requireMethod(method,"GET");actor.require("ADMIN");send(e,200,Values.map("version","0.1.0","validator",invoices.capabilities(),"accountingConfigured",outbox.configured(),"requests",requestCount.sum(),"errors",errorCount.sum(),"storage","encrypted-single-node","java",System.getProperty("java.version")));return;}
            if(path.equals("/api/openapi.json")){requireMethod(method,"GET");resource(e,"/openapi.json","application/json; charset=utf-8");return;}
            throw Problem.of(404,"NOT_FOUND");
        }catch(Problem p){if(p.status()>=500)errorCount.increment();send(e,p.status(),Values.map("error",Values.map("code",p.code(),"status",p.status(),"requestId",requestId)));}
        catch(Exception ex){errorCount.increment();System.err.println("Request "+requestId+" failed: "+ex.getClass().getSimpleName());send(e,500,Values.map("error",Values.map("code","INTERNAL_ERROR","status",500,"requestId",requestId)));}
        finally{requests.release();e.close();}
    }
    private void checkOrigin(HttpExchange e){String supplied=e.getRequestHeaders().getFirst("Origin");if(supplied!=null)Problem.require(supplied.equals(origin),403,"ORIGIN_REJECTED");
        Problem.require(!"cross-site".equals(e.getRequestHeaders().getFirst("Sec-Fetch-Site")),403,"ORIGIN_REJECTED");}
    private void security(HttpExchange e){Headers h=e.getResponseHeaders();h.set("X-Content-Type-Options","nosniff");h.set("X-Frame-Options","DENY");h.set("Referrer-Policy","no-referrer");
        h.set("Permissions-Policy","camera=(), microphone=(), geolocation=()");h.set("Cache-Control","no-store");
        h.set("Content-Security-Policy","default-src 'none'; script-src 'self'; style-src 'self'; img-src 'self' data:; font-src 'self'; connect-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'; object-src 'none'");
        if(secure)h.set("Strict-Transport-Security","max-age=31536000");}
    private void cookie(HttpExchange e,String value,int age){e.getResponseHeaders().add("Set-Cookie","rf_session="+value+"; Path=/; HttpOnly; SameSite=Strict; Max-Age="+age+(secure?"; Secure":""));}
    private String sessionCookie(HttpExchange e){List<String>headers=e.getRequestHeaders().get("Cookie");if(headers==null)return null;String found=null;for(String header:headers)for(String part:header.split(";")){
        String[]p=part.trim().split("=",2);if(p.length==2&&p[0].equals("rf_session")){if(found!=null)throw Problem.of(401,"AUTHENTICATION_REQUIRED");found=p[1];}}return found;}
    private Map<String,Object> body(HttpExchange e)throws IOException{String ct=e.getRequestHeaders().getFirst("Content-Type");Problem.require(ct!=null&&ct.toLowerCase(Locale.ROOT).startsWith("application/json"),415,"JSON_REQUIRED");
        byte[] b=read(e,65_536);try{return Json.obj(StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(b)).toString());}catch(CharacterCodingException ex){throw Problem.invalid();}}
    private byte[] read(HttpExchange e,int max)throws IOException{String length=e.getRequestHeaders().getFirst("Content-Length");if(length!=null)try{Problem.require(Long.parseLong(length)>=0&&Long.parseLong(length)<=max,413,"FILE_TOO_LARGE");}catch(NumberFormatException x){throw Problem.invalid();}
        String encoding=e.getRequestHeaders().getFirst("Content-Encoding");Problem.require(encoding==null||encoding.equalsIgnoreCase("identity"),415,"CONTENT_ENCODING_UNSUPPORTED");
        byte[] b=e.getRequestBody().readNBytes(max+1);Problem.require(b.length<=max,413,"FILE_TOO_LARGE");return b;}
    private Map<String,String> query(HttpExchange e){Map<String,String>q=new LinkedHashMap<>();String raw=e.getRequestURI().getRawQuery();if(raw==null||raw.isBlank())return q;
        for(String item:raw.split("&")){String[]p=item.split("=",2);try{String k=URLDecoder.decode(p[0],StandardCharsets.UTF_8),v=p.length>1?URLDecoder.decode(p[1],StandardCharsets.UTF_8):"";
            Problem.require(!q.containsKey(k),400,"INVALID_INPUT");q.put(k,v);}catch(IllegalArgumentException x){throw Problem.invalid();}}return q;}
    private int number(Map<String,String>q,String key,int fallback){try{return q.containsKey(key)?Integer.parseInt(q.get(key)):fallback;}catch(NumberFormatException e){throw Problem.of(400,"INVALID_PAGINATION");}}
    private void requireMethod(String actual,String expected){Problem.require(actual.equals(expected),405,"METHOD_NOT_ALLOWED");}
    private void send(HttpExchange e,int status,Object body)throws IOException{raw(e,status,"application/json; charset=utf-8",Json.stringify(body).getBytes(StandardCharsets.UTF_8));}
    private void raw(HttpExchange e,int status,String type,byte[] b)throws IOException{e.getResponseHeaders().set("Content-Type",type);e.sendResponseHeaders(status,b.length);e.getResponseBody().write(b);}
    private void download(HttpExchange e,String name,String type,byte[] b)throws IOException{e.getResponseHeaders().set("Content-Disposition","attachment; filename=\""+name+"\"");raw(e,200,type,b);}
    private void staticFile(HttpExchange e,String path)throws IOException{
        if(path.equals("/"))path="/index.html";
        if(Set.of("/index.html","/app.js","/styles.css","/icon.svg").contains(path)){
            String type=path.endsWith(".js")?"text/javascript; charset=utf-8":path.endsWith(".css")?"text/css; charset=utf-8":path.endsWith(".svg")?"image/svg+xml":"text/html; charset=utf-8";resource(e,"/public"+path,type);return;}
        if(path.matches("/locales/(en|de|fa)\\.json")){resource(e,"/public"+path,"application/json; charset=utf-8");return;}
        if(path.matches("/docs/(en|de|fa)/[a-z0-9-]+\\.md")){resource(e,"/public"+path,"text/plain; charset=utf-8");return;}
        throw Problem.of(404,"NOT_FOUND");
    }
    private void resource(HttpExchange e,String name,String type)throws IOException{try(InputStream in=ApiServer.class.getResourceAsStream(name)){if(in==null)throw Problem.of(404,"NOT_FOUND");raw(e,200,type,in.readAllBytes());}}
    @Override public void close(){server.stop(1);executor.close();}
}
