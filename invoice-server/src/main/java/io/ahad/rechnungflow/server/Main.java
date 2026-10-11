package io.ahad.rechnungflow.server;
import io.ahad.rechnungflow.core.*;
import io.ahad.rechnungflow.core.FileStore;
import io.ahad.rechnungflow.ingest.*;
import io.ahad.rechnungflow.workflow.*;
import java.nio.file.*;
import java.time.*;
import java.net.*;
import java.util.*;
public final class Main {
    public static void main(String[] args)throws Exception{
        List<String>arguments=List.of(args);if(arguments.contains("--help")){System.out.println("RechnungFlow 0.1.0 | java -jar rechnungflow.jar [--demo] [--data PATH] [--port PORT] [--host HOST]\nConfiguration: docs/en/deployment.md");return;}
        boolean demo=arguments.contains("--demo");String tenant=env("RF_WORKSPACE",demo?"demo":"workspace");
        Problem.require(tenant.matches("[a-z0-9-]{1,50}"),500,"INVALID_CONFIGURATION");
        Path data=Path.of(arg(arguments,"--data",env("RF_DATA",demo?"var-demo":"var")));String host=arg(arguments,"--host",env("RF_HOST","127.0.0.1"));
        int port=Integer.parseInt(arg(arguments,"--port",env("RF_PORT","8090")));Problem.require(port>=0&&port<=65535,500,"INVALID_CONFIGURATION");
        String origin=env("RF_PUBLIC_ORIGIN","");boolean loopback=Set.of("127.0.0.1","localhost","::1").contains(host);
        if(demo)Problem.require(loopback&&origin.isBlank(),500,"DEMO_MUST_BE_LOCAL");
        if(!loopback){Problem.require(origin.startsWith("https://")&&env("RF_ACK_SINGLE_NODE","").equals("true"),500,"SECURE_DEPLOYMENT_REQUIRED");}
        if(!origin.isBlank()){URI u=URI.create(origin);Problem.require(u.getHost()!=null&&u.getUserInfo()==null&&u.getQuery()==null&&u.getFragment()==null&&(u.getPath()==null||u.getPath().isEmpty()),500,"INVALID_PUBLIC_ORIGIN");}
        byte[] key=env("RF_MASTER_KEY","").isBlank()?null:Base64.getDecoder().decode(env("RF_MASTER_KEY",""));
        if(!loopback)Problem.require(key!=null,500,"EXTERNAL_MASTER_KEY_REQUIRED");
        FileStore store=new FileStore(data,key);AuthService auth=new AuthService(store);
        boolean fresh=store.read(s->!Store.table(s,"tenants").containsKey(tenant));String password=env("RF_BOOTSTRAP_PASSWORD","");if(password.isBlank())password=Crypto.token();
        if(fresh)auth.bootstrap(tenant,"RechnungFlow workspace",password,demo);
        else Problem.require(store.read(s->Values.text(Json.object(Store.table(s,"tenants").get(tenant)),"mode").equals(demo?"DEMO":"STRICT")),500,"WORKSPACE_MODE_MISMATCH");
        ValidationEngine validator=ValidationEngine.unavailable();String kosit=env("RF_KOSIT_DIR","");
        if(!kosit.isBlank())validator=new KositEngine(Path.of(kosit),data.resolve("validation-work"),Integer.parseInt(env("RF_VALIDATION_TIMEOUT","45")));
        InvoiceService invoices=new InvoiceService(store,new BlobVault(data.resolve("originals"),store.key()),validator);
        AccountingClient accounting=AccountingClient.unavailable();String url=env("RF_ACCOUNTING_URL","");
        if(!url.isBlank()){Problem.require(!demo,500,"DEMO_EXTERNAL_ACCOUNTING_FORBIDDEN");accounting=new HttpAccountingClient(URI.create(url),env("RF_ACCOUNTING_TOKEN",""),Duration.ofSeconds(15),env("RF_ACCOUNTING_IDEMPOTENT","false").equals("true"),false);}
        OutboxService outbox=new OutboxService(store,invoices,accounting);
        if(demo)DemoData.seed(store,auth,invoices,outbox,tenant);
        ApiServer server=new ApiServer(host,port,origin.isBlank()?null:origin,tenant,auth,invoices,outbox);
        Runtime.getRuntime().addShutdownHook(new Thread(()->{server.close();outbox.close();store.close();}));
        if(!env("RF_DISABLE_WORKER","false").equals("true"))outbox.start();server.start();
        System.out.println("\nRechnungFlow 0.1.0 | "+(demo?"DEMO / SANDBOX ONLY":"STRICT VALIDATION")+"\nOpen: "+(origin.isBlank()?"http://"+host+":"+server.port():origin)+"\nWorkspace: "+tenant);
        if(fresh){System.out.println("Bootstrap login: admin\nBootstrap password: "+password+"\nSave this password securely; it is displayed only on initialization.");
            if(demo)System.out.println("Demo logins: intake, reviewer, approver, approver2, finance, auditor. All use the bootstrap password above.");}
        System.out.println("Originals and state are encrypted. Back up the data directory and its separate master key.\nThis release is not tax advice, certification, a payment system, or a WORM archive.\n");
    }
    private static String arg(List<String>a,String key,String fallback){int i=a.indexOf(key);if(i<0)return fallback;if(i+1>=a.size())throw new IllegalArgumentException("Missing "+key);return a.get(i+1);}
    private static String env(String key,String fallback){String v=System.getenv(key);return v==null||v.isBlank()?fallback:v;}
}
