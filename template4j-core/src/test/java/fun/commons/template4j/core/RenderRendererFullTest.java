package fun.commons.template4j.core;

import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

/**
 * Covers RenderRenderer: data as JSONObject, non-JSONObject data cast, no-data branch.
 * Target: cover line 40 (UType.castValue non-JSONObject path) and branch at line 37.
 */
public class RenderRendererFullTest {

    @Test
    public void testRenderWithJsonObjectData() throws Exception {
        Object r = RenderEngine.build().renderJson("""
                {
                  "@class": "render",
                  "data": {"name": "alice"},
                  "select": "hello {@name}"
                }
                """, Map.of());
        Assertions.assertEquals(r, "hello alice");
    }

    @Test
    public void testRenderWithNonJsonObjectDataCast() throws Exception {
        // data is a Map rendered to a JSONObject — triggers the UType.castValue branch
        Object r = RenderEngine.build().renderJson("""
                {
                  "@class": "render",
                  "data": {
                    "nested": true
                  },
                  "select": "{@nested}"
                }
                """, Map.of());
        Assertions.assertEquals(true, r);
    }

    @Test
    public void testRenderWithoutData() throws Exception {
        Object r = RenderEngine.build().renderJson("""
                {
                  "@class": "render",
                  "select": "{@name}"
                }
                """, Map.of("name", "bob"));
        Assertions.assertEquals(r, "bob");
    }

    @Test
    public void testRenderWithSelectAsTemplate() throws Exception {
        // select is rendered first (no data), then context.render(template)
        Object r = RenderEngine.build().renderJson("""
                {
                  "@class": "render",
                  "select": {"key": "{@val}"},
                  "data": {"val": 42}
                }
                """, Map.of());
        Assertions.assertTrue(r instanceof JSONObject);
        Assertions.assertEquals(42, ((JSONObject) r).getIntValue("key"));
    }
}
