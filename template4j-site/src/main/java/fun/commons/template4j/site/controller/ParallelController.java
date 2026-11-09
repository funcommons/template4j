package fun.commons.template4j.site.controller;

import com.sun.net.httpserver.HttpServer;
import fun.commons.template4j.core.RenderEngine;
import fun.commons.template4j.site.common.ApiResponse;
import fun.commons.template4j.site.config.TraceIdFilter;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 演示 `@class: parallel` + `@class: rest` 并发编排。
 *
 * <p>启动时在随机端口起 mock server 模拟 3 个微服务,GET /api/demo/parallel 用 parallel 组件并发拉取。
 */
@RestController
@RequestMapping("/api/demo/parallel")
@RequiredArgsConstructor
public class ParallelController {

    private final RenderEngine renderEngine;
    private HttpServer mockServer;
    private int mockPort;

    @PostConstruct
    void startMock() throws IOException {
        mockServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        mockServer.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(8));
        mockServer.createContext("/user", exchange -> {
            sleep(150);
            sendJson(exchange, "{\"id\":\"" + exchange.getRequestURI().getQuery().replace("id=", "")
                    + "\",\"name\":\"alice\",\"vip\":true}");
        });
        mockServer.createContext("/orders", exchange -> {
            sleep(200);
            sendJson(exchange, "[{\"id\":\"o1\",\"amount\":100},{\"id\":\"o2\",\"amount\":200}]");
        });
        mockServer.createContext("/payment", exchange -> {
            sleep(100);
            sendJson(exchange, "{\"status\":\"paid\",\"method\":\"alipay\"}");
        });
        // 故意慢的接口 — 演示 try+fallback 降级
        mockServer.createContext("/slow-log", exchange -> {
            sleep(2000);
            sendJson(exchange, "{\"logged\":true}");
        });
        mockServer.start();
        mockPort = mockServer.getAddress().getPort();
    }

    @PreDestroy
    void stopMock() {
        if (mockServer != null) mockServer.stop(0);
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> demo(@RequestParam(defaultValue = "u-123") String userId,
                                                  HttpServletRequest httpReq) {
        String template = """
                {
                  "@class": "parallel",
                  "tasks": {
                    "user":    { "@class": "rest", "url": "http://127.0.0.1:%d/user?id=%s" },
                    "orders":  { "@class": "rest", "url": "http://127.0.0.1:%d/orders?id=%s" },
                    "payment": { "@class": "rest", "url": "http://127.0.0.1:%d/payment?id=%s" },
                    "log": {
                      "@class": "try",
                      "select": { "@class": "rest", "url": "http://127.0.0.1:%d/slow-log?id=%s", "timeout": 300 },
                      "fallback": { "logged": false, "reason": "skipped (timeout)" }
                    }
                  },
                  "timeout": 1000,
                  "body": {
                    "userId": "{$user.id}",
                    "userName": "{$user.name}",
                    "vip": "{$user.vip}",
                    "orderCount": "{$orders.size()}",
                    "totalAmount": "{= $orders*.amount.sum() }",
                    "payment": "{$payment}",
                    "audit": "{$log}"
                  }
                }
                """.formatted(mockPort, userId, mockPort, userId, mockPort, userId, mockPort, userId);

        long t0 = System.currentTimeMillis();
        Object result = renderEngine.renderJson(template, Map.of());
        long elapsed = System.currentTimeMillis() - t0;

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("result", result);
        out.put("elapsedMs", elapsed);
        out.put("explanation", "并发拉 3 个接口 (~200ms) + 一个故意超时的 log 接口 → try 降级。串行总耗时 ~2.5s, 并行 ~200ms。");
        return ApiResponse.success(out, (String) httpReq.getAttribute(TraceIdFilter.TRACE_ID_KEY));
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }

    private static void sendJson(com.sun.net.httpserver.HttpExchange exchange, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
    }
}
