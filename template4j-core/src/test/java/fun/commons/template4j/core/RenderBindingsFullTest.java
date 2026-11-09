package fun.commons.template4j.core;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

/**
 * Covers RenderBindings: get() with vars fallback, containsKey() with vars regex.
 * Target: cover the get() method line 10–34 and containsKey() method line 37–57 fully.
 */
public class RenderBindingsFullTest {

    @Test
    public void testGetWithVarsFallback() throws Exception {
        // When result is null and vars exists, get delegates to FuncVars
        Object r = RenderEngine.build().renderJson("""
                {
                  "$myvar": "hello",
                  "out": "{= $myvar }"
                }
                """, Map.of());
        Assertions.assertEquals(((com.alibaba.fastjson2.JSONObject) r).getString("out"), "hello");
    }

    @Test
    public void testGetWithDollarPrefixAndVars() throws Exception {
        // Access vars with $ prefix from Groovy — RenderBindings strips $
        Object r = RenderEngine.build().renderJson("""
                {
                  "$x": "val",
                  "out": "{= $x }"
                }
                """, Map.of());
        Assertions.assertEquals(((com.alibaba.fastjson2.JSONObject) r).getString("out"), "val");
    }

    @Test
    public void testContainsKeyWithVarsAndValidName() throws Exception {
        // containsKey should return true for $varName patterns when vars is present
        Object r = RenderEngine.build().renderJson("""
                {
                  "$abc": 123,
                  "out": "{= $abc }"
                }
                """, Map.of());
        Assertions.assertEquals(123, ((com.alibaba.fastjson2.JSONObject) r).getIntValue("out"));
    }

    @Test
    public void testGetJsr223KeepGlobals() {
        RenderBindings b = new RenderBindings();
        b.put("#jsr223.groovy.engine.keep.globals", "KEEP");
        Assertions.assertEquals(b.get("#jsr223.groovy.engine.keep.globals"), "KEEP");
        // No vars bean — should return directly without vars lookup
    }

    @Test
    public void testContainsKeyJsr223Globals() {
        RenderBindings b = new RenderBindings();
        Assertions.assertFalse(b.containsKey("jsr223.groovy.engine.keep.globals"));
    }

    @Test
    public void testContainsKeyScriptFilename() {
        RenderBindings b = new RenderBindings();
        Assertions.assertFalse(b.containsKey("javax.script.filename"));
    }

    @Test
    public void testGetNonexistentKey() {
        RenderBindings b = new RenderBindings();
        Assertions.assertNull(b.get("nonexistent"));
    }

    @Test
    public void testContainsKeyInvalidVarName() {
        RenderBindings b = new RenderBindings();
        Assertions.assertFalse(b.containsKey("*"));
        Assertions.assertFalse(b.containsKey(".."));
    }
}
