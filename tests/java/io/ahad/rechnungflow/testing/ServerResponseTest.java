package io.ahad.rechnungflow.testing;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpPrincipal;
import io.ahad.rechnungflow.server.ApiServer;
import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import static io.ahad.rechnungflow.testing.TestKit.*;

/** Reproduces buffered response delivery without requiring one particular JDK version. */
public final class ServerResponseTest {
    public static void main(String[] args) throws Exception {
        TestKit t = new TestKit("server-response");
        // Exercise the real writer without opening a public route or changing its visibility.
        Method raw = ApiServer.class.getDeclaredMethod("raw", HttpExchange.class, int.class, String.class, byte[].class);
        raw.setAccessible(true);
        try (ApiServer server = new ApiServer("127.0.0.1", 0, null, "test", null, null, null)) {
            t.test("Small 413 response is flushed before request-body cleanup", () -> {
                BufferedExchange exchange = new BufferedExchange(false);
                byte[] payload = "{\"error\":{\"code\":\"FILE_TOO_LARGE\"}}".getBytes(StandardCharsets.UTF_8);
                invoke(raw, server, exchange, 413, "application/json; charset=utf-8", payload);
                eq(413, exchange.getResponseCode());
                eq((long) payload.length, exchange.length);
                eq("application/json; charset=utf-8", exchange.getResponseHeaders().getFirst("Content-Type"));
                yes(Arrays.equals(payload, exchange.wire.toByteArray()));
                yes(!exchange.closed);
            });
            t.test("Binary response is flushed without changing its bytes", () -> {
                BufferedExchange exchange = new BufferedExchange(false);
                byte[] payload = new byte[257];
                for (int i = 0; i < payload.length; i++) payload[i] = (byte) i;
                invoke(raw, server, exchange, 200, "application/octet-stream", payload);
                yes(Arrays.equals(payload, exchange.wire.toByteArray()));
            });
            t.test("Response flush failures are not silently swallowed", () -> {
                BufferedExchange exchange = new BufferedExchange(true);
                boolean failed = false;
                try {
                    invoke(raw, server, exchange, 200, "text/plain", new byte[]{1});
                } catch (IOException expected) {
                    failed = true;
                }
                yes(failed);
            });
        }
        t.finish("build/test-results/server-response.xml");
    }

    private static void invoke(Method raw, ApiServer server, HttpExchange exchange, int status, String type, byte[] payload) throws Exception {
        try {
            raw.invoke(server, exchange, status, type, payload);
        } catch (InvocationTargetException wrapped) {
            if (wrapped.getCause() instanceof Exception cause) throw cause;
            throw wrapped;
        }
    }

    /** Input is deliberately unreadable: early errors must not consume the rejected upload. */
    private static final class BufferedExchange extends HttpExchange {
        private final Headers requestHeaders = new Headers();
        private final Headers responseHeaders = new Headers();
        private final ByteArrayOutputStream wire = new ByteArrayOutputStream();
        private final OutputStream response;
        private int status = -1;
        private long length;
        private boolean closed;

        BufferedExchange(boolean failFlush) {
            response = new BufferedOutputStream(wire) {
                @Override public void flush() throws IOException {
                    if (failFlush) throw new IOException("Synthetic transport flush failure");
                    super.flush();
                }
            };
        }
        @Override public Headers getRequestHeaders() { return requestHeaders; }
        @Override public Headers getResponseHeaders() { return responseHeaders; }
        @Override public URI getRequestURI() { return URI.create("/api/invoices"); }
        @Override public String getRequestMethod() { return "POST"; }
        @Override public HttpContext getHttpContext() { return null; }
        @Override public void close() { closed = true; }
        @Override public InputStream getRequestBody() { throw new AssertionError("Rejected input must not be read by the response writer"); }
        @Override public OutputStream getResponseBody() { return response; }
        @Override public void sendResponseHeaders(int code, long responseLength) { status = code; length = responseLength; }
        @Override public InetSocketAddress getRemoteAddress() { return new InetSocketAddress("127.0.0.1", 12345); }
        @Override public int getResponseCode() { return status; }
        @Override public InetSocketAddress getLocalAddress() { return new InetSocketAddress("127.0.0.1", 8080); }
        @Override public String getProtocol() { return "HTTP/1.1"; }
        @Override public Object getAttribute(String name) { return null; }
        @Override public void setAttribute(String name, Object value) { }
        @Override public void setStreams(InputStream input, OutputStream output) { throw new UnsupportedOperationException(); }
        @Override public HttpPrincipal getPrincipal() { return null; }
    }
}
