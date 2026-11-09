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

/**
 * 嵌套 parallel + 高并发场景的对抗测试。
 * 重点验证: 嵌套 parallel 不会因线程池饱和而死锁。
 */
public class ParallelNestedStressTest {

    private HttpServer server;
    private int port;

    @BeforeEach
    public void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(32));
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
    public void testNestedParallelDoesNotDeadlock() throws Exception {
        // 4 个 outer 任务, 每个内部再 4 个 inner 任务 → 共 16 个并发 HTTP 调用
        // 如果 EXECUTOR (size 16) 被 outer 占满, inner 等不到线程 → 死锁
        server.createContext("/leaf", exchange -> send(exchange, 200, "{\"v\":1}"));

        RenderEngine e = RenderEngine.build();
        String inner = """
                {
                  "@class": "parallel",
                  "tasks": {
                    "i1": { "@class": "rest", "url": "http://127.0.0.1:%d/leaf" },
                    "i2": { "@class": "rest", "url": "http://127.0.0.1:%d/leaf" }
                  },
                  "body": { "sum": "{= $i1.v + $i2.v }" }
                }
                """.formatted(port, port);
        String template = """
                {
                  "@class": "parallel",
                  "tasks": {
                    "o1": %s,
                    "o2": %s
                  },
                  "body": { "r1": "{$o1.sum}", "r2": "{$o2.sum}" }
                }
                """.formatted(inner, inner);

        long start = System.currentTimeMillis();
        Object r = e.renderJson(template, Map.of());
        long elapsed = System.currentTimeMillis() - start;

        com.alibaba.fastjson2.JSONObject obj = (com.alibaba.fastjson2.JSONObject) r;
        Assertions.assertEquals(2, obj.getIntValue("r1"));
        Assertions.assertEquals(2, obj.getIntValue("r2"));
        // 容忍较大延迟, 但不应死锁 (> 30s 视为死锁)
        Assertions.assertTrue(elapsed < 30000, "nested parallel deadlocked? elapsed=" + elapsed + "ms");
    }

    @Test
    public void testDeeplyNestedParallelSurvivesPoolSaturation() throws Exception {
        // 嵌套深度 2, 宽度足以饱和 pool (16)。
        // 默认 POOL_SIZE=16。outer 4 任务 × inner 4 任务 = 16 个 leaf HTTP 调用,
        // 但任意时刻 outer 占的线程数最多 4, 内部 16 个 leaf 各需 1 线程。
        // 关键: 即便瞬时需求 > pool size, supplyAsync 会排队而非拒绝, 不死锁。
        server.createContext("/deep", exchange -> send(exchange, 200, "{\"v\":1}"));

        RenderEngine e = RenderEngine.build();
        // 构造 inner (4 leaf tasks) 和 outer (4 inner tasks) 模板
        StringBuilder innerTasks = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            if (i > 0) innerTasks.append(",\n");
            innerTasks.append(String.format(
                    "    \"i%d\": { \"@class\": \"rest\", \"url\": \"http://127.0.0.1:%d/deep\" }", i, port));
        }
        String inner = "{\"@class\":\"parallel\",\"tasks\":{" + innerTasks + "}}";

        StringBuilder outerTasks = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            if (i > 0) outerTasks.append(",\n");
            outerTasks.append(String.format("    \"o%d\": %s", i, inner));
        }
        String template = "{\"@class\":\"parallel\",\"tasks\":{" + outerTasks + "}}";

        long start = System.currentTimeMillis();
        Object r = e.renderJson(template, Map.of());
        long elapsed = System.currentTimeMillis() - start;

        com.alibaba.fastjson2.JSONObject obj = (com.alibaba.fastjson2.JSONObject) r;
        Assertions.assertEquals(4, obj.size());
        // 不死锁, 且总耗时合理 (< 30s)
        Assertions.assertTrue(elapsed < 30000,
                "deeply nested parallel too slow or deadlocked: " + elapsed + "ms");
    }

    @Test
    public void testManyTasksQueueWithoutCrash() throws Exception {
        // 100 个任务, pool size 16, 应排队完成而非崩溃
        AtomicInteger calls = new AtomicInteger();
        server.createContext("/n", exchange -> {
            calls.incrementAndGet();
            send(exchange, 200, "{\"n\":" + calls.get() + "}");
        });

        StringBuilder tasks = new StringBuilder();
        for (int i = 0; i < 20; i++) {
            if (i > 0) tasks.append(",\n");
            tasks.append(String.format(
                    "    \"t%d\": { \"@class\": \"rest\", \"url\": \"http://127.0.0.1:%d/n\" }",
                    i, port));
        }

        RenderEngine e = RenderEngine.build();
        Object r = e.renderJson(
                "{\"@class\":\"parallel\",\"tasks\":{" + tasks + "}}",
                Map.of());

        com.alibaba.fastjson2.JSONObject obj = (com.alibaba.fastjson2.JSONObject) r;
        Assertions.assertEquals(20, obj.size());
        Assertions.assertEquals(20, calls.get());
    }

    @Test
    public void testParallelAllTasksFailContinueOnError() throws Exception {
        server.createContext("/fail", exchange -> send(exchange, 500, "x"));

        RenderEngine e = RenderEngine.build();
        Object r = e.renderJson("""
                {
                  "@class": "parallel",
                  "tasks": {
                    "a": { "@class": "rest", "url": "http://127.0.0.1:%d/fail" },
                    "b": { "@class": "rest", "url": "http://127.0.0.1:%d/fail" },
                    "c": { "@class": "rest", "url": "http://127.0.0.1:%d/fail" }
                  },
                  "continueOnError": true,
                  "body": { "a": "{$a}", "b": "{$b}", "c": "{$c}" }
                }
                """.formatted(port, port, port), Map.of());

        com.alibaba.fastjson2.JSONObject obj = (com.alibaba.fastjson2.JSONObject) r;
        Assertions.assertNull(obj.get("a"));
        Assertions.assertNull(obj.get("b"));
        Assertions.assertNull(obj.get("c"));
    }

    @Test
    public void testTryWrappingParallelFailure() throws Exception {
        // parallel 任务全失败, 抛 RenderException → try 捕获 → 走 fallback
        server.createContext("/x", exchange -> send(exchange, 500, "down"));

        RenderEngine e = RenderEngine.build();
        Object r = e.renderJson("""
                {
                  "@class": "try",
                  "select": {
                    "@class": "parallel",
                    "tasks": {
                      "a": { "@class": "rest", "url": "http://127.0.0.1:%d/x" }
                    }
                  },
                  "fallback": { "ok": false, "msg": "{$error.message}" }
                }
                """.formatted(port), Map.of());

        com.alibaba.fastjson2.JSONObject obj = (com.alibaba.fastjson2.JSONObject) r;
        Assertions.assertFalse(obj.getBooleanValue("ok"));
        Assertions.assertNotNull(obj.getString("msg"));
    }

    @Test
    public void testTryWrappingParallelTimeout() throws Exception {
        // parallel 整体超时, 抛 → try fallback
        server.createContext("/slow", exchange -> {
            try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
            send(exchange, 200, "{\"v\":1}");
        });

        RenderEngine e = RenderEngine.build();
        Object r = e.renderJson("""
                {
                  "@class": "try",
                  "select": {
                    "@class": "parallel",
                    "tasks": {
                      "a": { "@class": "rest", "url": "http://127.0.0.1:%d/slow" }
                    },
                    "timeout": 200
                  },
                  "fallback": { "timedOut": true }
                }
                """.formatted(port), Map.of());

        com.alibaba.fastjson2.JSONObject obj = (com.alibaba.fastjson2.JSONObject) r;
        Assertions.assertTrue(obj.getBooleanValue("timedOut"));
    }

    @Test
    public void testParallelTaskReturningScalar() throws Exception {
        // 任务返回字符串/数字, body 直接访问整个值
        RenderEngine e = RenderEngine.build();
        Object r = e.renderJson("""
                {
                  "@class": "parallel",
                  "tasks": {
                    "s": "hello",
                    "n": 42
                  },
                  "body": { "str": "{$s}", "num": "{$n}" }
                }
                """, Map.of());

        com.alibaba.fastjson2.JSONObject obj = (com.alibaba.fastjson2.JSONObject) r;
        Assertions.assertEquals("hello", obj.getString("str"));
        Assertions.assertEquals(42, obj.getIntValue("num"));
    }

    @Test
    public void testParallelWithZeroTasks() throws Exception {
        // 空任务映射, body 仍可渲染
        RenderEngine e = RenderEngine.build();
        Object r = e.renderJson("""
                {
                  "@class": "parallel",
                  "tasks": {},
                  "body": { "ok": true }
                }
                """, Map.of());

        com.alibaba.fastjson2.JSONObject obj = (com.alibaba.fastjson2.JSONObject) r;
        Assertions.assertTrue(obj.getBooleanValue("ok"));
    }

    @Test
    public void testParallelTasksAsNonMapFallsBackToBody() throws Exception {
        // tasks 不是 Map (例如数组), 应优雅退化: 渲染 body 或返回空 JSONObject
        RenderEngine e = RenderEngine.build();
        Object r = e.renderJson("""
                {
                  "@class": "parallel",
                  "tasks": [1, 2, 3],
                  "body": { "noTasks": true }
                }
                """, Map.of());

        com.alibaba.fastjson2.JSONObject obj = (com.alibaba.fastjson2.JSONObject) r;
        Assertions.assertTrue(obj.getBooleanValue("noTasks"));
    }
}
