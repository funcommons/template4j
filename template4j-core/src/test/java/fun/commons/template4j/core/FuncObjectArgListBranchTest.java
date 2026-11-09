package fun.commons.template4j.core;

import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

/**
 * Covers the missed branch in FuncObject$1 (anonymous ArrayList subclass):
 * get(int, Object) — the false branch of "value != null ? value : defaultValue",
 * i.e. when the argument at the given index is null, the defaultValue should be returned.
 *
 * Passing null directly as a Groovy function argument (e.g. f(null)) causes a
 * NullPointerException in Groovy's varargs method dispatch, so we use two alternative
 * strategies: (1) add null to _args inside the function body, and (2) call the function
 * with an explicitly typed Object[] containing null.
 */
public class FuncObjectArgListBranchTest {

    /**
     * When null is added to _args inside the function body,
     * _args.get(0, 'default') should return 'default' because
     * super.get(0) returns null, hitting the false branch of
     * "value != null ? value : defaultValue".
     */
    @Test
    public void testArgsGetWithNullElementReturnsDefault() throws Exception {
        Object r = RenderEngine.build().renderJson("""
                {
                  "$f()": "{% _args.add(null); return _args.get(0, 'default') %}",
                  "out": "{= f() }"
                }
                """, Map.of());
        Assertions.assertEquals(((JSONObject) r).getString("out"), "default");
    }

    /**
     * Same branch via _args.call(int, Object) which delegates to get(int, Object).
     */
    @Test
    public void testArgsCallWithNullElementReturnsDefault() throws Exception {
        Object r = RenderEngine.build().renderJson("""
                {
                  "$f()": "{% _args.add(null); return _args.call(0, 'fallback') %}",
                  "out": "{= f() }"
                }
                """, Map.of());
        Assertions.assertEquals(((JSONObject) r).getString("out"), "fallback");
    }

    /**
     * Null at index 1 with a non-null at index 0, to ensure the branch
     * is hit at a non-zero index too.
     */
    @Test
    public void testArgsGetNullAtSecondIndex() throws Exception {
        Object r = RenderEngine.build().renderJson("""
                {
                  "$f()": "{% _args.add('first'); _args.add(null); return _args.get(1, 'second_default') %}",
                  "out": "{= f() }"
                }
                """, Map.of());
        Assertions.assertEquals(((JSONObject) r).getString("out"), "second_default");
    }

    /**
     * When the argument is null and no default is provided (get(int) delegates to get(int, null)),
     * the result should be null (defaultValue is null).
     */
    @Test
    public void testArgsGetNullElementNoDefault() throws Exception {
        Object r = RenderEngine.build().renderJson("""
                {
                  "$f()": "{% _args.add(null); return _args.get(0) %}",
                  "out": "{= f() }"
                }
                """, Map.of());
        Assertions.assertNull(((JSONObject) r).get("out"));
    }

    /**
     * Non-null argument at the index should still return the actual value (true branch),
     * confirming both sides of the ternary work correctly.
     */
    @Test
    public void testArgsGetNonNullArgReturnsValue() throws Exception {
        Object r = RenderEngine.build().renderJson("""
                {
                  "$f()": "{% return _args.get(0, 'default') %}",
                  "out": "{= f('actual') }"
                }
                """, Map.of());
        Assertions.assertEquals(((JSONObject) r).getString("out"), "actual");
    }

    /**
     * Pass null through the function call using an explicitly typed Object[].
     * This bypasses Groovy's varargs method dispatch ambiguity with null.
     */
    @Test
    public void testNullArgViaObjectArrayCall() throws Exception {
        Object r = RenderEngine.build().renderJson("""
                {
                  "$f()": "{% return _args.get(0, 'default') %}",
                  "out": "{% return f.call((Object[])new Object[]{null}) %}"
                }
                """, Map.of());
        Assertions.assertEquals(((JSONObject) r).getString("out"), "default");
    }

    /**
     * Pass null through the function call using Object[] and access via call(int, Object).
     */
    @Test
    public void testNullArgViaObjectArrayCallWithCallMethod() throws Exception {
        Object r = RenderEngine.build().renderJson("""
                {
                  "$f()": "{% return _args.call(0, 'fb2') %}",
                  "out": "{% return f.call((Object[])new Object[]{null}) %}"
                }
                """, Map.of());
        Assertions.assertEquals(((JSONObject) r).getString("out"), "fb2");
    }
}
