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
 * Covers RestRenderer remaining lines 160-165: non-2xx response handling.
 * Need: 4xx and 5xx responses WITH and WITHOUT error template.
 */
public class RestRendererErrorPathTest {

    private HttpServer server;
    private int port;
    private AtomicInteger responseStatus = new AtomicInteger(200);
    private String responseBody = "{\"ok\":true}";

    @BeforeEach
    public void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        port = server.getAddress().getPort();
        server.createContext("/", this::handle);
        server.start();
    }

    @AfterEach
    public void tearDown() {
        if (server != null) server.stop(0);
    }

    private void handle(HttpExchange ex) throws IOException {
        byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(responseStatus.get(), bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    private String url(String path) {
        return "http://127.0.0.1:" + port + path;
    }

    // Lines 160-165: 4xx client error path — status.is4xxClientError() → HttpClientErrorException
    @Test
    public void test4xxClientError() throws Exception {
        responseStatus.set(404);
        responseBody = "not found";
        try {
            RenderEngine.build().renderJson("""
                    {
                      "@class": "rest",
                      "url": "%s"
                    }
                    """.formatted(url("/404")), Map.of());
            Assertions.fail("Should throw HttpClientErrorException");
        } catch (Exception e) {
            Assertions.assertTrue(e.getMessage().contains("404") || e.getCause() != null);
        } finally {
            responseStatus.set(200);
        }
    }

    // Lines 160-165: 5xx server error path — HttpServerErrorException
    @Test
    public void test5xxServerError() throws Exception {
        responseStatus.set(500);
        responseBody = "server error";
        try {
            RenderEngine.build().renderJson("""
                    {
                      "@class": "rest",
                      "url": "%s"
                    }
                    """.formatted(url("/500")), Map.of());
            Assertions.fail("Should throw HttpServerErrorException");
        } catch (Exception e) {
            Assertions.assertTrue(e.getMessage().contains("500") || e.getCause() != null);
        } finally {
            responseStatus.set(200);
        }
    }

    // Lines 160-165: 4xx with error template
    @Test
    public void test4xxClientErrorWithTemplate() throws Exception {
        responseStatus.set(403);
        responseBody = "forbidden body";
        try {
            Object r = RenderEngine.build().renderJson("""
                    {
                      "@class": "rest",
                      "url": "%s",
                      "error": {
                        "code": "{$responseData.status}",
                        "body": "{$responseData.message}"
                      }
                    }
                    """.formatted(url("/403")), Map.of());
            com.alibaba.fastjson2.JSONObject jo = (com.alibaba.fastjson2.JSONObject) r;
            Assertions.assertEquals(403, jo.getIntValue("code"));
        } finally {
            responseStatus.set(200);
        }
    }

    // Lines 160-165: 5xx with error template
    @Test
    public void test5xxServerErrorWithTemplate() throws Exception {
        responseStatus.set(503);
        responseBody = "service unavailable";
        try {
            Object r = RenderEngine.build().renderJson("""
                    {
                      "@class": "rest",
                      "url": "%s",
                      "error": {
                        "code": "{$responseData.status}",
                        "body": "{$responseData.message}"
                      }
                    }
                    """.formatted(url("/503")), Map.of());
            com.alibaba.fastjson2.JSONObject jo = (com.alibaba.fastjson2.JSONObject) r;
            Assertions.assertEquals(503, jo.getIntValue("code"));
        } finally {
            responseStatus.set(200);
        }
    }

    // Lines 160-165: non-standard HTTP status code (e.g. 302 redirect — not 2xx, not 4xx, not 5xx)
    @Test
    public void testNonStandardStatusCode() throws Exception {
        responseStatus.set(302);
        responseBody = "redirect";
        try {
            RenderEngine.build().renderJson("""
                    {
                      "@class": "rest",
                      "url": "%s"
                    }
                    """.formatted(url("/302")), Map.of());
            Assertions.fail("Should throw");
        } catch (Exception e) {
            // 302 falls into the else → HttpServerErrorException
        } finally {
            responseStatus.set(200);
        }
    }
}
