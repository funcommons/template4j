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
 * 覆盖 TryRenderer 剩余分支: onError 自身失败、rethrow + fallback、未知异常类型、parseBooleanField null。
 */
public class TryRendererRemainingCoverageTest {

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

    /** onError 自身失败 + rethrow=true → 抛 RenderException("onError handler failed")。 */
    @Test
    public void onErrorFailsWithRethrow() throws Exception {
        server.createContext("/fail", exchange -> send(exchange, 500, "x"));

        RenderEngine e = RenderEngine.build();
        // onError 用未知组件 → 渲染时抛
        Assertions.assertThrows(Exception.class, () -> e.renderJson("""
                {
                  "@class": "try",
                  "select": { "@class": "rest", "url": "http://127.0.0.1:%d/fail" },
                  "onError": { "@class": "unknown-component-x" },
                  "rethrow": true,
                  "fallback": { "ignored": true }
                }
                """.formatted(port), Map.of()));
    }

    /** onError 自身失败 + 仅 onError (无 fallback) → 抛 RenderException。 */
    @Test
    public void onErrorFailsWithoutFallback() throws Exception {
        server.createContext("/fail", exchange -> send(exchange, 500, "x"));

        RenderEngine e = RenderEngine.build();
        Assertions.assertThrows(Exception.class, () -> e.renderJson("""
                {
                  "@class": "try",
                  "select": { "@class": "rest", "url": "http://127.0.0.1:%d/fail" },
                  "onError": { "@class": "unknown-component-y" }
                }
                """.formatted(port), Map.of()));
    }

    /** onError 自身失败但有 fallback → 容忍 onError 失败, 返回 fallback (不抛)。 */
    @Test
    public void onErrorFailsButFallbackRescues() throws Exception {
        server.createContext("/fail", exchange -> send(exchange, 500, "x"));

        RenderEngine e = RenderEngine.build();
        Object r = e.renderJson("""
                {
                  "@class": "try",
                  "select": { "@class": "rest", "url": "http://127.0.0.1:%d/fail" },
                  "onError": { "@class": "unknown-component-z" },
                  "fallback": { "rescued": true }
                }
                """.formatted(port), Map.of());

        com.alibaba.fastjson2.JSONObject obj = (com.alibaba.fastjson2.JSONObject) r;
        Assertions.assertTrue(obj.getBooleanValue("rescued"));
    }

    /** select 抛 非-RenderException (HttpException), 不带 fallback/onError → 抛 wrapped RenderException。 */
    @Test
    public void selectHttpExceptionWithoutFallback() throws Exception {
        server.createContext("/fail", exchange -> send(exchange, 500, "x"));

        RenderEngine e = RenderEngine.build();
        // 抛 HttpException (RuntimeException), TryRenderer 捕获后 unwrap → 不是 RenderException → 包装
        Assertions.assertThrows(Exception.class, () -> e.renderJson("""
                {
                  "@class": "try",
                  "select": { "@class": "rest", "url": "http://127.0.0.1:%d/fail" }
                }
                """.formatted(port), Map.of()));
    }

    /** rethrow 字段值为非布尔字符串 → parseBooleanField BooleanUtils 返回 null → fallback。 */
    @Test
    public void rethrowAsNonBooleanString() throws Exception {
        server.createContext("/fail", exchange -> send(exchange, 500, "x"));

        RenderEngine e = RenderEngine.build();
        // rethrow: "yes" → BooleanUtils.toBooleanObject 返回 Boolean.TRUE
        // 但 "garbage" → null → fallback to default false
        Object r = e.renderJson("""
                {
                  "@class": "try",
                  "select": { "@class": "rest", "url": "http://127.0.0.1:%d/fail" },
                  "rethrow": "garbage",
                  "fallback": { "ok": true }
                }
                """.formatted(port), Map.of());

        com.alibaba.fastjson2.JSONObject obj = (com.alibaba.fastjson2.JSONObject) r;
        Assertions.assertTrue(obj.getBooleanValue("ok"));
    }
}
