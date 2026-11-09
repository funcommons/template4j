package fun.commons.template4j.core;

import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

/**
 * Covers jsonShape branches with both { } and [ ] shapes,
 * and null handling in JSON-shaped strings.
 */
public class JsonShapeCoverageTest {

    // jsonShape with curly braces — {"k":"{@v}"} shape
    @Test
    public void testJsonShapeCurlyWithExpression() throws Exception {
        Object r = RenderEngine.build().renderJson("""
                {"out": "{\\"k\\":\\"{@v}\\"}"}
                """, Map.of("v", "x"));
        Assertions.assertNotNull(r);
    }

    // jsonShape with curly braces and null value
    @Test
    public void testJsonShapeCurlyWithNullQuoted() throws Exception {
        // JSON-shaped string with null value in quoted position
        Object r = RenderEngine.build().renderJson("""
                {"out": "{\\"k\\":\\"{@missing}\\"}"}
                """, Map.of());
        Assertions.assertNotNull(r);
        // The null should be replaced with "null" literal (jsonShape=true, quotedKey found)
    }

    // jsonShape with curly braces and null value NOT quoted
    @Test
    public void testJsonShapeCurlyWithNullNotQuoted() throws Exception {
        // JSON-shaped string with null value in non-quoted position
        Object r = RenderEngine.build().renderJson("""
                {"out": "{\\"k\\":{@missing}}"}
                """, Map.of());
        Assertions.assertNotNull(r);
    }

    // jsonShape with square brackets — ["{@v}"] shape
    @Test
    public void testJsonShapeSquareWithExpression() throws Exception {
        Object r = RenderEngine.build().renderJson("""
                {"out": "[\\"{@v}\\"]"}
                """, Map.of("v", "x"));
        Assertions.assertNotNull(r);
    }

    // jsonShape with square brackets and null value
    @Test
    public void testJsonShapeSquareWithNullQuoted() throws Exception {
        Object r = RenderEngine.build().renderJson("""
                {"out": "[\\"{@missing}\\"]"}
                """, Map.of());
        Assertions.assertNotNull(r);
    }

    // String NOT jsonShape — just a regular string template
    @Test
    public void testNotJsonShapeString() throws Exception {
        Object r = RenderEngine.build().renderJson("""
                {"out": "hello {@v} world"}
                """, Map.of("v", "x"));
        Assertions.assertEquals(((JSONObject) r).getString("out"), "hello x world");
    }

    // String NOT jsonShape with null value — should replace with empty
    @Test
    public void testNotJsonShapeWithNull() throws Exception {
        Object r = RenderEngine.build().renderJson("""
                {"out": "hello {@missing} world"}
                """, Map.of());
        Assertions.assertEquals(((JSONObject) r).getString("out"), "hello  world");
    }

    // Single expression return — bypasses jsonShape entirely
    @Test
    public void testSingleExpressionReturn() throws Exception {
        Object r = RenderEngine.build().renderJson("""
                {"out": "{@v}"}
                """, Map.of("v", "x"));
        Assertions.assertEquals(((JSONObject) r).getString("out"), "x");
    }
}
