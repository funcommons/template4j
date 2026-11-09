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
 * 覆盖 RestRenderer 与 ParallelRenderer 100% 覆盖率门槛所需的剩余分支。
 */
public class ComponentRemainingCoverageTest {

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
    }

    private static void send(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
    }

    /** 覆盖 cache hit WITH success template 分支 (L115-116)。 */
    @Test
    public void cacheHitRendersSuccessTemplate() throws Exception {
        server.createContext("/c", exchange -> send(exchange, 200, "{\"v\":1}"));

        RenderEngine e = RenderEngine.build();
        String template = """
                {
                  "@class": "rest",
                  "url": "http://127.0.0.1:%d/c",
                  "cache": { "key": "succ-cache-key", "ttl": 60000 },
                  "success": { "doubled": "{= $responseData.v * 2 }" }
                }
                """.formatted(port);

        Object r1 = e.renderJson(template, Map.of());
        Object r2 = e.renderJson(template, Map.of());

        com.alibaba.fastjson2.JSONObject o1 = (com.alibaba.fastjson2.JSONObject) r1;
        com.alibaba.fastjson2.JSONObject o2 = (com.alibaba.fastjson2.JSONObject) r2;
        // 两次都通过 success 模板, 且都拿到正确数据
        Assertions.assertEquals(2, o1.getIntValue("doubled"));
        Assertions.assertEquals(2, o2.getIntValue("doubled"));
    }

    /** 覆盖 non-standard HTTP method (PATCH) 分支 (L181)。 */
    @Test
    public void patchMethodGoesToDefaultCase() throws Exception {
        server.createContext("/p", exchange -> send(exchange, 200, "{\"ok\":true}"));

        RenderEngine e = RenderEngine.build();
        Object r = e.renderJson("""
                {
                  "@class": "rest",
                  "url": "http://127.0.0.1:%d/p",
                  "method": "PATCH",
                  "data": { "x": 1 }
                }
                """.formatted(port), Map.of());

        com.alibaba.fastjson2.JSONObject obj = (com.alibaba.fastjson2.JSONObject) r;
        Assertions.assertTrue(obj.getBooleanValue("ok"));
    }

    /** 覆盖 retry network failure 分支 (L217-218)。 */
    @Test
    public void retryOnNetworkFailureRetriesUntilSuccess() throws Exception {
        // 第一个端口拒绝连接 (模拟 network failure), 但同一模板没办法跨端口切换。
        // 改用 server stop / start 模拟不可达。
        RenderEngine e = RenderEngine.build();
        // 端口 1 (保留端口) 不可达 → 全部 network failure → retry 用完 → error 分支
        Object r = e.renderJson("""
                {
                  "@class": "rest",
                  "url": "http://127.0.0.1:1/unreachable",
                  "retry": { "count": 2, "baseMs": 5, "retryOn": "network" },
                  "error": { "failed": true, "msg": "{$responseData.message}" }
                }
                """, Map.of());

        com.alibaba.fastjson2.JSONObject obj = (com.alibaba.fastjson2.JSONObject) r;
        Assertions.assertTrue(obj.getBooleanValue("failed"));
        Assertions.assertNotNull(obj.getString("msg"));
    }

    /** 覆盖 ParallelRenderer 空任务映射返回空 JSONObject (L51)。 */
    @Test
    public void parallelEmptyTasksWithoutBodyReturnsEmpty() throws Exception {
        RenderEngine e = RenderEngine.build();
        Object r = e.renderJson("""
                { "@class": "parallel", "tasks": {} }
                """, Map.of());
        com.alibaba.fastjson2.JSONObject obj = (com.alibaba.fastjson2.JSONObject) r;
        Assertions.assertTrue(obj.isEmpty());
    }

    /** 覆盖 ParallelRenderer 非 Map tasks 退化 (L60)。 */
    @Test
    public void parallelNonMapTasksWithoutBodyReturnsEmpty() throws Exception {
        RenderEngine e = RenderEngine.build();
        Object r = e.renderJson("""
                { "@class": "parallel", "tasks": [1,2,3] }
                """, Map.of());
        com.alibaba.fastjson2.JSONObject obj = (com.alibaba.fastjson2.JSONObject) r;
        Assertions.assertTrue(obj.isEmpty());
    }

    /** 覆盖 parseLongField 非 Number 字符串解析 (L312-314)。 */
    @Test
    public void restTimeoutAsString() throws Exception {
        server.createContext("/t", exchange -> send(exchange, 200, "{\"ok\":true}"));
        RenderEngine e = RenderEngine.build();
        Object r = e.renderJson("""
                {
                  "@class": "rest",
                  "url": "http://127.0.0.1:%d/t",
                  "timeout": "5000"
                }
                """.formatted(port), Map.of());
        com.alibaba.fastjson2.JSONObject obj = (com.alibaba.fastjson2.JSONObject) r;
        Assertions.assertTrue(obj.getBooleanValue("ok"));
    }

    /** 覆盖 parseLong Number 直接路径。 */
    @Test
    public void parallelTimeoutFromNumberObject() throws Exception {
        RenderEngine e = RenderEngine.build();
        Object r = e.renderJson("""
                { "@class": "parallel", "tasks": {}, "timeout": 60000 }
                """, Map.of());
        Assertions.assertTrue(r instanceof com.alibaba.fastjson2.JSONObject);
    }

    /** 覆盖 ParallelRenderer continueOnError=true 全部失败的 deadline branch (L93-98)。 */
    @Test
    public void parallelContinueOnErrorWithSlowTasksAndShortTimeout() throws Exception {
        server.createContext("/slow", exchange -> {
            try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
            send(exchange, 200, "{\"v\":1}");
        });
        RenderEngine e = RenderEngine.build();
        Object r = e.renderJson("""
                {
                  "@class": "parallel",
                  "tasks": {
                    "a": { "@class": "rest", "url": "http://127.0.0.1:%d/slow" }
                  },
                  "timeout": 100,
                  "continueOnError": true,
                  "body": { "a": "{$a}" }
                }
                """.formatted(port), Map.of());
        com.alibaba.fastjson2.JSONObject obj = (com.alibaba.fastjson2.JSONObject) r;
        Assertions.assertNull(obj.get("a"));
    }
}
