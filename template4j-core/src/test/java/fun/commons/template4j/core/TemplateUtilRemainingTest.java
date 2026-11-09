package fun.commons.template4j.core;

import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

/**
 * Covers TemplateUtil remaining missed lines:
 * - Lines 209-214: $xxx# variable no-render
 * - Line 207 branch: $xxx#() function no-render
 */
public class TemplateUtilRemainingTest {

    @Test
    public void testDollarVarHashNoRender() throws Exception {
        // $val# stores value as-is (wrapped in const), not rendered
        Object r = RenderEngine.build().renderJson("""
                {
                  "$val#": "{@name}",
                  "out": "{$val}"
                }
                """, Map.of("name", "hello"));
        Assertions.assertEquals(((JSONObject) r).getString("out"), "{@name}");
    }

    @Test
    public void testDollarFuncHashNoRender() throws Exception {
        // $f()# — function with no-render, stored as const
        Object r = RenderEngine.build().renderJson("""
                {
                  "$f()#": "{@name}",
                  "out": "{= f() }"
                }
                """, Map.of("name", "hello"));
        // The const wrapper returns the literal string
        Assertions.assertEquals(((JSONObject) r).getString("out"), "{@name}");
    }

    @Test
    public void testSimplifyAtClassValue() throws Exception {
        // value "@if:select" → @class:if, select:select
        // The value form uses @if:select syntax where the whole value is "@if:{@val}"
        // But this is in the value, not the key. The simplify for values uses
        // the regex ^@\w+(\|[\w!_.-]+)?:[\w\W]*
        // Let's use the key form instead: @if key
        Object r = RenderEngine.build().renderJson("""
                {
                  "@if": "{@val}",
                  "is": "!null",
                  "then": "yes"
                }
                """, Map.of("val", "x"));
        Assertions.assertEquals(r, "yes");
    }

    @Test
    public void testSimplifyAtClassKeyWithMethod() throws Exception {
        // @if|method key → @class: if, method: method
        Object r = RenderEngine.build().renderJson("""
                {
                  "@if|!null": "{@val}",
                  "then": "y"
                }
                """, Map.of("val", "x"));
        Assertions.assertEquals(r, "y");
    }

    @Test
    public void testSimplifyDollarShorthand() throws Exception {
        // value "$" on a key → variable shorthand: {% $keyname %}
        Object r = RenderEngine.build().renderJson("""
                {
                  "$name": "test",
                  "name": "$"
                }
                """, Map.of());
        Assertions.assertEquals(((JSONObject) r).getString("name"), "test");
    }
}
