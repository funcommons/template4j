package fun.commons.template4j.core.components;

import com.sun.net.httpserver.HttpServer;
import fun.commons.template4j.core.RenderEngine;
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
 * 覆盖 ParallelRenderer 各边缘分支: 空 tasks map / 非 Map tasks / 单任务超时 / continueOnError / unwrap / parseLong / parseBooleanField。
 */
public class ParallelRendererExtraCoverageTest {

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

    private static void send(com.sun.net.httpserver.HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
    }

    @Test
    public void emptyTasksMapWithBody() throws Exception {
        // tasks 是空 Map + body → 返回 body
        RenderEngine e = RenderEngine.build();
        Object r = e.renderJson("""
                {
                  "@class": "parallel",
                  "tasks": {},
                  "body": { "msg": "empty" }
                }
                """, Map.of());
        Assertions.assertNotNull(r);
        Assertions.assertTrue(r.toString().contains("empty"));
    }

    @Test
    public void emptyTasksMapWithoutBody() throws Exception {
        // tasks 空 Map + 没 body → 返回空 JSONObject
        RenderEngine e = RenderEngine.build();
        Object r = e.renderJson("""
                {
                  "@class": "parallel",
                  "tasks": {}
                }
                """, Map.of());
        Assertions.assertNotNull(r);
    }

    @Test
    public void tasksNotMapWithBody() throws Exception {
        // tasks 不是 Map (e.g. 字符串) + body → 走 body
        RenderEngine e = RenderEngine.build();
        Object r = e.renderJson("""
                {
                  "@class": "parallel",
                  "tasks": "not-a-map",
                  "body": { "fallback": true }
                }
                """, Map.of());
        Assertions.assertNotNull(r);
        Assertions.assertTrue(r.toString().contains("fallback"));
    }

    @Test
    public void tasksNotMapWithoutBody() throws Exception {
        RenderEngine e = RenderEngine.build();
        Object r = e.renderJson("""
                {
                  "@class": "parallel",
                  "tasks": "not-a-map"
                }
                """, Map.of());
        Assertions.assertNotNull(r);
    }

    @Test
    public void continueOnErrorWithFailingTask() throws Exception {
        server.createContext("/fail", exchange -> send(exchange, 500, "x"));
        server.createContext("/ok", exchange -> send(exchange, 200, "{\"v\":1}"));
        server.start();

        RenderEngine e = RenderEngine.build();
        Object r = e.renderJson("""
                {
                  "@class": "parallel",
                  "tasks": {
                    "ok":    { "@class": "rest", "url": "http://127.0.0.1:%d/ok" },
                    "fail":  { "@class": "rest", "url": "http://127.0.0.1:%d/fail" }
                  },
                  "continueOnError": true
                }
                """.formatted(port, port), Map.of());
        // continueOnError=true → 部分失败仍返回结果 (失败的 task 是 null)
        Assertions.assertNotNull(r);
        Assertions.assertTrue(r.toString().contains("\"v\":1"));
    }

    @Test
    public void timeoutWithErrorBranch() throws Exception {
        server.createContext("/slow", exchange -> {
            try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
            send(exchange, 200, "{\"slow\":true}");
        });
        server.start();

        RenderEngine e = RenderEngine.build();
        Object r = e.renderJson("""
                {
                  "@class": "parallel",
                  "tasks": {
                    "slow": { "@class": "rest", "url": "http://127.0.0.1:%d/slow", "timeout": 100 }
                  },
                  "timeout": 200,
                  "error": { "timedOut": true, "task": "{$error.task}" }
                }
                """.formatted(port), Map.of());
        com.alibaba.fastjson2.JSONObject obj = (com.alibaba.fastjson2.JSONObject) r;
        Assertions.assertTrue(obj.getBooleanValue("timedOut"));
    }

    @Test
    public void unwrapWithCompletionException() throws Exception {
        // 间接验证 unwrap: 让 task 抛 CompletionException 包 HttpException
        server.createContext("/fail", exchange -> send(exchange, 500, "boom"));
        server.start();

        RenderEngine e = RenderEngine.build();
        // 让 fail 任务被 catch 时 cause 是 CompletionException, unwrap 一次再取 cause
        Assertions.assertThrows(Exception.class, () -> e.renderJson("""
                {
                  "@class": "parallel",
                  "tasks": {
                    "fail": { "@class": "rest", "url": "http://127.0.0.1:%d/fail" }
                  }
                }
                """.formatted(port), Map.of()));
    }

    @Test
    public void parseLongHandlesNullValue() throws Exception {
        // timeout=null → parseLong 走 fallback 分支
        RenderEngine e = RenderEngine.build();
        Object r = e.renderJson("""
                {
                  "@class": "parallel",
                  "tasks": { "x": { "v": 1 } },
                  "timeout": null
                }
                """, Map.of());
        Assertions.assertNotNull(r);
    }

    @Test
    public void parseLongHandlesInvalidString() throws Exception {
        // timeout 字符串无法转数字 → parseLong fallback
        RenderEngine e = RenderEngine.build();
        Object r = e.renderJson("""
                {
                  "@class": "parallel",
                  "tasks": { "x": { "v": 1 } },
                  "timeout": "not-a-number"
                }
                """, Map.of());
        Assertions.assertNotNull(r);
    }

    @Test
    public void parseBooleanFieldVariousValues() throws Exception {
        // continueOnError 各种值
        RenderEngine e = RenderEngine.build();
        for (String val : new String[]{"true", "false", "yes", "garbage"}) {
            Object r = e.renderJson(String.format("""
                    {
                      "@class": "parallel",
                      "tasks": { "x": { "v": 1 } },
                      "continueOnError": "%s"
                    }
                    """, val), Map.of());
            Assertions.assertNotNull(r);
        }
    }
}