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

/**
 * 端到端集成: parallel + rest + try 组合使用的真实场景。
 * 验证 BFF 聚合模式: 并发多接口 + 局部降级。
 */
public class ParallelRestIntegrationTest {

    private HttpServer server;
    private int port;

    @BeforeEach
    public void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        // 默认 executor 串行处理请求, 显式给一个线程池才能并发响应 parallel
        server.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(8));
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
    public void testBffAggregate_parallelWithTryFallback() throws Exception {
        server.createContext("/user", exchange -> send(exchange, 200, "{\"id\":1,\"name\":\"alice\"}"));
        server.createContext("/orders", exchange -> send(exchange, 200, "[{\"id\":\"o1\"},{\"id\":\"o2\"}]"));
        server.createContext("/payments", exchange -> send(exchange, 500, "down"));

        RenderEngine e = RenderEngine.build();
        Object r = e.renderJson("""
                {
                  "@class": "parallel",
                  "tasks": {
                    "user":    { "@class": "rest", "url": "http://127.0.0.1:%d/user" },
                    "orders":  { "@class": "rest", "url": "http://127.0.0.1:%d/orders" },
                    "payments": {
                      "@class": "try",
                      "select": { "@class": "rest", "url": "http://127.0.0.1:%d/payments" },
                      "fallback": { "status": "unavailable" }
                    }
                  },
                  "body": {
                    "userName": "{$user.name}",
                    "orderCount": "{$orders.size()}",
                    "paymentStatus": "{$payments.status}"
                  }
                }
                """.formatted(port, port, port), Map.of());

        com.alibaba.fastjson2.JSONObject obj = (com.alibaba.fastjson2.JSONObject) r;
        Assertions.assertEquals("alice", obj.getString("userName"));
        Assertions.assertEquals(2, obj.getIntValue("orderCount"));
        Assertions.assertEquals("unavailable", obj.getString("paymentStatus"));
    }

    @Test
    public void testParallelTasksRunActuallyConcurrent() throws Exception {
        // 两个各 sleep 300ms 的端点, 串行需要 600ms, 并发 ~300ms
        server.createContext("/a", exchange -> {
            try { Thread.sleep(300); } catch (InterruptedException ignored) {}
            send(exchange, 200, "{\"v\":\"a\"}");
        });
        server.createContext("/b", exchange -> {
            try { Thread.sleep(300); } catch (InterruptedException ignored) {}
            send(exchange, 200, "{\"v\":\"b\"}");
        });

        RenderEngine e = RenderEngine.build();
        String template = """
                {
                  "@class": "parallel",
                  "tasks": {
                    "a": { "@class": "rest", "url": "http://127.0.0.1:%d/a" },
                    "b": { "@class": "rest", "url": "http://127.0.0.1:%d/b" }
                  },
                  "body": { "a": "{$a.v}", "b": "{$b.v}" }
                }
                """.formatted(port, port);

        long start = System.currentTimeMillis();
        Object r = e.renderJson(template, Map.of());
        long elapsed = System.currentTimeMillis() - start;

        com.alibaba.fastjson2.JSONObject obj = (com.alibaba.fastjson2.JSONObject) r;
        Assertions.assertEquals("a", obj.getString("a"));
        Assertions.assertEquals("b", obj.getString("b"));
        // 并发执行应该 < 550ms (串行会 > 600ms)
        Assertions.assertTrue(elapsed < 550,
                "expected parallel time < 550ms, got " + elapsed + "ms");
    }

    @Test
    public void testRestRetryWithExponentialBackoff() throws Exception {
        server.createContext("/retry", exchange -> send(exchange, 200, "{\"ok\":true}"));

        RenderEngine e = RenderEngine.build();
        Object r = e.renderJson("""
                {
                  "@class": "rest",
                  "url": "http://127.0.0.1:%d/retry",
                  "retry": { "count": 3, "backoff": "exponential", "baseMs": 50 }
                }
                """.formatted(port), Map.of());

        com.alibaba.fastjson2.JSONObject obj = (com.alibaba.fastjson2.JSONObject) r;
        Assertions.assertTrue(obj.getBooleanValue("ok"));
    }

    @Test
    public void testRestCacheUsedAcrossParallelTasks() throws Exception {
        // 多个并行任务用同一 cache key, 应只触发一次网络调用
        java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
        server.createContext("/shared", exchange -> {
            calls.incrementAndGet();
            send(exchange, 200, "{\"n\":" + calls.get() + "}");
        });

        RenderEngine e = RenderEngine.build();
        String inner = """
                {
                  "@class": "rest",
                  "url": "http://127.0.0.1:%d/shared",
                  "cache": { "key": "shared-key", "ttl": 60000 }
                }
                """.formatted(port);

        Object r1 = e.renderJson(inner, Map.of());
        Object r2 = e.renderJson(inner, Map.of());
        Object r3 = e.renderJson(inner, Map.of());

        Assertions.assertEquals(1, calls.get());
        com.alibaba.fastjson2.JSONObject o1 = (com.alibaba.fastjson2.JSONObject) r1;
        Assertions.assertEquals(1, o1.getIntValue("n"));
    }
}
