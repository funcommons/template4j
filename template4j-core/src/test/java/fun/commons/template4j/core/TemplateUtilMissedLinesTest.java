package fun.commons.template4j.core;

import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

/**
 * Covers TemplateUtil missed lines:
 * - Lines 207-214: $xxx# variable (no-render var)
 * - simplify with @class method
 * - simplify @class key shorthand
 */
public class TemplateUtilMissedLinesTest {

    @Test
    public void testDollarVarNoRender() throws Exception {
        // $val# — variable taken as-is without rendering (wrapped in const)
        Object r = RenderEngine.build().renderJson("""
                {
                  "$val#": "{@name}",
                  "out": "{$val}"
                }
                """, Map.of("name", "hello"));
        // $val# stores the literal template string, not rendered result
        Assertions.assertNotNull(r);
        Assertions.assertEquals(((JSONObject) r).getString("out"), "{@name}");
    }

    @Test
    public void testSimplifyAtClassValue() throws Exception {
        // @if: value → simplified to @class: if, select: value
        Object r = RenderEngine.build().renderJson("""
                {
                  "@if": "{@name}",
                  "is": "!null",
                  "then": "yes"
                }
                """, Map.of("name", "test"));
        Assertions.assertEquals(r, "yes");
    }

    @Test
    public void testSimplifyDollarWithType() throws Exception {
        // $|type → variable shorthand with type cast
        Object r = RenderEngine.build().renderJson("""
                {
                  "$x": "123",
                  "val": "$|Integer"
                }
                """, Map.of());
        Assertions.assertNotNull(r);
    }

    @Test
    public void testSimplifyAtWithType() throws Exception {
        // @|type → data shorthand with type cast
        Object r = RenderEngine.build().renderJson("""
                {
                  "val": "@|String"
                }
                """, Map.of("val", 42));
        Assertions.assertEquals(((JSONObject) r).getString("val"), "42");
    }

    @Test
    public void testSimplifyAtClassKeyShorthand() throws Exception {
        // @if key → @class: if, select: value
        Object r = RenderEngine.build().renderJson("""
                {
                  "@if": "{@name}",
                  "is": "!null",
                  "then": "y"
                }
                """, Map.of("name", "test"));
        Assertions.assertEquals(r, "y");
    }
}
