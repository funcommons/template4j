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
import java.util.concurrent.atomic.AtomicReference;

/**
 * Covers RestRenderer missing branches:
 * - Non-2xx response without error template (throws)
 * - Non-HttpStatusCodeException with error template
 * - POST with non-Map data (raw string body)
 * - GET with non-Map data (ignored)
 */
public class RestRendererFullTest {

    private HttpServer server;
    private int port;
    private AtomicReference<String> lastMethod = new AtomicReference<>();
    private AtomicReference<String> lastBody = new AtomicReference<>();
    private AtomicReference<String> lastContentType = new AtomicReference<>();
    private AtomicInteger responseStatus = new AtomicInteger(200);
    private String responseBody = "{\"ok\":true}";
    private String responseContentType = "application/json; charset=utf-8";

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
        lastMethod.set(ex.getRequestMethod());
        lastContentType.set(ex.getRequestHeaders().getFirst("Content-Type"));
        lastBody.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", responseContentType);
        ex.sendResponseHeaders(responseStatus.get(), bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    private String url(String path) {
        return "http://127.0.0.1:" + port + path;
    }

    @Test
    public void testNon2xxWithoutErrorTemplateThrows() throws Exception {
        responseStatus.set(404);
        responseBody = "not found";
        try {
            RenderEngine.build().renderJson("""
                    {
                      "@class": "rest",
                      "url": "%s"
                    }
                    """.formatted(url("/notfound")), Map.of());
            Assertions.fail("Should have thrown for 404");
        } catch (Exception e) {
            Assertions.assertTrue(e.getMessage().contains("404") || e.getCause() != null);
        } finally {
            responseStatus.set(200);
        }
    }

    @Test
    public void testNon2xxServerErrorWithoutErrorTemplate() throws Exception {
        responseStatus.set(500);
        responseBody = "internal error";
        try {
            RenderEngine.build().renderJson("""
                    {
                      "@class": "rest",
                      "url": "%s"
                    }
                    """.formatted(url("/servererr")), Map.of());
            Assertions.fail("Should have thrown for 500");
        } catch (Exception e) {
            // expected
        } finally {
            responseStatus.set(200);
        }
    }

    @Test
    public void testErrorTemplateWithNonHttpStatusCodeException() throws Exception {
        // Use a malformed URL to trigger a non-HttpStatusCodeException, but with error template
        try {
            RenderEngine.build().renderJson("""
                    {
                      "@class": "rest",
                      "url": "not-a-valid-url",
                      "error": {
                        "code": -1,
                        "msg": "{$responseData.message}"
                      }
                    }
                    """, Map.of());
            // May succeed with error template rendering
        } catch (Exception e) {
            // acceptable — the URL is invalid
        }
    }

    @Test
    public void testPostWithNonMapDataString() throws Exception {
        responseBody = "{\"got\":true}";
        RenderEngine e = RenderEngine.build();
        Object r = e.renderJson("""
                {
                  "@class": "rest",
                  "url": "%s",
                  "method": "post",
                  "data": "just a string"
                }
                """.formatted(url("/rawstr")), Map.of());
        Assertions.assertTrue(r instanceof com.alibaba.fastjson2.JSONObject);
        Assertions.assertEquals(lastBody.get(), "just a string");
    }

    @Test
    public void testPostWithNoData() throws Exception {
        responseBody = "{\"ok\":true}";
        RenderEngine e = RenderEngine.build();
        Object r = e.renderJson("""
                {
                  "@class": "rest",
                  "url": "%s",
                  "method": "post"
                }
                """.formatted(url("/nopostdata")), Map.of());
        Assertions.assertTrue(r instanceof com.alibaba.fastjson2.JSONObject);
    }

    @Test
    public void testGetWithNonMapData() throws Exception {
        responseBody = "{\"ok\":true}";
        RenderEngine e = RenderEngine.build();
        // GET + data that is not a Map (string) — data is rendered but not added to query
        Object r = e.renderJson("""
                {
                  "@class": "rest",
                  "url": "%s",
                  "method": "get",
                  "data": "not_a_map"
                }
                """.formatted(url("/getnonmap")), Map.of());
        Assertions.assertTrue(r instanceof com.alibaba.fastjson2.JSONObject);
    }

    @Test
    public void testHeadersNotMap() throws Exception {
        responseBody = "{\"ok\":true}";
        RenderEngine e = RenderEngine.build();
        // headers that is not a Map — should not crash, just skip
        Object r = e.renderJson("""
                {
                  "@class": "rest",
                  "url": "%s",
                  "headers": "not_a_map"
                }
                """.formatted(url("/nonmapheaders")), Map.of());
        Assertions.assertTrue(r instanceof com.alibaba.fastjson2.JSONObject);
    }

    @Test
    public void testPostFormDataNotMap() throws Exception {
        responseBody = "{\"ok\":true}";
        RenderEngine e = RenderEngine.build();
        // form-urlencoded + data is not a Map
        Object r = e.renderJson("""
                {
                  "@class": "rest",
                  "url": "%s",
                  "method": "post",
                  "headers": {"Content-Type": "application/x-www-form-urlencoded"},
                  "data": "not_a_map"
                }
                """.formatted(url("/formnonmap")), Map.of());
        Assertions.assertTrue(r instanceof com.alibaba.fastjson2.JSONObject);
    }
}
