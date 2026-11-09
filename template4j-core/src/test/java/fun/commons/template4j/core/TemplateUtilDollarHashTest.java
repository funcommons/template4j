package fun.commons.template4j.core;

import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

/**
 * Covers TemplateUtil lines 207-214: $xxx# variable no-render path.
 * The $xxx# regex match at line 207 and the const-wrap at lines 209-214.
 */
public class TemplateUtilDollarHashTest {

    @Test
    public void testDollarVarHashNoRender() throws Exception {
        // $val# → const wrapper, value not rendered
        Object r = RenderEngine.build().renderJson("""
                {
                  "$val#": "{@name}",
                  "out": "{$val}"
                }
                """, Map.of("name", "hello"));
        // $val# wraps value in const, so it's the literal "{@name}"
        Assertions.assertEquals(((JSONObject) r).getString("out"), "{@name}");
    }

    @Test
    public void testDollarFuncHashNoRender() throws Exception {
        // $f()# → const wrapper for function
        Object r = RenderEngine.build().renderJson("""
                {
                  "$f()#": "{@name}",
                  "out": "{= f() }"
                }
                """, Map.of("name", "hello"));
        Assertions.assertEquals(((JSONObject) r).getString("out"), "{@name}");
    }

    @Test
    public void testDollarVarHashWithNonStringValue() throws Exception {
        // $val# with a non-string value (e.g. a number)
        Object r = RenderEngine.build().renderJson("""
                {
                  "$val#": 42,
                  "out": "{$val}"
                }
                """, Map.of());
        Assertions.assertEquals(42, ((JSONObject) r).getIntValue("out"));
    }

    @Test
    public void testDollarVarHashWithObjectValue() throws Exception {
        // $val# with an object value
        Object r = RenderEngine.build().renderJson("""
                {
                  "$val#": {"nested": true},
                  "out": "{$val}"
                }
                """, Map.of());
        Assertions.assertNotNull(r);
        // The const wrapper returns the object as-is without rendering
    }
}
