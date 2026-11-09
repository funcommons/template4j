package fun.commons.template4j.core;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 覆盖 RestRenderer 增强：timeout / retry / cache / auth。
 * 不覆盖 circuitBreaker / validate（未实现）。
 */
public class RestRendererEnhancedTest {

    private HttpServer server;
    private int port;

    @BeforeEach
    public void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        port = server.getAddress().getPort();
    }

    @AfterEach
    public void stopServer() {
        if (server != null) server.stop(0);
    }

    private static void send(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    @Test
    public void testTimeoutTriggersErrorBranch() throws Exception {
        server.createContext("/slow", exchange -> {
            try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
            send(exchange, 200, "{\"v\":\"late\"}");
        });
        server.start();

        RenderEngine e = RenderEngine.build();
        Object r = e.renderJson("""
                {
                  "@class": "rest",
                  "url": "http://127.0.0.1:%d/slow",
                  "timeout": 200,
                  "error": { "timedOut": true, "msg": "{$responseData.message}" }
                }
                """.formatted(port), Map.of());

        com.alibaba.fastjson2.JSONObject obj = (com.alibaba.fastjson2.JSONObject) r;
        Assertions.assertTrue(obj.getBooleanValue("timedOut"));
        Assertions.assertNotNull(obj.getString("msg"));
    }

    @Test
    public void testRetrySucceedsAfterFailures() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        server.createContext("/flaky", exchange -> {
            int n = calls.incrementAndGet();
            if (n < 3) {
                send(exchange, 500, "fail-" + n);
            } else {
                send(exchange, 200, "{\"v\":\"ok\",\"n\":" + n + "}");
            }
        });
        server.start();

        RenderEngine e = RenderEngine.build();
        Object r = e.renderJson("""
                {
                  "@class": "rest",
                  "url": "http://127.0.0.1:%d/flaky",
                  "retry": { "count": 3, "backoff": "fixed", "baseMs": 10 }
                }
                """.formatted(port), Map.of());

        com.alibaba.fastjson2.JSONObject obj = (com.alibaba.fastjson2.JSONObject) r;
        Assertions.assertEquals("ok", obj.getString("v"));
        Assertions.assertEquals(3, obj.getIntValue("n"));
        Assertions.assertEquals(3, calls.get());
    }

    @Test
    public void testRetryExhaustedGoesToErrorBranch() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        server.createContext("/always-fail", exchange -> {
            calls.incrementAndGet();
            send(exchange, 500, "nope");
        });
        server.start();

        RenderEngine e = RenderEngine.build();
        Object r = e.renderJson("""
                {
                  "@class": "rest",
                  "url": "http://127.0.0.1:%d/always-fail",
                  "retry": { "count": 2, "baseMs": 5 },
                  "error": { "attempts": "{$responseData.status}" }
                }
                """.formatted(port), Map.of());

        com.alibaba.fastjson2.JSONObject obj = (com.alibaba.fastjson2.JSONObject) r;
        // 1 initial + 2 retries = 3 total
        Assertions.assertEquals(3, calls.get());
        Assertions.assertEquals(500, obj.getIntValue("attempts"));
    }

    @Test
    public void testCacheAvoidsDuplicateCalls() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        server.createContext("/cached", exchange -> {
            calls.incrementAndGet();
            send(exchange, 200, "{\"n\":" + calls.get() + "}");
        });
        server.start();

        RenderEngine e = RenderEngine.build();
        String template = """
                {
                  "@class": "rest",
                  "url": "http://127.0.0.1:%d/cached",
                  "cache": { "key": "k1", "ttl": 60000 }
                }
                """.formatted(port);

        Object r1 = e.renderJson(template, Map.of());
        Object r2 = e.renderJson(template, Map.of());

        com.alibaba.fastjson2.JSONObject o1 = (com.alibaba.fastjson2.JSONObject) r1;
        com.alibaba.fastjson2.JSONObject o2 = (com.alibaba.fastjson2.JSONObject) r2;
        // 第二次命中缓存, 服务器只被调用一次, 两次响应相同
        Assertions.assertEquals(1, calls.get());
        Assertions.assertEquals(o1.getIntValue("n"), o2.getIntValue("n"));
    }

    @Test
    public void testCacheDifferentKeysSeparateHits() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        server.createContext("/cached2", exchange -> {
            calls.incrementAndGet();
            send(exchange, 200, "{\"n\":" + calls.get() + "}");
        });
        server.start();

        RenderEngine e = RenderEngine.build();
        Object r1 = e.renderJson("""
                {
                  "@class": "rest",
                  "url": "http://127.0.0.1:%d/cached2",
                  "cache": { "key": "k-A", "ttl": 60000 }
                }
                """.formatted(port), Map.of());
        Object r2 = e.renderJson("""
                {
                  "@class": "rest",
                  "url": "http://127.0.0.1:%d/cached2",
                  "cache": { "key": "k-B", "ttl": 60000 }
                }
                """.formatted(port), Map.of());

        Assertions.assertEquals(2, calls.get());
        Assertions.assertEquals(1, ((com.alibaba.fastjson2.JSONObject) r1).getIntValue("n"));
        Assertions.assertEquals(2, ((com.alibaba.fastjson2.JSONObject) r2).getIntValue("n"));
    }

    @Test
    public void testCacheExpiryRefetches() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        server.createContext("/expiring", exchange -> {
            calls.incrementAndGet();
            send(exchange, 200, "{\"n\":" + calls.get() + "}");
        });
        server.start();

        RenderEngine e = RenderEngine.build();
        String template = """
                {
                  "@class": "rest",
                  "url": "http://127.0.0.1:%d/expiring",
                  "cache": { "key": "k-exp", "ttl": 50 }
                }
                """.formatted(port);

        e.renderJson(template, Map.of());
        Thread.sleep(80);
        e.renderJson(template, Map.of());

        Assertions.assertEquals(2, calls.get());
    }

    @Test
    public void testAuthBearerHeaderInjected() throws Exception {
        AtomicReference<String> authHeader = new AtomicReference<>();
        server.createContext("/auth", exchange -> {
            authHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
            send(exchange, 200, "{\"ok\":true}");
        });
        server.start();

        RenderEngine e = RenderEngine.build();
        e.renderJson("""
                {
                  "@class": "rest",
                  "url": "http://127.0.0.1:%d/auth",
                  "auth": { "type": "bearer", "token": "tok-123" }
                }
                """.formatted(port), Map.of());

        Assertions.assertEquals("Bearer tok-123", authHeader.get());
    }

    @Test
    public void testAuthBasicHeaderInjected() throws Exception {
        AtomicReference<String> authHeader = new AtomicReference<>();
        server.createContext("/auth-basic", exchange -> {
            authHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
            send(exchange, 200, "{\"ok\":true}");
        });
        server.start();

        RenderEngine e = RenderEngine.build();
        e.renderJson("""
                {
                  "@class": "rest",
                  "url": "http://127.0.0.1:%d/auth-basic",
                  "auth": { "type": "basic", "user": "alice", "pass": "secret" }
                }
                """.formatted(port), Map.of());

        String h = authHeader.get();
        Assertions.assertNotNull(h);
        Assertions.assertTrue(h.startsWith("Basic "));
        String decoded = new String(java.util.Base64.getDecoder().decode(h.substring(6)), StandardCharsets.UTF_8);
        Assertions.assertEquals("alice:secret", decoded);
    }
}
