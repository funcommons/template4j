package fun.commons.template4j.core;

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

/**
 * 覆盖 TryRenderer：成功透传、fallback 降级、onError 副作用、rethrow 上抛。
 */
public class TryRendererTest {

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

    @Test
    public void testTrySuccessPassthrough() throws Exception {
        server.createContext("/ok", exchange -> {
            byte[] body = "{\"v\":1}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(body); }
        });
        server.start();

        RenderEngine e = RenderEngine.build();
        Object r = e.renderJson("""
                {
                  "@class": "try",
                  "select": { "@class": "rest", "url": "http://127.0.0.1:%d/ok" },
                  "fallback": { "v": 999 }
                }
                """.formatted(port), Map.of());

        com.alibaba.fastjson2.JSONObject obj = (com.alibaba.fastjson2.JSONObject) r;
        Assertions.assertEquals(1, obj.getIntValue("v"));
    }

    @Test
    public void testTryFallbackOnError() throws Exception {
        server.createContext("/fail", exchange -> {
            byte[] body = "boom".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(500, body.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(body); }
        });
        server.start();

        RenderEngine e = RenderEngine.build();
        Object r = e.renderJson("""
                {
                  "@class": "try",
                  "select": { "@class": "rest", "url": "http://127.0.0.1:%d/fail" },
                  "fallback": { "status": "unknown" }
                }
                """.formatted(port), Map.of());

        com.alibaba.fastjson2.JSONObject obj = (com.alibaba.fastjson2.JSONObject) r;
        Assertions.assertEquals("unknown", obj.getString("status"));
    }

    @Test
    public void testTryOnErrorSideEffectThenFallback() throws Exception {
        server.createContext("/fail", exchange -> {
            byte[] body = "x".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(500, body.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(body); }
        });
        server.createContext("/audit", exchange -> {
            byte[] body = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(body); }
        });
        server.start();

        RenderEngine e = RenderEngine.build();
        Object r = e.renderJson("""
                {
                  "@class": "try",
                  "select": { "@class": "rest", "url": "http://127.0.0.1:%d/fail" },
                  "onError": { "@class": "rest", "url": "http://127.0.0.1:%d/audit", "method": "post", "data": { "msg": "{$error.message}" } },
                  "fallback": "logged"
                }
                """.formatted(port, port), Map.of());

        Assertions.assertEquals("logged", r);
    }

    @Test
    public void testTryErrorVariablesAvailableInFallback() throws Exception {
        server.createContext("/fail", exchange -> {
            byte[] body = "boom-body".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(500, body.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(body); }
        });
        server.start();

        RenderEngine e = RenderEngine.build();
        Object r = e.renderJson("""
                {
                  "@class": "try",
                  "select": { "@class": "rest", "url": "http://127.0.0.1:%d/fail" },
                  "fallback": {
                    "msg": "{$error.message}",
                    "cause": "{$error.cause}",
                    "path": "{$error.path}"
                  }
                }
                """.formatted(port), Map.of());

        com.alibaba.fastjson2.JSONObject obj = (com.alibaba.fastjson2.JSONObject) r;
        Assertions.assertNotNull(obj.getString("msg"));
        Assertions.assertEquals("HttpException", obj.getString("cause"));
    }

    @Test
    public void testTryRethrowWhenConfigured() throws Exception {
        server.createContext("/fail", exchange -> {
            byte[] body = "x".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(500, body.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(body); }
        });
        server.start();

        RenderEngine e = RenderEngine.build();
        // rethrow=true 时即使有 fallback 也应当抛 RenderException
        Assertions.assertThrows(Exception.class, () -> e.renderJson("""
                {
                  "@class": "try",
                  "select": { "@class": "rest", "url": "http://127.0.0.1:%d/fail" },
                  "fallback": { "ignored": true },
                  "rethrow": true
                }
                """.formatted(port), Map.of()));
    }

    @Test
    public void testTryNoFallbackNoOnErrorPropagatesException() throws Exception {
        RenderEngine e = RenderEngine.build();
        Assertions.assertThrows(Exception.class, () -> e.renderJson("""
                {
                  "@class": "try",
                  "select": { "@class": "rest", "url": "http://127.0.0.1:1/unreachable" }
                }
                """, Map.of()));
    }

    @Test
    public void testTryOnlyOnErrorReturnsNull() throws Exception {
        RenderEngine e = RenderEngine.build();
        Object r = e.renderJson("""
                {
                  "@class": "try",
                  "select": { "@class": "rest", "url": "http://127.0.0.1:1/unreachable" },
                  "onError": "called"
                }
                """, Map.of());
        // 仅 onError 时,执行完副作用后返回 null (onError 字符串副作用结果被丢弃)
        Assertions.assertNull(r);
    }
}
