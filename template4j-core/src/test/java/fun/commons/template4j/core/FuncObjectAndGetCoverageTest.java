package fun.commons.template4j.core;

import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

/**
 * Covers FuncObject.get(String name) which calls call(name).
 * Also covers RenderBindings lines that were previously missed.
 */
public class FuncObjectAndGetCoverageTest {

    // FuncObject.get(String) — accessed via Groovy as f.get('argName')
    @Test
    public void testFuncObjectGetString() throws Exception {
        // When a FuncObject is accessed via get(name) in Groovy,
        // it calls call(name), which passes the name as an arg
        Object r = RenderEngine.build().renderJson("""
                {"$f()": "{% return _args[0] %}", "out": "{= f.get('hello') }"}
                """, Map.of());
        Assertions.assertEquals(((JSONObject) r).getString("out"), "hello");
    }

    // RenderBindings.containsKey() — test with various keys
    // This is called internally by the Groovy engine when checking if a binding exists
    @Test
    public void testRenderBindingsContainsKeyViaGroovy() throws Exception {
        // Access a $-prefixed variable from Groovy — triggers RenderBindings.containsKey
        Object r = RenderEngine.build().renderJson("""
                {"$x": "val", "out": "{= $x }"}
                """, Map.of());
        Assertions.assertEquals(((JSONObject) r).getString("out"), "val");
    }

    @Test
    public void testRenderBindingsContainsKeyNonVarViaGroovy() throws Exception {
        // Access a variable that doesn't exist — triggers RenderBindings.containsKey returning false
        Object r = RenderEngine.build().renderJson("""
                {"out": "{= $nonexistent }"}
                """, Map.of());
        // Should not throw — the variable is just null
        Assertions.assertNull(((JSONObject) r).get("out"));
    }
}
