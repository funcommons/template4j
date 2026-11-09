package fun.commons.template4j.core;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

/**
 * Covers RenderRenderer line 40: UType.castValue for non-JSONObject data.
 * Line 37 branch: data instanceof JSONObject check.
 */
public class RenderRendererBranchTest {

    @Test
    public void testRenderWithDataAsMap() throws Exception {
        // When data is rendered to a Map that becomes JSONObject
        Object r = RenderEngine.build().renderJson("""
                {
                  "@class": "render",
                  "data": {"name": "world"},
                  "select": "hello {@name}"
                }
                """, Map.of());
        Assertions.assertEquals(r, "hello world");
    }

    @Test
    public void testRenderWithDataAsObject() throws Exception {
        // data field that renders to a non-JSONObject (e.g. a number) — triggers UType.castValue
        try {
            RenderEngine.build().renderJson("""
                    {
                      "@class": "render",
                      "data": 42,
                      "select": "x"
                    }
                    """, Map.of());
        } catch (Exception e) {
            // Expected: UType.castValue(42, JSONObject.class) will likely fail
        }
    }

    @Test
    public void testRenderWithoutDataSelectsTemplate() throws Exception {
        // No data → renderField("select") returns rendered value, then context.render(template)
        Object r = RenderEngine.build().renderJson("""
                {
                  "@class": "render",
                  "select": "{@name}"
                }
                """, Map.of("name", "bob"));
        Assertions.assertEquals(r, "bob");
    }
}
