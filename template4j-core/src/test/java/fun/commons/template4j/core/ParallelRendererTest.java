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
 * 覆盖 ParallelRenderer：并发聚合、错误隔离、超时。
 * 与 RestRendererServerTest 一样使用内置 HttpServer mock。
 */
public class ParallelRendererTest {

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
    public void testParallelTwoTasksBothSuccess() throws Exception {
        server.createContext("/user", exchange -> {
            byte[] body = "{\"name\":\"alice\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(body); }
        });
        server.createContext("/orders", exchange -> {
            byte[] body = "[1,2,3]".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(body); }
        });
        server.start();

        RenderEngine e = RenderEngine.build();
        Object r = e.renderJson("""
                {
                  "@class": "parallel",
                  "tasks": {
                    "user":   { "@class": "rest", "url": "http://127.0.0.1:%d/user" },
                    "orders": { "@class": "rest", "url": "http://127.0.0.1:%d/orders" }
                  },
                  "body": {
                    "userName": "{$user.name}",
                    "orderCount": "{$orders.size()}"
                  }
                }
                """.formatted(port, port), Map.of());

        com.alibaba.fastjson2.JSONObject obj = (com.alibaba.fastjson2.JSONObject) r;
        Assertions.assertEquals("alice", obj.getString("userName"));
        Assertions.assertEquals(3, obj.getIntValue("orderCount"));
    }

    @Test
    public void testParallelSingleTask() throws Exception {
        server.createContext("/single", exchange -> {
            byte[] body = "{\"v\":42}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(body); }
        });
        server.start();

        RenderEngine e = RenderEngine.build();
        Object r = e.renderJson("""
                {
                  "@class": "parallel",
                  "tasks": {
                    "only": { "@class": "rest", "url": "http://127.0.0.1:%d/single" }
                  },
                  "body": "{$only.v}"
                }
                """.formatted(port), Map.of());

        Assertions.assertEquals(42, ((Number) r).intValue());
    }

    @Test
    public void testParallelContinueOnErrorTrue() throws Exception {
        server.createContext("/ok", exchange -> {
            byte[] body = "{\"v\":\"ok\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(body); }
        });
        server.createContext("/fail", exchange -> {
            byte[] body = "boom".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(500, body.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(body); }
        });
        server.start();

        RenderEngine e = RenderEngine.build();
        Object r = e.renderJson("""
                {
                  "@class": "parallel",
                  "tasks": {
                    "good": { "@class": "rest", "url": "http://127.0.0.1:%d/ok" },
                    "bad":  { "@class": "rest", "url": "http://127.0.0.1:%d/fail" }
                  },
                  "continueOnError": true,
                  "body": {
                    "goodVal": "{$good.v}",
                    "badVal": "{$bad}"
                  }
                }
                """.formatted(port, port), Map.of());

        com.alibaba.fastjson2.JSONObject obj = (com.alibaba.fastjson2.JSONObject) r;
        Assertions.assertEquals("ok", obj.getString("goodVal"));
        Assertions.assertNull(obj.get("badVal"));
    }

    @Test
    public void testParallelErrorBranchWhenTaskFails() throws Exception {
        server.createContext("/fail", exchange -> {
            byte[] body = "down".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(500, body.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(body); }
        });
        server.start();

        RenderEngine e = RenderEngine.build();
        Object r = e.renderJson("""
                {
                  "@class": "parallel",
                  "tasks": {
                    "bad": { "@class": "rest", "url": "http://127.0.0.1:%d/fail" }
                  },
                  "error": {
                    "failed": true,
                    "task": "{$error.task}"
                  }
                }
                """.formatted(port), Map.of());

        com.alibaba.fastjson2.JSONObject obj = (com.alibaba.fastjson2.JSONObject) r;
        Assertions.assertTrue(obj.getBooleanValue("failed"));
        Assertions.assertEquals("bad", obj.getString("task"));
    }

    @Test
    public void testParallelTimeoutTriggersError() throws Exception {
        server.createContext("/slow", exchange -> {
            try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
            byte[] body = "{\"v\":\"late\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(body); }
        });
        server.start();

        RenderEngine e = RenderEngine.build();
        Object r = e.renderJson("""
                {
                  "@class": "parallel",
                  "tasks": {
                    "slow": { "@class": "rest", "url": "http://127.0.0.1:%d/slow" }
                  },
                  "timeout": 200,
                  "error": { "timedOut": true }
                }
                """.formatted(port), Map.of());

        com.alibaba.fastjson2.JSONObject obj = (com.alibaba.fastjson2.JSONObject) r;
        Assertions.assertTrue(obj.getBooleanValue("timedOut"));
    }

    @Test
    public void testParallelNoBodyReturnsTaskMap() throws Exception {
        server.createContext("/x", exchange -> {
            byte[] body = "{\"v\":1}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(body); }
        });
        server.start();

        RenderEngine e = RenderEngine.build();
        Object r = e.renderJson("""
                {
                  "@class": "parallel",
                  "tasks": {
                    "x": { "@class": "rest", "url": "http://127.0.0.1:%d/x" }
                  }
                }
                """.formatted(port), Map.of());

        com.alibaba.fastjson2.JSONObject obj = (com.alibaba.fastjson2.JSONObject) r;
        Assertions.assertNotNull(obj.get("x"));
        Assertions.assertEquals(1, ((com.alibaba.fastjson2.JSONObject) obj.get("x")).getIntValue("v"));
    }

    @Test
    public void testParallelOverallDeadlineBoundsTotalWait() throws Exception {
        // 两个各 sleep 2s 的任务 + 200ms timeout。
        // 旧行为(per-future timeout): 先等 A 200ms → timeout, 再等 B 200ms → timeout, 总 400ms。
        // 新行为(overall deadline): 总等待严格 ≤ 200ms。
        server.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(4));
        server.createContext("/slow-a", exchange -> {
            try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
            byte[] body = "{\"v\":\"a\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(body); }
        });
        server.createContext("/slow-b", exchange -> {
            try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
            byte[] body = "{\"v\":\"b\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(body); }
        });
        server.start();

        RenderEngine e = RenderEngine.build();
        long start = System.currentTimeMillis();
        Object r = e.renderJson("""
                {
                  "@class": "parallel",
                  "tasks": {
                    "a": { "@class": "rest", "url": "http://127.0.0.1:%d/slow-a" },
                    "b": { "@class": "rest", "url": "http://127.0.0.1:%d/slow-b" }
                  },
                  "timeout": 300,
                  "error": { "timedOut": true }
                }
                """.formatted(port, port), Map.of());
        long elapsed = System.currentTimeMillis() - start;

        com.alibaba.fastjson2.JSONObject obj = (com.alibaba.fastjson2.JSONObject) r;
        Assertions.assertTrue(obj.getBooleanValue("timedOut"));
        // 总等待应 < 600ms (旧行为会 ~600ms), 严格 ≤ timeout + slack
        Assertions.assertTrue(elapsed < 600,
                "expected overall deadline bounded total wait < 600ms, got " + elapsed + "ms");
    }
}
