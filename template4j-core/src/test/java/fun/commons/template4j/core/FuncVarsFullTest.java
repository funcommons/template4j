package fun.commons.template4j.core;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

/**
 * Covers FuncVars: get, call, put, putAt.
 */
public class FuncVarsFullTest {

    @Test
    public void testGetAndCall() throws Exception {
        Object r = RenderEngine.build().renderJson("""
                {
                  "$x": "hello",
                  "out": "{= vars.get('x') }"
                }
                """, Map.of());
        Assertions.assertEquals(((com.alibaba.fastjson2.JSONObject) r).getString("out"), "hello");
    }

    @Test
    public void testCallDelegatesToVars() throws Exception {
        Object r = RenderEngine.build().renderJson("""
                {
                  "$y": "world",
                  "out": "{= vars.call('y') }"
                }
                """, Map.of());
        Assertions.assertEquals(((com.alibaba.fastjson2.JSONObject) r).getString("out"), "world");
    }

    @Test
    public void testPutAndPutAt() throws Exception {
        Object r = RenderEngine.build().renderJson("""
                {
                  "$x": "initial",
                  "out": "{= vars.putAt('z', 'setval'); return vars.get('z') }"
                }
                """, Map.of());
        Assertions.assertEquals(((com.alibaba.fastjson2.JSONObject) r).getString("out"), "setval");
    }

    @Test
    public void testPutViaScript() throws Exception {
        // render() with a bare script returns the script result directly
        Object r = RenderEngine.build().render("{% vars.put('k','v'); return vars.get('k') %}", Map.of());
        Assertions.assertEquals(r, "v");
    }
}
