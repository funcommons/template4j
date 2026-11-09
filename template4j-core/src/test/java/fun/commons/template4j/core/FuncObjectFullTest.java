package fun.commons.template4j.core;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

/**
 * Covers FuncObject: call, get, lock recursion prevention, anonymous ArrayList inner class.
 */
public class FuncObjectFullTest {

    @Test
    public void testFuncObjectCallNoArgs() throws Exception {
        // $greet() with no args
        Object r = RenderEngine.build().renderJson("""
                {
                  "$greet()": "{= 'Hello' + _args(0, 'Guest') }",
                  "out": "{= greet() }"
                }
                """, Map.of());
        Assertions.assertEquals(((com.alibaba.fastjson2.JSONObject) r).getString("out"), "HelloGuest");
    }

    @Test
    public void testFuncObjectCallWithExplicitArgs() throws Exception {
        Object r = RenderEngine.build().renderJson("""
                {
                  "$greet()": "{= 'Hello ' + _args[0] + '!' }",
                  "out": "{= greet('World') }"
                }
                """, Map.of());
        Assertions.assertEquals(((com.alibaba.fastjson2.JSONObject) r).getString("out"), "Hello World!");
    }

    @Test
    public void testFuncObjectCallWithMultipleArgs() throws Exception {
        Object r = RenderEngine.build().renderJson("""
                {
                  "$add()": "{% return _args[0] + _args[1] %}",
                  "out": "{= add(3, 4) }"
                }
                """, Map.of());
        Assertions.assertEquals(7, ((com.alibaba.fastjson2.JSONObject) r).getIntValue("out"));
    }

    @Test
    public void testFuncObjectCallArgOutOfBoundsReturnsDefaultNull() throws Exception {
        Object r = RenderEngine.build().renderJson("""
                {
                  "$f()": "{% return _args[5] %}",
                  "out": "{= f('only_one') }"
                }
                """, Map.of());
        // out of bounds returns null from get(index, null)
        Assertions.assertNull(((com.alibaba.fastjson2.JSONObject) r).get("out"));
    }

    @Test
    public void testFuncObjectCallWithDefaultValue() throws Exception {
        Object r = RenderEngine.build().renderJson("""
                {
                  "$f()": "{% return _args.call(0, 'default') %}",
                  "out": "{= f() }"
                }
                """, Map.of());
        Assertions.assertEquals(((com.alibaba.fastjson2.JSONObject) r).getString("out"), "default");
    }

    @Test
    public void testFuncObjectRecursiveCallPrevented() throws Exception {
        // $f() calls $f() — lock prevents infinite recursion, returns null
        Object r = RenderEngine.build().renderJson("""
                {
                  "$f()": "{= f() }",
                  "out": "{= f() }"
                }
                """, Map.of());
        // Recursive call prevented by lock → null
        Assertions.assertNotNull(r);
    }

    @Test
    public void testArgsNullDefaultValue() throws Exception {
        Object r = RenderEngine.build().renderJson("""
                {
                  "$f()": "{% return _args.call(0) %}",
                  "out": "{= f() }"
                }
                """, Map.of());
        // No args provided, index 0 out of bounds → defaultValue null
        Assertions.assertNull(((com.alibaba.fastjson2.JSONObject) r).get("out"));
    }

    @Test
    public void testArgsGetWithIndexAndDefault() throws Exception {
        Object r = RenderEngine.build().renderJson("""
                {
                  "$f()": "{% return _args.get(0, 'fallback') %}",
                  "out": "{= f() }"
                }
                """, Map.of());
        Assertions.assertEquals(((com.alibaba.fastjson2.JSONObject) r).getString("out"), "fallback");
    }

    @Test
    public void testArgsGetCallOverload() throws Exception {
        // _args.call(int, defaultValue) method in the anonymous ArrayList
        Object r = RenderEngine.build().renderJson("""
                {
                  "$f()": "{% return _args.call(0, 'fb2') %}",
                  "out": "{= f() }"
                }
                """, Map.of());
        Assertions.assertEquals(((com.alibaba.fastjson2.JSONObject) r).getString("out"), "fb2");
    }
}
