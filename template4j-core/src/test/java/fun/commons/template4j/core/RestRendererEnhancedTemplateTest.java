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
import java.util.concurrent.atomic.AtomicReference;

/**
 * 覆盖 rest 增强子字段的模板表达式渲染: cache.key / auth.token / timeout 使用 {@x} 引用数据。
 */
public class RestRendererEnhancedTemplateTest {

    private HttpServer server;
    private int port;

    @BeforeEach
    public void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        port = server.getAddress().getPort();
    }

    @AfterEach
    public void stopServer() {
        if (server != null) server.stop(0);
    }

    private static void send(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
    }

    @Test
    public void testCacheKeyUsesTemplateExpr() throws Exception {
        server.createContext("/c1", exchange -> send(exchange, 200, "{\"v\":\"first\"}"));
        server.createContext("/c2", exchange -> send(exchange, 200, "{\"v\":\"second\"}"));

        RenderEngine e = RenderEngine.build();
        // 同一模板, 不同 data → 不同 cache key → 不同结果
        Object r1 = e.renderJson("""
                {
                  "@class": "rest",
                  "url": "http://127.0.0.1:%d/{@path}",
                  "cache": { "key": "k:{@path}", "ttl": 60000 }
                }
                """.formatted(port), Map.of("path", "c1"));
        Object r2 = e.renderJson("""
                {
                  "@class": "rest",
                  "url": "http://127.0.0.1:%d/{@path}",
                  "cache": { "key": "k:{@path}", "ttl": 60000 }
                }
                """.formatted(port), Map.of("path", "c2"));

        Assertions.assertEquals("first",
                ((com.alibaba.fastjson2.JSONObject) r1).getString("v"));
        Assertions.assertEquals("second",
                ((com.alibaba.fastjson2.JSONObject) r2).getString("v"));
    }

    @Test
    public void testAuthBearerTokenFromData() throws Exception {
        server.createContext("/auth", exchange -> {
            send(exchange, 200, "{\"ok\":true}");
        });
        AtomicReference<String> header = new AtomicReference<>();
        server.createContext("/auth2", exchange -> {
            header.set(exchange.getRequestHeaders().getFirst("Authorization"));
            send(exchange, 200, "{\"ok\":true}");
        });

        RenderEngine e = RenderEngine.build();
        e.renderJson("""
                {
                  "@class": "rest",
                  "url": "http://127.0.0.1:%d/auth2",
                  "auth": { "type": "bearer", "token": "{@tok}" }
                }
                """.formatted(port), Map.of("tok", "TKN-XYZ"));

        Assertions.assertEquals("Bearer TKN-XYZ", header.get());
    }

    @Test
    public void testTimeoutFromTemplateExpr() throws Exception {
        // 仅断言 timeout 字段可解析为模板表达式不报错; 服务器慢响应会触发 timeout error。
        server.createContext("/slow", exchange -> {
            try { Thread.sleep(1500); } catch (InterruptedException ignored) {}
            send(exchange, 200, "{\"v\":1}");
        });

        RenderEngine e = RenderEngine.build();
        Object r = e.renderJson("""
                {
                  "@class": "rest",
                  "url": "http://127.0.0.1:%d/slow",
                  "timeout": "{@ms}",
                  "error": { "timedOut": true }
                }
                """.formatted(port), Map.of("ms", 100));

        com.alibaba.fastjson2.JSONObject obj = (com.alibaba.fastjson2.JSONObject) r;
        Assertions.assertTrue(obj.getBooleanValue("timedOut"));
    }
}
