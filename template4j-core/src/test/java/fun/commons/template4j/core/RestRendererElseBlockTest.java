package fun.commons.template4j.core;

import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

/**
 * Covers RestRenderer's 4xx/5xx error path with an {@code error} template.
 *
 * <p>With the JDK {@link java.net.http.HttpClient} backend, 4xx/5xx responses
 * are NOT auto-thrown — RestRenderer checks {@code statusCode >= 300} and
 * routes to the {@code error} template. No reflection / no-throw error
 * handler injection is needed.
 */
public class RestRendererElseBlockTest {

    @Test
    public void testRestRenderer5xxWithErrorTemplate() throws Exception {
        com.sun.net.httpserver.HttpServer server = com.sun.net.httpserver.HttpServer.create(
                new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        int port = server.getAddress().getPort();
        server.createContext("/", ex -> {
            byte[] bytes = "server error".getBytes();
            ex.getResponseHeaders().add("Content-Type", "text/plain");
            ex.sendResponseHeaders(500, bytes.length);
            try (var os = ex.getResponseBody()) { os.write(bytes); }
        });
        server.start();
        try {
            Object r = RenderEngine.build().renderJson("""
                    {"@class":"rest","url":"http://127.0.0.1:%d","error":{"code":"{$responseData.status}","msg":"{$responseData.message}"}}
                    """.formatted(port), Map.of());
            Assertions.assertTrue(r instanceof JSONObject);
            Assertions.assertEquals(500, ((JSONObject) r).getIntValue("code"));
            Assertions.assertEquals("server error", ((JSONObject) r).getString("msg"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void testRestRenderer4xxWithErrorTemplate() throws Exception {
        com.sun.net.httpserver.HttpServer server = com.sun.net.httpserver.HttpServer.create(
                new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        int port = server.getAddress().getPort();
        server.createContext("/", ex -> {
            byte[] bytes = "not found".getBytes();
            ex.getResponseHeaders().add("Content-Type", "text/plain");
            ex.sendResponseHeaders(404, bytes.length);
            try (var os = ex.getResponseBody()) { os.write(bytes); }
        });
        server.start();
        try {
            Object r = RenderEngine.build().renderJson("""
                    {"@class":"rest","url":"http://127.0.0.1:%d","error":{"code":"{$responseData.status}","msg":"{$responseData.message}"}}
                    """.formatted(port), Map.of());
            Assertions.assertTrue(r instanceof JSONObject);
            Assertions.assertEquals(404, ((JSONObject) r).getIntValue("code"));
            Assertions.assertEquals("not found", ((JSONObject) r).getString("msg"));
        } finally {
            server.stop(0);
        }
    }
}
