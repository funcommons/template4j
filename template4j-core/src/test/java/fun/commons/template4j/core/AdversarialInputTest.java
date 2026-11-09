package fun.commons.template4j.core;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 破坏性输入测试: 故意构造异常/边界模板, 验证优雅退化 (不崩溃、错误信息清晰)。
 */
public class AdversarialInputTest {

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

    @Test
    public void parallelTasksFieldMissing() {
        // parallel 缺 tasks 字段 → assertField 抛 RenderException
        Assertions.assertThrows(Exception.class, () ->
                RenderEngine.build().renderJson("""
                        { "@class": "parallel", "body": { "ok": true } }
                        """, Map.of()));
    }

    @Test
    public void trySelectFieldMissing() {
        // try 缺 select 字段 → assertField 抛
        Assertions.assertThrows(Exception.class, () ->
                RenderEngine.build().renderJson("""
                        { "@class": "try", "fallback": { "ok": true } }
                        """, Map.of()));
    }

    @Test
    public void restUrlFieldMissing() {
        // rest 缺 url/select → assertFieldOne 抛
        Assertions.assertThrows(Exception.class, () ->
                RenderEngine.build().renderJson("""
                        { "@class": "rest", "method": "get" }
                        """, Map.of()));
    }

    @Test
    public void retryCountZeroMeansNoRetry() throws Exception {
        // count=0 → parseRetry 返回 null, 行为同无 retry
        server.createContext("/ok", exchange -> {
            byte[] body = "{\"v\":1}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (var os = exchange.getResponseBody()) { os.write(body); }
        });
        Object r = RenderEngine.build().renderJson("""
                {
                  "@class": "rest",
                  "url": "http://127.0.0.1:%d/ok",
                  "retry": { "count": 0 }
                }
                """.formatted(port), Map.of());
        Assertions.assertEquals(1, ((com.alibaba.fastjson2.JSONObject) r).getIntValue("v"));
    }

    @Test
    public void retryCountNegativeAlsoMeansNoRetry() throws Exception {
        server.createContext("/ok", exchange -> {
            byte[] body = "{\"v\":1}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (var os = exchange.getResponseBody()) { os.write(body); }
        });
        Object r = RenderEngine.build().renderJson("""
                {
                  "@class": "rest",
                  "url": "http://127.0.0.1:%d/ok",
                  "retry": { "count": -3 }
                }
                """.formatted(port), Map.of());
        Assertions.assertEquals(1, ((com.alibaba.fastjson2.JSONObject) r).getIntValue("v"));
    }

    @Test
    public void cacheTtlZeroMeansNoCache() throws Exception {
        server.createContext("/c", exchange -> {
            byte[] body = "{\"v\":1}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (var os = exchange.getResponseBody()) { os.write(body); }
        });
        // ttl=0 → parseCache 返回 null, 不缓存
        Object r = RenderEngine.build().renderJson("""
                {
                  "@class": "rest",
                  "url": "http://127.0.0.1:%d/c",
                  "cache": { "key": "k", "ttl": 0 }
                }
                """.formatted(port), Map.of());
        Assertions.assertEquals(1, ((com.alibaba.fastjson2.JSONObject) r).getIntValue("v"));
    }

    @Test
    public void cacheKeyMissingMeansNoCache() throws Exception {
        server.createContext("/c2", exchange -> {
            byte[] body = "{\"v\":1}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (var os = exchange.getResponseBody()) { os.write(body); }
        });
        Object r = RenderEngine.build().renderJson("""
                {
                  "@class": "rest",
                  "url": "http://127.0.0.1:%d/c2",
                  "cache": { "ttl": 60000 }
                }
                """.formatted(port), Map.of());
        Assertions.assertEquals(1, ((com.alibaba.fastjson2.JSONObject) r).getIntValue("v"));
    }

    @Test
    public void authUnknownTypeSilentlyIgnored() throws Exception {
        server.createContext("/a", exchange -> {
            byte[] body = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (var os = exchange.getResponseBody()) { os.write(body); }
        });
        // 未知 auth type → 默认 case → 不注入 header, 但请求正常
        Object r = RenderEngine.build().renderJson("""
                {
                  "@class": "rest",
                  "url": "http://127.0.0.1:%d/a",
                  "auth": { "type": "weird-scheme", "token": "x" }
                }
                """.formatted(port), Map.of());
        Assertions.assertTrue(((com.alibaba.fastjson2.JSONObject) r).getBooleanValue("ok"));
    }

    @Test
    public void authBearerMissingTokenSilentlyIgnored() throws Exception {
        server.createContext("/b", exchange -> {
            byte[] body = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (var os = exchange.getResponseBody()) { os.write(body); }
        });
        // bearer 无 token → 不注入, 请求正常
        Object r = RenderEngine.build().renderJson("""
                {
                  "@class": "rest",
                  "url": "http://127.0.0.1:%d/b",
                  "auth": { "type": "bearer" }
                }
                """.formatted(port), Map.of());
        Assertions.assertTrue(((com.alibaba.fastjson2.JSONObject) r).getBooleanValue("ok"));
    }

    @Test
    public void backoffUnknownDefaultsToFixed() throws Exception {
        server.createContext("/u", exchange -> {
            byte[] body = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (var os = exchange.getResponseBody()) { os.write(body); }
        });
        Object r = RenderEngine.build().renderJson("""
                {
                  "@class": "rest",
                  "url": "http://127.0.0.1:%d/u",
                  "retry": { "count": 2, "backoff": "weird", "baseMs": 5 }
                }
                """.formatted(port), Map.of());
        Assertions.assertTrue(((com.alibaba.fastjson2.JSONObject) r).getBooleanValue("ok"));
    }

    @Test
    public void retryOnAllStringWorksAsExpected() throws Exception {
        server.createContext("/v", exchange -> {
            byte[] body = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (var os = exchange.getResponseBody()) { os.write(body); }
        });
        Object r = RenderEngine.build().renderJson("""
                {
                  "@class": "rest",
                  "url": "http://127.0.0.1:%d/v",
                  "retry": { "count": 1, "retryOn": "all" }
                }
                """.formatted(port), Map.of());
        Assertions.assertTrue(((com.alibaba.fastjson2.JSONObject) r).getBooleanValue("ok"));
    }
}
