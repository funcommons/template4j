package fun.commons.template4j.core;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
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
 * 覆盖 {@link RestRenderer} 全部分支：
 * - GET / POST / PUT / DELETE
 * - JSON / form / raw body
 * - dataType: auto / json-object / json-array / yaml-object / yaml-array / string
 * - success / error 分支
 * - url 与 select 字段二选一
 */
public class RestRendererTest {

    private HttpServer server;
    private int port;
    private AtomicReference<String> lastMethod = new AtomicReference<>();
    private AtomicReference<String> lastPath = new AtomicReference<>();
    private AtomicReference<String> lastQuery = new AtomicReference<>();
    private AtomicReference<String> lastContentType = new AtomicReference<>();
    private AtomicReference<String> lastBody = new AtomicReference<>();
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
        lastPath.set(ex.getRequestURI().getPath());
        lastQuery.set(ex.getRequestURI().getQuery());
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

    // ========== GET ==========

    @Test
    public void testGetSimple() throws Exception {
        responseBody = "{\"hello\":\"world\"}";
        RenderEngine e = RenderEngine.build();
        Object r = e.renderJson("{\"@class\":\"rest\",\"url\":\"" + url("/api") + "\"}", Map.of());
        Assertions.assertTrue(r instanceof JSONObject);
        Assertions.assertEquals(((JSONObject) r).getString("hello"), "world");
        Assertions.assertEquals(lastMethod.get(), "GET");
    }

    @Test
    public void testGetQueryFromData() throws Exception {
        responseBody = "{}";
        RenderEngine e = RenderEngine.build();
        e.renderJson("""
                {
                  "@class":"rest",
                  "url":"%s",
                  "method":"get",
                  "data":{ "a":"1", "b":"2" }
                }
                """.formatted(url("/q")), Map.of());
        String q = lastQuery.get();
        Assertions.assertNotNull(q);
        Assertions.assertTrue(q.contains("a=1"), "query 缺少 a=1: " + q);
        Assertions.assertTrue(q.contains("b=2"), "query 缺少 b=2: " + q);
    }

    @Test
    public void testGetQueryFromUrlPlaceholder() throws Exception {
        responseBody = "{}";
        RenderEngine e = RenderEngine.build();
        e.renderJson("""
                {
                  "@class":"rest",
                  "url":"%s?id={@uid}"
                }
                """.formatted(url("/p")), Map.of("uid", 42));
        Assertions.assertEquals(lastQuery.get(), "id=42");
    }

    // ========== POST JSON ==========

    @Test
    public void testPostJsonBody() throws Exception {
        responseBody = "{\"received\":true,\"name\":\"alice\"}";
        RenderEngine e = RenderEngine.build();
        Object r = e.renderJson("""
                {
                  "@class":"rest",
                  "url":"%s",
                  "method":"post",
                  "data":{ "name":"{@name}", "n":"{@n}" }
                }
                """.formatted(url("/post")), Map.of("name", "alice", "n", 7));
        Assertions.assertTrue(r instanceof JSONObject, "应返回 JSONObject");
        Assertions.assertEquals(((JSONObject) r).getString("name"), "alice");
        Assertions.assertEquals(lastMethod.get(), "POST");
        Assertions.assertTrue(lastContentType.get() != null && lastContentType.get().toLowerCase().contains("json"), "Content-Type 应包含 json: " + lastContentType.get());
        String body = lastBody.get();
        Assertions.assertTrue(body.contains("\"name\":\"alice\""), "body 应含 name=alice: " + body);
        // {@n}=7 在 JSON 中是数字，所以是 "n":7 而不是 "n":"7"
        Assertions.assertTrue(body.contains("\"n\":7"), "body 应含 n=7: " + body);
    }

    // ========== POST form ==========

    @Test
    public void testPostFormUrlencoded() throws Exception {
        responseBody = "{}";
        RenderEngine e = RenderEngine.build();
        e.renderJson("""
                {
                  "@class":"rest",
                  "url":"%s",
                  "method":"post",
                  "headers":{ "Content-Type":"application/x-www-form-urlencoded" },
                  "data":{ "x":"1", "y":"2" }
                }
                """.formatted(url("/form")), Map.of());
        Assertions.assertTrue(lastContentType.get().toLowerCase().contains("form-urlencoded"), "Content-Type 应为 form-urlencoded");
        String body = lastBody.get();
        Assertions.assertTrue(body.contains("x=1"), "form body 应含 x=1: " + body);
        Assertions.assertTrue(body.contains("y=2"), "form body 应含 y=2: " + body);
    }

    @Test
    public void testPostMultipartFormData() throws Exception {
        responseBody = "{}";
        RenderEngine e = RenderEngine.build();
        e.renderJson("""
                {
                  "@class":"rest",
                  "url":"%s",
                  "method":"post",
                  "headers":{ "Content-Type":"multipart/form-data" },
                  "data":{ "x":"1" }
                }
                """.formatted(url("/multi")), Map.of());
        Assertions.assertTrue(lastContentType.get().toLowerCase().contains("multipart"), "Content-Type 应为 multipart");
    }

    @Test
    public void testPostRawStringBody() throws Exception {
        responseBody = "{}";
        RenderEngine e = RenderEngine.build();
        e.renderJson("""
                {
                  "@class":"rest",
                  "url":"%s",
                  "method":"put",
                  "data":"raw text body"
                }
                """.formatted(url("/raw")), Map.of());
        Assertions.assertEquals(lastMethod.get(), "PUT");
        Assertions.assertEquals(lastBody.get(), "raw text body");
    }

    // ========== dataType ==========

    @Test
    public void testDataTypeJsonObject() throws Exception {
        responseBody = "{\"a\":1}";
        RenderEngine e = RenderEngine.build();
        Object r = e.renderJson("""
                {
                  "@class":"rest",
                  "url":"%s",
                  "dataType":"json-object"
                }
                """.formatted(url("/jsobj")), Map.of());
        Assertions.assertTrue(r instanceof JSONObject);
        Assertions.assertEquals(1, ((JSONObject) r).getIntValue("a"));
    }

    @Test
    public void testDataTypeJsonArray() throws Exception {
        responseBody = "[1,2,3]";
        RenderEngine e = RenderEngine.build();
        Object r = e.renderJson("""
                {
                  "@class":"rest",
                  "url":"%s",
                  "dataType":"json-array"
                }
                """.formatted(url("/jsarr")), Map.of());
        Assertions.assertTrue(r instanceof JSONArray);
        Assertions.assertEquals(3, ((JSONArray) r).size());
    }

    @Test
    public void testDataTypeYamlObject() throws Exception {
        responseBody = "a: 1\nb: hello\n";
        RenderEngine e = RenderEngine.build();
        Object r = e.renderJson("""
                {
                  "@class":"rest",
                  "url":"%s",
                  "dataType":"yaml-object"
                }
                """.formatted(url("/yamlobj")), Map.of());
        Assertions.assertTrue(r instanceof JSONObject);
        Assertions.assertEquals(1, ((JSONObject) r).getIntValue("a"));
        Assertions.assertEquals(((JSONObject) r).getString("b"), "hello");
    }

    @Test
    public void testDataTypeString() throws Exception {
        responseBody = "raw text";
        RenderEngine e = RenderEngine.build();
        Object r = e.renderJson("""
                {
                  "@class":"rest",
                  "url":"%s",
                  "dataType":"string"
                }
                """.formatted(url("/s")), Map.of());
        Assertions.assertEquals(r, "raw text");
    }

    @Test
    public void testDataTypeAutoJsonObject() throws Exception {
        responseBody = "{\"x\":1}";
        RenderEngine e = RenderEngine.build();
        Object r = e.renderJson("""
                {
                  "@class":"rest",
                  "url":"%s"
                }
                """.formatted(url("/auto")), Map.of());
        Assertions.assertTrue(r instanceof JSONObject);
    }

    // ========== success / error ==========

    @Test
    public void testSuccessTemplate() throws Exception {
        responseBody = "{\"data\":{\"id\":99,\"name\":\"alice\"}}";
        RenderEngine e = RenderEngine.build();
        Object r = e.renderJson("""
                {
                  "@class":"rest",
                  "url":"%s",
                  "success":{
                    "code":0,
                    "user":"{$responseData.data.name}",
                    "id":"{$responseData.data.id}"
                  }
                }
                """.formatted(url("/succ")), Map.of());
        JSONObject jo = (JSONObject) r;
        Assertions.assertEquals(0, jo.getIntValue("code"));
        Assertions.assertEquals(jo.getString("user"), "alice");
        Assertions.assertEquals(99, jo.getIntValue("id"));
    }

    @Test
    public void testErrorTemplate() throws Exception {
        responseStatus.set(500);
        responseBody = "{\"status\":500,\"message\":\"boom\"}";
        try {
            RenderEngine e = RenderEngine.build();
            Object r = e.renderJson("""
                    {
                      "@class":"rest",
                      "url":"%s",
                      "error":{
                        "code":"{$responseData.status}",
                        "body":"{$responseData.message}"
                      }
                    }
                    """.formatted(url("/err")), Map.of());
            JSONObject jo = (JSONObject) r;
            Assertions.assertEquals(500, jo.getIntValue("code"));
            // B2 修复后：responseData.message 为响应体原文
            Assertions.assertEquals(responseBody, jo.getString("body"));
        } finally {
            responseStatus.set(200);
        }
    }

    @Test
    public void testNoUrlFieldThrows() {
        RenderEngine e = RenderEngine.build();
        try {
            e.renderJson("{\"@class\":\"rest\"}", Map.of());
            Assertions.fail("缺少 url/select 应抛错");
        } catch (Exception ex) {
            // 期望抛错
        }
    }

    @Test
    public void testDeleteMethod() throws Exception {
        responseBody = "{}";
        RenderEngine e = RenderEngine.build();
        e.renderJson("""
                {
                  "@class":"rest",
                  "url":"%s",
                  "method":"delete"
                }
                """.formatted(url("/d")), Map.of());
        Assertions.assertEquals(lastMethod.get(), "DELETE");
    }

    @Test
    public void testSelectAliasForUrl() throws Exception {
        responseBody = "{\"alias\":true}";
        RenderEngine e = RenderEngine.build();
        Object r = e.renderJson("""
                {
                  "@class":"rest",
                  "select":"%s"
                }
                """.formatted(url("/alias")), Map.of());
        Assertions.assertEquals(Boolean.TRUE, ((JSONObject) r).get("alias"));
    }
}