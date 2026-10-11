package io.ahad.rechnungflow.workflow;
import io.ahad.rechnungflow.core.*;
import java.net.*;
import java.net.http.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.Flow.*;
/** Fixed deployment-owned HTTPS destination. Redirects and arbitrary user URLs are prohibited. */
public final class HttpAccountingClient implements AccountingClient {
    private final HttpClient client;private final URI uri;private final String token;private final Duration timeout;private final boolean idempotent;
    public HttpAccountingClient(URI uri,String token,Duration timeout,boolean idempotent,boolean allowLocalTest){
        boolean local=allowLocalTest&&Set.of("127.0.0.1","localhost","[::1]","::1").contains(uri.getHost())&&"http".equals(uri.getScheme());
        Problem.require(("https".equals(uri.getScheme())||local)&&uri.getHost()!=null&&uri.getUserInfo()==null&&uri.getFragment()==null,500,"ACCOUNTING_URL_INVALID");
        Problem.require(token!=null&&!token.contains("\r")&&!token.contains("\n")&&timeout.toMillis()>=100&&timeout.toSeconds()<=120,500,"INVALID_CONFIGURATION");
        this.uri=uri;this.token=token;this.timeout=timeout;this.idempotent=idempotent;
        client=HttpClient.newBuilder().connectTimeout(timeout).followRedirects(HttpClient.Redirect.NEVER).build();
    }
    public Delivery deliver(String key,Map<String,Object> payload){
        CompletableFuture<HttpResponse<byte[]>> future=null;
        try{
            var builder=HttpRequest.newBuilder(uri).timeout(timeout).header("Content-Type","application/json").header("Accept","application/json").header("Idempotency-Key",key);
            if(!token.isBlank())builder.header("Authorization","Bearer "+token);
            future=client.sendAsync(builder.POST(HttpRequest.BodyPublishers.ofString(Json.stringify(payload))).build(),r->new LimitedBody(65_536));
            var response=future.get(timeout.toMillis(),TimeUnit.MILLISECONDS);int status=response.statusCode();
            if(status==429||status==503)return new Delivery(idempotent?"RETRY":"UNKNOWN","","UPSTREAM_"+status);
            if(status>=200&&status<300){
                var answer=Json.obj(new String(response.body(),StandardCharsets.UTF_8));String receipt=Values.text(answer,"receiptId");
                if(receipt.isBlank()||receipt.length()>200||!Values.text(payload,"invoiceId").equals(Values.text(answer,"invoiceId")))return new Delivery("UNKNOWN","","ACKNOWLEDGEMENT_INVALID");
                return new Delivery("DELIVERED",receipt,"");
            }
            if(status>=400&&status<500&&status!=408)return new Delivery("FAILED","","UPSTREAM_"+status);
            return new Delivery("UNKNOWN","","UPSTREAM_"+status);
        }catch(Exception e){if(future!=null)future.cancel(true);if(e instanceof InterruptedException)Thread.currentThread().interrupt();return new Delivery("UNKNOWN","","DELIVERY_UNCERTAIN");}
    }
    private static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final CompletableFuture<byte[]> body=new CompletableFuture<>();private final ByteArrayOutputStream bytes=new ByteArrayOutputStream();private final int limit;private Subscription subscription;
        LimitedBody(int limit){this.limit=limit;}
        public CompletionStage<byte[]> getBody(){return body;}
        public void onSubscribe(Subscription s){subscription=s;s.request(1);}
        public void onNext(List<ByteBuffer> buffers){
            for(ByteBuffer b:buffers){if((long)bytes.size()+b.remaining()>limit){subscription.cancel();body.completeExceptionally(new IOException("Response limit"));return;}byte[] chunk=new byte[b.remaining()];b.get(chunk);bytes.writeBytes(chunk);}subscription.request(1);
        }
        public void onError(Throwable e){body.completeExceptionally(e);}
        public void onComplete(){body.complete(bytes.toByteArray());}
    }
}
