package fun.commons.template4j.core;

import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

/**
 * Targeted coverage for TemplateUtil $xxx# lines 207-214.
 * The $xxx# regex at line 207 matches variable names ending with #.
 * Lines 209-214 create a const wrapper.
 */
public class TemplateUtilDollarHashFullTest {

    @Test
    public void testDollarVarHashNoRenderWithStringValue() throws Exception {
        // $name# with a string value → const wrapper, not rendered
        Object r = RenderEngine.build().renderJson("""
                {
                  "$val#": "{@field}",
                  "out": "{$val}"
                }
                """, Map.of("field", "rendered"));
        // $val# stores the literal "{@field}" without rendering
        Assertions.assertEquals(((JSONObject) r).getString("out"), "{@field}");
    }

    @Test
    public void testDollarVarHashNoRenderWithNumberValue() throws Exception {
        // $name# with a number value → const wrapper returns number as-is
        Object r = RenderEngine.build().renderJson("""
                {
                  "$val#": 42,
                  "out": "{$val}"
                }
                """, Map.of());
        Assertions.assertEquals(42, ((JSONObject) r).getIntValue("out"));
    }

    @Test
    public void testDollarFuncHashNoRender() throws Exception {
        // $f()# with a string value → const wrapper for function
        Object r = RenderEngine.build().renderJson("""
                {
                  "$f()#": "{@field}",
                  "out": "{= f() }"
                }
                """, Map.of("field", "rendered"));
        // const wrapper returns literal
        Assertions.assertEquals(((JSONObject) r).getString("out"), "{@field}");
    }

    @Test
    public void testDollarVarHashWithObjectValue() throws Exception {
        // $name# with an object value → const wrapper returns object
        Object r = RenderEngine.build().renderJson("""
                {
                  "$val#": {"nested": true},
                  "out": "{$val}"
                }
                """, Map.of());
        Assertions.assertNotNull(r);
        Object out = ((JSONObject) r).get("out");
        Assertions.assertTrue(out instanceof JSONObject);
        Assertions.assertTrue(((JSONObject) out).getBooleanValue("nested"));
    }

    @Test
    public void testDollarVarHashWithArrayValue() throws Exception {
        // $name# with an array value → const wrapper returns array
        Object r = RenderEngine.build().renderJson("""
                {
                  "$val#": [1, 2, 3],
                  "out": "{$val}"
                }
                """, Map.of());
        Assertions.assertNotNull(r);
    }
}
