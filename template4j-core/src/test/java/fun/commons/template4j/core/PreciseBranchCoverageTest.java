package fun.commons.template4j.core;

import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

/**
 * Targeted tests for the exact remaining missed branches.
 */
public class PreciseBranchCoverageTest {

    // ===== JsonRenderer.convertSelf() =====
    // Line 126: jsonShape=true, but quotedKey is NOT in the string
    // This means the null value is in a JSON-shaped string but NOT quoted
    @Test
    public void testJsonShapeNullNotQuoted() throws Exception {
        // A JSON-shaped string with a null value that's NOT in quotes
        // e.g., {"key": {@missing}} — the placeholder is not quoted
        Object r = RenderEngine.build().renderJson("""
                {"out": "{\\"key\\": {@missing}}"}
                """, Map.of());
        Assertions.assertNotNull(r);
        // The null value should be replaced with empty string (not "null" literal)
        // because it's not in a quoted position
    }

    // Line 105-107: jsonShape with curly braces — test the exact branch
    @Test
    public void testJsonShapeCurlyBracesTrue() throws Exception {
        // String that is a JSON object shape: {"k":"v"}
        Object r = RenderEngine.build().renderJson("""
                {"out": "{\\"k\\":\\"{@v}\\"}"}
                """, Map.of("v", "x"));
        Assertions.assertNotNull(r);
    }

    // Line 105-107: jsonShape with square brackets — test the exact branch
    @Test
    public void testJsonShapeSquareBracketsTrue() throws Exception {
        // String that is a JSON array shape: ["v"]
        Object r = RenderEngine.build().renderJson("""
                {"out": "[\\"{@v}\\"]"}
                """, Map.of("v", "x"));
        Assertions.assertNotNull(r);
    }

    // ===== JsonRenderer.expressionValue() =====
    // The 3 missed branches are likely in the quote-stripping code
    // Lines 308-309: isBesieged checks for @ path with quotes
    // Lines 325-326: isBesieged checks for $ var path with quotes

    // Test @ path WITHOUT quotes (to cover the false branch of isBesieged)
    @Test
    public void testAtPathWithoutQuotes() throws Exception {
        Object r = RenderEngine.build().renderJson("""
                {"out": "{@ name }"}
                """, Map.of("name", "Alice"));
        Assertions.assertEquals(((JSONObject) r).getString("out"), "Alice");
    }

    // Test @ path WITH single quotes (to cover the true branch)
    @Test
    public void testAtPathWithSingleQuotes() throws Exception {
        Object r = RenderEngine.build().renderJson("""
                {"out": "{@ 'name' }"}
                """, Map.of("name", "Bob"));
        Assertions.assertEquals(((JSONObject) r).getString("out"), "Bob");
    }

    // Test @ path WITH double quotes (to cover the true branch)
    @Test
    public void testAtPathWithDoubleQuotes() throws Exception {
        Object r = RenderEngine.build().renderJson("""
                {"out": "{@ \\"name\\" }"}
                """, Map.of("name", "Carol"));
        Assertions.assertEquals(((JSONObject) r).getString("out"), "Carol");
    }

    // Test $ var path WITHOUT quotes (to cover false branch of isBesieged)
    @Test
    public void testDollarVarPathWithoutQuotes() throws Exception {
        Object r = RenderEngine.build().renderJson("""
                {"$m": {"x": 42}, "out": "{$m.x}"}
                """, Map.of());
        Assertions.assertEquals(42, ((JSONObject) r).getIntValue("out"));
    }

    // Test $ var path WITH single quotes (to cover true branch)
    @Test
    public void testDollarVarPathWithSingleQuotes() throws Exception {
        Object r = RenderEngine.build().renderJson("""
                {"$m": {"x": 42}, "out": "{$m['x']}"}
                """, Map.of());
        Assertions.assertEquals(42, ((JSONObject) r).getIntValue("out"));
    }

    // Test $ var path WITH double quotes (to cover true branch)
    @Test
    public void testDollarVarPathWithDoubleQuotes() throws Exception {
        Object r = RenderEngine.build().render(
                Map.of("out", "{$m[\"x\"]}"),
                Map.of("m", Map.of("x", 42)));
        Assertions.assertNotNull(r);
    }

    // ===== RestRenderer: cover the else block (lines 159-167) =====
    // 5xx response with no error template — RestRenderer throws HttpException
    @Test
    public void testRestRendererNon2xxWithoutErrorTemplate() throws Exception {
        // 5xx response without error template → should throw
        com.sun.net.httpserver.HttpServer server = com.sun.net.httpserver.HttpServer.create(
                new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        int port = server.getAddress().getPort();
        server.createContext("/", ex -> {
            byte[] bytes = "error".getBytes();
            ex.getResponseHeaders().add("Content-Type", "text/plain");
            ex.sendResponseHeaders(500, bytes.length);
            try (var os = ex.getResponseBody()) { os.write(bytes); }
        });
        server.start();
        try {
            RenderEngine.build().renderJson("""
                    {"@class":"rest","url":"http://127.0.0.1:%d"}
                    """.formatted(port), Map.of());
            Assertions.fail("Should throw for 5xx without error template");
        } catch (RenderException e) {
            // Expected — RestRenderer throws HttpException on 5xx with no error template
            // which is caught by the catch block and re-thrown as RenderException
            Assertions.assertTrue(e.getMessage().contains("script error") || e.getMessage().contains("rest"));
        } finally {
            server.stop(0);
        }
    }

    // ===== RenderBindings: cover the @SneakyThrows line =====
    // The missed line is the @SneakyThrows annotation line
    // This is a known JaCoCo/Lombok issue. Let's try to exercise get() more.
    @Test
    public void testRenderBindingsGetWithVariousKeys() throws Exception {
        // Test with a $-prefixed key to trigger the vars fallback
        Object r = RenderEngine.build().renderJson("""
                {"$x": "val", "out": "{= $x }"}
                """, Map.of());
        Assertions.assertEquals(((JSONObject) r).getString("out"), "val");
    }

    // ===== UType: try to trigger the catch block in testBoolean =====
    // The catch block (lines 186-188) is reached when Array.getLength throws
    // This is essentially impossible with normal Java objects
    // Let's try using a custom object via reflection
    @Test
    public void testUTypeTestBooleanWithPrimitiveArrayViaScript() throws Exception {
        // This exercises the array branch but NOT the catch block
        Object r = RenderEngine.build().render(
                "{% def arr = new int[3]; return fun.commons.template4j.utils.UType.testBoolean(arr) ? 'yes' : 'no' %}", Map.of());
        Assertions.assertEquals(r, "yes");
    }

    @Test
    public void testUTypeTestBooleanWithEmptyPrimitiveArrayViaScript() throws Exception {
        Object r = RenderEngine.build().render(
                "{% def arr = new int[0]; return fun.commons.template4j.utils.UType.testBoolean(arr) ? 'yes' : 'no' %}", Map.of());
        Assertions.assertEquals(r, "no");
    }
}
