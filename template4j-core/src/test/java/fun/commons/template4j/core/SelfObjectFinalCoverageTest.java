package fun.commons.template4j.core;

import fun.commons.template4j.core.SelfObject;
import fun.commons.template4j.core.JsonRenderer;
import fun.commons.template4j.core.RenderException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

/**
 * Covers SelfObject remaining branches:
 * - Line 50: getResourceUrl with parent fallback
 * - Line 87: vars() with parent lookup when result is null
 * - Line 158: putVarDefine with key not starting with $
 */
public class SelfObjectFinalCoverageTest {

    // Line 50: getResourceUrl — child delegates to parent when self.resourceUrl is blank
    @Test
    public void testResourceUrlParentFallback() throws Exception {
        // Use renderUrl which sets resourceUrl on root SelfObject
        // Any nested template access should delegate to parent's resourceUrl
        Object r = RenderEngine.build().renderUrl("template4j-test.json", Map.of());
        Assertions.assertNotNull(r);
    }

    // Line 87: vars() parent lookup — variable defined in parent, accessed from child
    @Test
    public void testVarsWithParentLookup() throws Exception {
        Object r = RenderEngine.build().renderJson("""
                {"$shared": "parent_val", "child": {"out": "{$shared}"}}
                """, Map.of());
        Assertions.assertEquals(((com.alibaba.fastjson2.JSONObject) r).getJSONObject("child").getString("out"), "parent_val");
    }

    // Line 87: vars() with parentVars=true and result null in self → walks up to parent
    @Test
    public void testVarsWalksUpMultipleLevels() throws Exception {
        Object r = RenderEngine.build().renderJson("""
                {"$deep": "deep_val", "a": {"b": {"out": "{$deep}"}}}
                """, Map.of());
        com.alibaba.fastjson2.JSONObject result = (com.alibaba.fastjson2.JSONObject) r;
        Assertions.assertEquals(result.getJSONObject("a").getJSONObject("b").getString("out"), "deep_val");
    }

    // Line 158: putVarDefine with $ key containing a map with keys not starting with $
    @Test
    public void testPutVarDefineKeyWithoutDollar() throws Exception {
        Object r = RenderEngine.build().renderJson("""
                {"$": {"name": "value"}, "out": "{$name}"}
                """, Map.of());
        Assertions.assertEquals(((com.alibaba.fastjson2.JSONObject) r).getString("out"), "value");
    }

    // Line 158: putVarDefine with $ key containing a map with keys already starting with $
    @Test
    public void testPutVarDefineKeyWithDollar() throws Exception {
        Object r = RenderEngine.build().renderJson("""
                {"$": {"$name": "value"}, "out": "{$name}"}
                """, Map.of());
        Assertions.assertEquals(((com.alibaba.fastjson2.JSONObject) r).getString("out"), "value");
    }

    // Line 158: putVarDefine with invalid type (not Map or List)
    @Test
    public void testPutVarDefineInvalidType() throws Exception {
        try {
            RenderEngine.build().renderJson("""
                    {"$": "not_a_map_or_list"}
                    """, Map.of());
            Assertions.fail("Should throw for invalid $ type");
        } catch (RenderException e) {
            Assertions.assertTrue(e.getMessage().contains("not support"));
        }
    }
}
