package fun.commons.template4j.core;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import fun.commons.template4j.core.components.RestRenderer;
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
import java.util.concurrent.atomic.AtomicReference;

/**
 * 覆盖 RestRenderer 各边缘分支: circuitBreaker 全状态机、parseRetry/Cache/Breaker 各种 fallback、
 * applyAuth 各种 type、render 的 GET-with-existing-query / POST-with-string / x-www-form-urlencoded 等。
 */
public class RestRendererCoverageTest {

    private HttpServer server;
    private int port;

    @BeforeEach
    public void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        port = server.getAddress().getPort();
        RestRenderer.resetCircuitBreakers();
    }

    @AfterEach
    public void stopServer() {
        if (server != null) server.stop(0);
    }

    private static void send(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    // ---- circuit breaker 全状态 ----

    @Test
    public void circuitBreakerOpensAfterThreshold() throws Exception {
        // 5xx 失败 threshold=2 → 触发 OPEN, 后续请求直接走 error 分支不调 HTTP
        AtomicInteger calls = new AtomicInteger();
        server.createContext("/fail", exchange -> {
            calls.incrementAndGet();
            send(exchange, 500, "fail");
        });
        server.start();

        RenderEngine e = RenderEngine.build();
        String tpl = """
                {
                  "@class": "rest",
                  "url": "http://127.0.0.1:%d/fail",
                  "circuitBreaker": { "threshold": 2, "resetSec": 60 },
                  "error": { "blocked": true }
                }
                """.formatted(port);

        // 第一次失败 (记录 1 次)
        Object r1 = e.renderJson(tpl, Map.of());
        // 第二次失败 (达到 threshold, breaker OPEN)
        Object r2 = e.renderJson(tpl, Map.of());
        // 第三次 - breaker OPEN, 不调 HTTP 直接走 error
        Object r3 = e.renderJson(tpl, Map.of());

        Assertions.assertEquals(2, calls.get(), "应只在 OPEN 前调用 2 次");
        Assertions.assertTrue(((com.alibaba.fastjson2.JSONObject) r3).getBooleanValue("blocked"));
    }

    @Test
    public void circuitBreakerOpenThrowsHttpExceptionWithoutErrorBranch() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        server.createContext("/hard-fail", exchange -> {
            calls.incrementAndGet();
            send(exchange, 500, "fail");
        });
        server.start();

        RenderEngine e = RenderEngine.build();
        String tpl = """
                {
                  "@class": "rest",
                  "url": "http://127.0.0.1:%d/hard-fail",
                  "circuitBreaker": { "threshold": 1, "resetSec": 60 }
                }
                """.formatted(port);

        // 第一次: 触发 OPEN (抛 RenderException 包 HttpException)
        Assertions.assertThrows(Exception.class,
                () -> e.renderJson(tpl, Map.of()));
        // 第二次: breaker 已 OPEN, 无 error 分支 → 直接抛 "circuit breaker open"
        Assertions.assertThrows(Exception.class,
                () -> e.renderJson(tpl, Map.of()));
    }

    @Test
    public void circuitBreakerHalfOpenAfterReset() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        server.createContext("/recover", exchange -> {
            int n = calls.incrementAndGet();
            if (n == 1) {
                send(exchange, 500, "fail");
            } else {
                send(exchange, 200, "{\"ok\":true}");
            }
        });
        server.start();

        RenderEngine e = RenderEngine.build();
        String tpl = """
                {
                  "@class": "rest",
                  "url": "http://127.0.0.1:%d/recover",
                  "circuitBreaker": { "threshold": 1, "resetSec": 0 }
                }
                """.formatted(port);

        // 第一次失败 → OPEN
        Assertions.assertThrows(Exception.class,
                () -> e.renderJson(tpl, Map.of()));
        // resetSec=0 → 立刻 HALF_OPEN, 第二次成功 → CLOSED
        Object r2 = e.renderJson(tpl, Map.of());
        Assertions.assertTrue(((com.alibaba.fastjson2.JSONObject) r2).getBooleanValue("ok"));
    }

    // ---- parseRetry 边缘 ----

    @Test
    public void parseRetryCountZeroReturnsNull() throws Exception {
        // count <= 0 → parseRetry 返回 null, 不重试
        AtomicInteger calls = new AtomicInteger();
        server.createContext("/once", exchange -> {
            calls.incrementAndGet();
            send(exchange, 200, "{\"ok\":true}");
        });
        server.start();

        RenderEngine e = RenderEngine.build();
        Object r = e.renderJson("""
                {
                  "@class": "rest",
                  "url": "http://127.0.0.1:%d/once",
                  "retry": { "count": 0, "baseMs": 5 }
                }
                """.formatted(port), Map.of());
        Assertions.assertTrue(((com.alibaba.fastjson2.JSONObject) r).getBooleanValue("ok"));
        Assertions.assertEquals(1, calls.get());
    }

    @Test
    public void parseRetryNotMapReturnsNull() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        server.createContext("/once2", exchange -> {
            calls.incrementAndGet();
            send(exchange, 200, "{\"ok\":true}");
        });
        server.start();

        RenderEngine e = RenderEngine.build();
        Object r = e.renderJson("""
                {
                  "@class": "rest",
                  "url": "http://127.0.0.1:%d/once2",
                  "retry": "not-a-map"
                }
                """.formatted(port), Map.of());
        Assertions.assertTrue(((com.alibaba.fastjson2.JSONObject) r).getBooleanValue("ok"));
    }

    @Test
    public void parseRetryExponentialBackoff() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        server.createContext("/exp", exchange -> {
            int n = calls.incrementAndGet();
            if (n < 2) {
                send(exchange, 500, "fail");
            } else {
                send(exchange, 200, "{\"n\":" + n + "}");
            }
        });
        server.start();

        RenderEngine e = RenderEngine.build();
        Object r = e.renderJson("""
                {
                  "@class": "rest",
                  "url": "http://127.0.0.1:%d/exp",
                  "retry": { "count": 2, "backoff": "exponential", "baseMs": 10 }
                }
                """.formatted(port), Map.of());
        Assertions.assertEquals(2, ((com.alibaba.fastjson2.JSONObject) r).getIntValue("n"));
    }

    @Test
    public void parseRetryLinearBackoff() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        server.createContext("/lin", exchange -> {
            int n = calls.incrementAndGet();
            if (n < 2) {
                send(exchange, 500, "fail");
            } else {
                send(exchange, 200, "{\"n\":" + n + "}");
            }
        });
        server.start();

        RenderEngine e = RenderEngine.build();
        Object r = e.renderJson("""
                {
                  "@class": "rest",
                  "url": "http://127.0.0.1:%d/lin",
                  "retry": { "count": 2, "backoff": "linear", "baseMs": 10 }
                }
                """.formatted(port), Map.of());
        Assertions.assertEquals(2, ((com.alibaba.fastjson2.JSONObject) r).getIntValue("n"));
    }

    // ---- parseCache 边缘 ----

    @Test
    public void cacheWithoutKeyIsIgnored() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        server.createContext("/nokey", exchange -> {
            int n = calls.incrementAndGet();
            send(exchange, 200, "{\"n\":" + n + "}");
        });
        server.start();

        RenderEngine e = RenderEngine.build();
        String tpl = """
                {
                  "@class": "rest",
                  "url": "http://127.0.0.1:%d/nokey",
                  "cache": { "ttl": 60000 }
                }
                """.formatted(port);

        e.renderJson(tpl, Map.of());
        e.renderJson(tpl, Map.of());
        // 没 key → 不缓存
        Assertions.assertEquals(2, calls.get());
    }

    @Test
    public void cacheWithoutTtlIsIgnored() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        server.createContext("/nottl", exchange -> {
            int n = calls.incrementAndGet();
            send(exchange, 200, "{\"n\":" + n + "}");
        });
        server.start();

        RenderEngine e = RenderEngine.build();
        String tpl = """
                {
                  "@class": "rest",
                  "url": "http://127.0.0.1:%d/nottl",
                  "cache": { "key": "x" }
                }
                """.formatted(port);

        e.renderJson(tpl, Map.of());
        e.renderJson(tpl, Map.of());
        Assertions.assertEquals(2, calls.get());
    }

    @Test
    public void cacheNotMapIsIgnored() throws Exception {
        server.createContext("/string-cache", exchange -> send(exchange, 200, "{\"ok\":true}"));
        server.start();

        RenderEngine e = RenderEngine.build();
        Object r = e.renderJson("""
                {
                  "@class": "rest",
                  "url": "http://127.0.0.1:%d/string-cache",
                  "cache": "not-a-map"
                }
                """.formatted(port), Map.of());
        Assertions.assertTrue(((com.alibaba.fastjson2.JSONObject) r).getBooleanValue("ok"));
    }

    // ---- parseCircuitBreaker 边缘 ----

    @Test
    public void circuitBreakerZeroThresholdIsIgnored() throws Exception {
        server.createContext("/zero-cb", exchange -> send(exchange, 200, "{\"ok\":true}"));
        server.start();

        RenderEngine e = RenderEngine.build();
        Object r = e.renderJson("""
                {
                  "@class": "rest",
                  "url": "http://127.0.0.1:%d/zero-cb",
                  "circuitBreaker": { "threshold": 0, "resetSec": 60 }
                }
                """.formatted(port), Map.of());
        Assertions.assertTrue(((com.alibaba.fastjson2.JSONObject) r).getBooleanValue("ok"));
    }

    @Test
    public void circuitBreakerZeroResetIsIgnored() throws Exception {
        server.createContext("/zero-reset", exchange -> send(exchange, 200, "{\"ok\":true}"));
        server.start();

        RenderEngine e = RenderEngine.build();
        Object r = e.renderJson("""
                {
                  "@class": "rest",
                  "url": "http://127.0.0.1:%d/zero-reset",
                  "circuitBreaker": { "threshold": 5, "resetSec": 0 }
                }
                """.formatted(port), Map.of());
        Assertions.assertTrue(((com.alibaba.fastjson2.JSONObject) r).getBooleanValue("ok"));
    }

    @Test
    public void circuitBreakerNotMapIsIgnored() throws Exception {
        server.createContext("/not-map", exchange -> send(exchange, 200, "{\"ok\":true}"));
        server.start();

        RenderEngine e = RenderEngine.build();
        Object r = e.renderJson("""
                {
                  "@class": "rest",
                  "url": "http://127.0.0.1:%d/not-map",
                  "circuitBreaker": "raw-string"
                }
                """.formatted(port), Map.of());
        Assertions.assertTrue(((com.alibaba.fastjson2.JSONObject) r).getBooleanValue("ok"));
    }

    // ---- applyAuth 边缘 ----

    @Test
    public void authUnknownTypeIsIgnored() throws Exception {
        AtomicReference<String> h = new AtomicReference<>();
        server.createContext("/unknown-auth", exchange -> {
            h.set(exchange.getRequestHeaders().getFirst("Authorization"));
            send(exchange, 200, "{\"ok\":true}");
        });
        server.start();

        RenderEngine e = RenderEngine.build();
        e.renderJson("""
                {
                  "@class": "rest",
                  "url": "http://127.0.0.1:%d/unknown-auth",
                  "auth": { "type": "digest", "token": "x" }
                }
                """.formatted(port), Map.of());
        Assertions.assertNull(h.get());
    }

    @Test
    public void authBearerMissingTokenDoesNotInject() throws Exception {
        AtomicReference<String> h = new AtomicReference<>();
        server.createContext("/no-token", exchange -> {
            h.set(exchange.getRequestHeaders().getFirst("Authorization"));
            send(exchange, 200, "{\"ok\":true}");
        });
        server.start();

        RenderEngine e = RenderEngine.build();
        e.renderJson("""
                {
                  "@class": "rest",
                  "url": "http://127.0.0.1:%d/no-token",
                  "auth": { "type": "bearer" }
                }
                """.formatted(port), Map.of());
        Assertions.assertNull(h.get());
    }

    @Test
    public void authBasicMissingUserDoesNotInject() throws Exception {
        AtomicReference<String> h = new AtomicReference<>();
        server.createContext("/basic-no-user", exchange -> {
            h.set(exchange.getRequestHeaders().getFirst("Authorization"));
            send(exchange, 200, "{\"ok\":true}");
        });
        server.start();

        RenderEngine e = RenderEngine.build();
        e.renderJson("""
                {
                  "@class": "rest",
                  "url": "http://127.0.0.1:%d/basic-no-user",
                  "auth": { "type": "basic", "pass": "secret" }
                }
                """.formatted(port), Map.of());
        Assertions.assertNull(h.get());
    }

    @Test
    public void authNotMapIsIgnored() throws Exception {
        server.createContext("/auth-not-map", exchange -> send(exchange, 200, "{\"ok\":true}"));
        server.start();

        RenderEngine e = RenderEngine.build();
        Object r = e.renderJson("""
                {
                  "@class": "rest",
                  "url": "http://127.0.0.1:%d/auth-not-map",
                  "auth": "not-a-map"
                }
                """.formatted(port), Map.of());
        Assertions.assertTrue(((com.alibaba.fastjson2.JSONObject) r).getBooleanValue("ok"));
    }

    // ---- render 边缘 ----

    @Test
    public void getWithDataAppendsQueryAndPreservesExistingQuery() throws Exception {
        AtomicReference<String> path = new AtomicReference<>();
        server.createContext("/q", exchange -> {
            path.set(exchange.getRequestURI().getRawQuery());
            send(exchange, 200, "{\"ok\":true}");
        });
        server.start();

        RenderEngine e = RenderEngine.build();
        // URL 已经有 ?, data 中再添加参数 → 用 & 拼接
        e.renderJson("""
                {
                  "@class": "rest",
                  "method": "GET",
                  "url": "http://127.0.0.1:%d/q?existing=1",
                  "data": { "extra": "yes" }
                }
                """.formatted(port), Map.of());
        Assertions.assertNotNull(path.get());
        Assertions.assertTrue(path.get().contains("existing=1"));
        Assertions.assertTrue(path.get().contains("extra=yes"));
    }

    @Test
    public void postWithStringBody() throws Exception {
        AtomicReference<String> body = new AtomicReference<>();
        server.createContext("/post-str", exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            send(exchange, 200, "{\"ok\":true}");
        });
        server.start();

        RenderEngine e = RenderEngine.build();
        e.renderJson("""
                {
                  "@class": "rest",
                  "method": "POST",
                  "url": "http://127.0.0.1:%d/post-str",
                  "data": "raw-string-body"
                }
                """.formatted(port), Map.of());
        Assertions.assertEquals("raw-string-body", body.get());
    }

    @Test
    public void postWithFormUrlencodedUsesQueryString() throws Exception {
        AtomicReference<String> body = new AtomicReference<>();
        AtomicReference<String> ct = new AtomicReference<>();
        server.createContext("/post-form", exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            ct.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            send(exchange, 200, "{\"ok\":true}");
        });
        server.start();

        RenderEngine e = RenderEngine.build();
        e.renderJson("""
                {
                  "@class": "rest",
                  "method": "POST",
                  "url": "http://127.0.0.1:%d/post-form",
                  "headers": { "Content-Type": "application/x-www-form-urlencoded" },
                  "data": { "a": "1", "b": "2" }
                }
                """.formatted(port), Map.of());
        Assertions.assertNotNull(body.get());
        Assertions.assertTrue(body.get().contains("a=1"));
        Assertions.assertTrue(body.get().contains("b=2"));
        Assertions.assertTrue(ct.get().contains("application/x-www-form-urlencoded"));
    }

    @Test
    public void getWithDataNullProducesNoQueryString() throws Exception {
        AtomicReference<String> path = new AtomicReference<>();
        server.createContext("/nq", exchange -> {
            path.set(exchange.getRequestURI().getRawQuery());
            send(exchange, 200, "{\"ok\":true}");
        });
        server.start();

        RenderEngine e = RenderEngine.build();
        e.renderJson("""
                {
                  "@class": "rest",
                  "method": "GET",
                  "url": "http://127.0.0.1:%d/nq",
                  "data": null
                }
                """.formatted(port), Map.of());
        Assertions.assertNull(path.get());
    }

    @Test
    public void putMethodRequest() throws Exception {
        AtomicReference<String> method = new AtomicReference<>();
        server.createContext("/put", exchange -> {
            method.set(exchange.getRequestMethod());
            send(exchange, 200, "{\"ok\":true}");
        });
        server.start();

        RenderEngine e = RenderEngine.build();
        e.renderJson("""
                {
                  "@class": "rest",
                  "method": "PUT",
                  "url": "http://127.0.0.1:%d/put",
                  "data": {"x":1}
                }
                """.formatted(port), Map.of());
        Assertions.assertEquals("PUT", method.get());
    }

    @Test
    public void deleteMethodRequest() throws Exception {
        AtomicReference<String> method = new AtomicReference<>();
        server.createContext("/del", exchange -> {
            method.set(exchange.getRequestMethod());
            send(exchange, 200, "{\"ok\":true}");
        });
        server.start();

        RenderEngine e = RenderEngine.build();
        e.renderJson("""
                {
                  "@class": "rest",
                  "method": "DELETE",
                  "url": "http://127.0.0.1:%d/del"
                }
                """.formatted(port), Map.of());
        Assertions.assertEquals("DELETE", method.get());
    }

    @Test
    public void customMethodRequest() throws Exception {
        AtomicReference<String> method = new AtomicReference<>();
        server.createContext("/patch", exchange -> {
            method.set(exchange.getRequestMethod());
            send(exchange, 200, "{\"ok\":true}");
        });
        server.start();

        RenderEngine e = RenderEngine.build();
        e.renderJson("""
                {
                  "@class": "rest",
                  "method": "PATCH",
                  "url": "http://127.0.0.1:%d/patch"
                }
                """.formatted(port), Map.of());
        Assertions.assertEquals("PATCH", method.get());
    }

    @Test
    public void errorBranchOnHttpError() throws Exception {
        server.createContext("/http400", exchange -> send(exchange, 400, "bad request"));
        server.start();

        RenderEngine e = RenderEngine.build();
        Object r = e.renderJson("""
                {
                  "@class": "rest",
                  "url": "http://127.0.0.1:%d/http400",
                  "error": { "kind": "http-error", "status": "{$responseData.status}" }
                }
                """.formatted(port), Map.of());
        com.alibaba.fastjson2.JSONObject obj = (com.alibaba.fastjson2.JSONObject) r;
        Assertions.assertEquals("http-error", obj.getString("kind"));
        Assertions.assertEquals(400, obj.getIntValue("status"));
    }

    @Test
    public void httpErrorThrowsWithoutErrorBranch() throws Exception {
        server.createContext("/http500", exchange -> send(exchange, 500, "fail"));
        server.start();

        RenderEngine e = RenderEngine.build();
        Assertions.assertThrows(Exception.class,
                () -> e.renderJson("""
                {
                  "@class": "rest",
                  "url": "http://127.0.0.1:%d/http500"
                }
                """.formatted(port), Map.of()));
    }

    @Test
    public void successBranchOverridesResult() throws Exception {
        server.createContext("/ok-branch", exchange -> send(exchange, 200, "{\"v\":99}"));
        server.start();

        RenderEngine e = RenderEngine.build();
        Object r = e.renderJson("""
                {
                  "@class": "rest",
                  "url": "http://127.0.0.1:%d/ok-branch",
                  "success": { "wrapped": true, "orig": "{$responseData.v}" }
                }
                """.formatted(port), Map.of());
        com.alibaba.fastjson2.JSONObject obj = (com.alibaba.fastjson2.JSONObject) r;
        Assertions.assertTrue(obj.getBooleanValue("wrapped"));
        Assertions.assertEquals(99, obj.getIntValue("orig"));
    }

    @Test
    public void successBranchOnCacheHit() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        server.createContext("/cached-success", exchange -> {
            calls.incrementAndGet();
            send(exchange, 200, "{\"v\":7}");
        });
        server.start();

        RenderEngine e = RenderEngine.build();
        String tpl = """
                {
                  "@class": "rest",
                  "url": "http://127.0.0.1:%d/cached-success",
                  "cache": { "key": "cs", "ttl": 60000 },
                  "success": { "wrapped": true, "v": "{$responseData.v}" }
                }
                """.formatted(port);

        e.renderJson(tpl, Map.of());
        Object r2 = e.renderJson(tpl, Map.of());
        Assertions.assertEquals(1, calls.get());
        Assertions.assertTrue(((com.alibaba.fastjson2.JSONObject) r2).getBooleanValue("wrapped"));
    }

    @Test
    public void postWithoutDataSendsEmptyBody() throws Exception {
        AtomicReference<String> body = new AtomicReference<>();
        server.createContext("/empty", exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            send(exchange, 200, "{\"ok\":true}");
        });
        server.start();

        RenderEngine e = RenderEngine.build();
        e.renderJson("""
                {
                  "@class": "rest",
                  "method": "POST",
                  "url": "http://127.0.0.1:%d/empty"
                }
                """.formatted(port), Map.of());
        Assertions.assertEquals("", body.get());
    }

    @Test
    public void headersMapWithNullValuesSkipped() throws Exception {
        AtomicReference<Integer> count = new AtomicReference<>();
        server.createContext("/h", exchange -> {
            count.set(exchange.getRequestHeaders().size());
            send(exchange, 200, "{\"ok\":true}");
        });
        server.start();

        RenderEngine e = RenderEngine.build();
        e.renderJson("""
                {
                  "@class": "rest",
                  "url": "http://127.0.0.1:%d/h",
                  "headers": { "X-Custom": "value" }
                }
                """.formatted(port), Map.of());
        Assertions.assertNotNull(count.get());
        Assertions.assertTrue(count.get() >= 1);
    }

    // ---- parseCircuitBreaker edge ----
    // (BreakerPolicy / recordBreakerFailure 已重构为 resilience4j CircuitBreakerConfig / Registry,
    //  反射目标随之更新为私有 parseCircuitBreaker(RenderContext) 的各分支)

    @Test
    public void parseCircuitBreakerEdgeViaReflection() throws Exception {
        java.lang.reflect.Method m = RestRenderer.class.getDeclaredMethod(
                "parseCircuitBreaker", RenderComponent.RenderContext.class);
        m.setAccessible(true);

        // 有效配置: threshold=5 / resetSec=60 (等价旧 BreakerPolicy(5, 60000L))
        // → 返回 resilience4j CircuitBreakerConfig, slidingWindowSize=5, OPEN 等待 60s
        RenderComponent.RenderContext ok = new RenderComponent.RenderContext();
        ok.setMap(new java.util.HashMap<>(Map.of("circuitBreaker", Map.of("threshold", 5, "resetSec", 60))));
        ok.setSelf(new SelfObject((RenderEngine.Renderer) RenderEngine.build(), new java.util.HashMap<>()));
        Object cfg = m.invoke(null, ok);
        Assertions.assertNotNull(cfg);
        io.github.resilience4j.circuitbreaker.CircuitBreakerConfig r4j =
                (io.github.resilience4j.circuitbreaker.CircuitBreakerConfig) cfg;
        Assertions.assertEquals(5, r4j.getSlidingWindowSize());
        Assertions.assertEquals(5, r4j.getMinimumNumberOfCalls());
        Assertions.assertEquals(1, r4j.getPermittedNumberOfCallsInHalfOpenState());
        Assertions.assertEquals(100f, r4j.getFailureRateThreshold());
        Assertions.assertEquals(60000L, r4j.getWaitIntervalFunctionInOpenState().apply(1));

        // threshold<=0 → 禁用分支 → null
        RenderComponent.RenderContext zero = new RenderComponent.RenderContext();
        zero.setMap(new java.util.HashMap<>(Map.of("circuitBreaker", Map.of("threshold", 0, "resetSec", 60))));
        zero.setSelf(new SelfObject((RenderEngine.Renderer) RenderEngine.build(), new java.util.HashMap<>()));
        Assertions.assertNull(m.invoke(null, zero));

        // renderField 抛异常 → 防御分支 → null (等价旧 recordBreakerFailure st==null 的防御语义)
        RenderEngine.Renderer boomRenderer = new RenderEngine.Renderer() {
            @Override
            public Object convertSelf(Object template, SelfObject self) {
                throw new RenderException("", "circuitBreaker", "boom");
            }

            @Override
            public TemplateLoader getTemplateLoader() {
                return null;
            }

            @Override
            public void trace(RenderEngine.TraceEvent event) { }
        };
        RenderComponent.RenderContext boom = new RenderComponent.RenderContext();
        boom.setMap(new java.util.HashMap<>(Map.of("circuitBreaker", "anything")));
        boom.setSelf(new SelfObject(boomRenderer, new java.util.HashMap<>()));
        Assertions.assertNull(m.invoke(null, boom));
    }
}