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
 * 覆盖 rest circuitBreaker 子属性: 熔断 / half-open / 重置。
 */
public class CircuitBreakerTest {

    private HttpServer server;
    private int port;

    @BeforeEach
    public void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(8));
        server.start();
        port = server.getAddress().getPort();
    }

    @AfterEach
    public void stopServer() {
        if (server != null) server.stop(0);
        // 清理静态 breaker state, 避免测试间污染
        fun.commons.template4j.core.components.RestRenderer.resetCircuitBreakers();
    }

    private static void send(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
    }

    @Test
    public void circuitOpensAfterThresholdFailures() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        server.createContext("/flaky", exchange -> {
            calls.incrementAndGet();
            send(exchange, 500, "x");
        });

        RenderEngine e = RenderEngine.build();
        String template = """
                {
                  "@class": "rest",
                  "url": "http://127.0.0.1:%d/flaky",
                  "circuitBreaker": { "threshold": 3, "resetSec": 60 }
                }
                """.formatted(port);

        // 前 3 次: 调用, 都失败 (注意: circuitBreaker 不带 retry, 所以每次 render 是 1 次 HTTP)
        for (int i = 0; i < 3; i++) {
            try { e.renderJson(template, Map.of()); } catch (Exception ignored) {}
        }
        Assertions.assertEquals(3, calls.get());

        // 第 4 次: 熔断, 不应再调用 HTTP (走 error 模板 或抛)
        try { e.renderJson(template, Map.of()); } catch (Exception ignored) {}
        Assertions.assertEquals(3, calls.get(), "circuit should be open, no HTTP call");
    }

    @Test
    public void circuitResetsAfterTimeout() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        server.createContext("/resilient", exchange -> {
            int n = calls.incrementAndGet();
            // 第 3 次成功 (half-open 探测通过 → 关闭熔断)
            if (n >= 3) {
                send(exchange, 200, "{\"ok\":true}");
            } else {
                send(exchange, 500, "x");
            }
        });

        RenderEngine e = RenderEngine.build();
        String template = """
                {
                  "@class": "rest",
                  "url": "http://127.0.0.1:%d/resilient",
                  "circuitBreaker": { "threshold": 2, "resetSec": 1 }
                }
                """.formatted(port);

        // 2 次失败 → open
        for (int i = 0; i < 2; i++) {
            try { e.renderJson(template, Map.of()); } catch (Exception ignored) {}
        }
        Assertions.assertEquals(2, calls.get());

        // 等 1.5 秒 → half-open
        Thread.sleep(1500);

        // 探测请求 → 第 3 次成功 → 关闭熔断
        Object r = e.renderJson(template, Map.of());
        Assertions.assertEquals(3, calls.get());
        Assertions.assertTrue(((com.alibaba.fastjson2.JSONObject) r).getBooleanValue("ok"));

        // 后续请求正常调用
        e.renderJson(template, Map.of());
        Assertions.assertEquals(4, calls.get());
    }

    @Test
    public void circuitSuccessResetsFailureCounter() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        server.createContext("/intermittent", exchange -> {
            int n = calls.incrementAndGet();
            // 第 2 次成功, 然后再失败 2 次不应触发熔断 (因计数被重置)
            if (n == 2) {
                send(exchange, 200, "{\"ok\":true}");
            } else {
                send(exchange, 500, "x");
            }
        });

        RenderEngine e = RenderEngine.build();
        String template = """
                {
                  "@class": "rest",
                  "url": "http://127.0.0.1:%d/intermittent",
                  "circuitBreaker": { "threshold": 2, "resetSec": 60 }
                }
                """.formatted(port);

        // 调用 1: 失败 (counter=1)
        try { e.renderJson(template, Map.of()); } catch (Exception ignored) {}
        // 调用 2: 成功 (counter 重置为 0)
        e.renderJson(template, Map.of());
        // 调用 3: 失败 (counter=1)
        try { e.renderJson(template, Map.of()); } catch (Exception ignored) {}
        // 调用 4: 失败 (counter=2 → 熔断)
        try { e.renderJson(template, Map.of()); } catch (Exception ignored) {}
        // 调用 5: 熔断, 不应调用 HTTP
        try { e.renderJson(template, Map.of()); } catch (Exception ignored) {}
        Assertions.assertEquals(4, calls.get(), "5th call should be blocked by open circuit");
    }

    @Test
    public void circuitOpenGoesToErrorTemplate() throws Exception {
        server.createContext("/err", exchange -> send(exchange, 500, "x"));

        RenderEngine e = RenderEngine.build();
        String template = """
                {
                  "@class": "rest",
                  "url": "http://127.0.0.1:%d/err",
                  "circuitBreaker": { "threshold": 2, "resetSec": 60 },
                  "error": { "circuitOpen": "{$responseData.message}" }
                }
                """.formatted(port);

        // 2 次失败 → open
        for (int i = 0; i < 2; i++) {
            try { e.renderJson(template, Map.of()); } catch (Exception ignored) {}
        }
        // 第 3 次: 熔断, 走 error 模板
        Object r = e.renderJson(template, Map.of());
        com.alibaba.fastjson2.JSONObject obj = (com.alibaba.fastjson2.JSONObject) r;
        Assertions.assertNotNull(obj.getString("circuitOpen"));
    }
}
