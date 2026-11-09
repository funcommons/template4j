package fun.commons.template4j.core;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
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
 * 覆盖 RestRenderer 成功 / 4xx / 5xx 响应路径，使用内置 HttpServer mock。
 */
public class RestRendererServerTest {

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
    public void testRestSuccess2xx() throws Exception {
        server.createContext("/ok", exchange -> {
            byte[] body = "{\"hello\":\"world\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.start();

        RenderEngine e = RenderEngine.build();
        Object r = e.renderJson("""
                {
                  "@class": "rest",
                  "url": "http://127.0.0.1:%d/ok"
                }
                """.formatted(port), Map.of());
        // result 是 JSONObject from JSON.parse
        Assertions.assertEquals(((com.alibaba.fastjson2.JSONObject) r).getString("hello"), "world");
    }

    @Test
    public void testRestSuccess2xxWithSuccessBlock() throws Exception {
        server.createContext("/ok2", exchange -> {
            byte[] body = "{\"v\":42}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.start();

        RenderEngine e = RenderEngine.build();
        Object r = e.renderJson("""
                {
                  "@class": "rest",
                  "url": "http://127.0.0.1:%d/ok2",
                  "success": {"got": "{$responseData.v}"}
                }
                """.formatted(port), Map.of());
        Assertions.assertEquals(42, ((com.alibaba.fastjson2.JSONObject) r).getIntValue("got"));
    }

    @Test
    public void testRestClientError4xx() throws Exception {
        server.createContext("/err4", exchange -> {
            byte[] body = "{\"msg\":\"bad request\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(400, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.start();

        RenderEngine e = RenderEngine.build();
        Object r = e.renderJson("""
                {
                  "@class": "rest",
                  "url": "http://127.0.0.1:%d/err4",
                  "error": {"status": "{$responseData.status}", "msg": "{$responseData.message}"}
                }
                """.formatted(port), Map.of());
        Assertions.assertEquals(400, ((com.alibaba.fastjson2.JSONObject) r).getIntValue("status"));
    }

    @Test
    public void testRestServerError5xx() throws Exception {
        server.createContext("/err5", exchange -> {
            byte[] body = "{\"msg\":\"internal\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(500, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.start();

        RenderEngine e = RenderEngine.build();
        Object r = e.renderJson("""
                {
                  "@class": "rest",
                  "url": "http://127.0.0.1:%d/err5",
                  "error": {"status": "{$responseData.status}"}
                }
                """.formatted(port), Map.of());
        Assertions.assertEquals(500, ((com.alibaba.fastjson2.JSONObject) r).getIntValue("status"));
    }

    @Test
    public void testRestSuccessWithSuccessBlockText() throws Exception {
        // 成功 + success 块 + 响应是 JSON — trigger has-success 分支
        server.createContext("/succ_text", exchange -> {
            byte[] body = "\"hello\"".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.start();

        RenderEngine e = RenderEngine.build();
        Object r = e.renderJson("""
                {
                  "@class": "rest",
                  "url": "http://127.0.0.1:%d/succ_text",
                  "dataType": "string",
                  "success": "got: {$responseData}"
                }
                """.formatted(port), Map.of());
        // 当 responseData 是 String "hello"，模板里 {$responseData} 渲染为字面 "hello"（含引号）
        Assertions.assertEquals(r, "got: \"hello\"");
    }

    @Test
    public void testRestSuccessNoDataType() throws Exception {
        // 成功但没指定 dataType — default "auto"
        server.createContext("/succ_auto", exchange -> {
            byte[] body = "{\"x\":1}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.start();

        RenderEngine e = RenderEngine.build();
        Object r = e.renderJson("""
                {
                  "@class": "rest",
                  "url": "http://127.0.0.1:%d/succ_auto"
                }
                """.formatted(port), Map.of());
        Assertions.assertEquals(1, ((com.alibaba.fastjson2.JSONObject) r).getIntValue("x"));
    }
}
