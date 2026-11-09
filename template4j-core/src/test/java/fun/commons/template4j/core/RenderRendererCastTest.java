package fun.commons.template4j.core;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

/**
 * Covers RenderRenderer line 40: UType.castValue for data that is rendered to a non-JSONObject type.
 * The else-branch at line 39-40 is reached when data is not instanceof JSONObject after rendering.
 */
public class RenderRendererCastTest {

    @Test
    public void testRenderWithCastableData() throws Exception {
        // data that becomes a non-JSONObject but can be cast to JSONObject via UType
        // e.g. a String that is valid JSON
        Object r = RenderEngine.build().renderJson("""
                {
                  "@class": "render",
                  "data": "{\\"name\\": \\"castable\\"}",
                  "select": "{@name}"
                }
                """, Map.of());
        Assertions.assertEquals(r, "castable");
    }

    @Test
    public void testRenderWithMapDataCast() throws Exception {
        // data that is a Map → cast to JSONObject
        Object r = RenderEngine.build().renderJson("""
                {
                  "@class": "render",
                  "data": {"key": "value"},
                  "select": "{@key}"
                }
                """, Map.of());
        Assertions.assertEquals(r, "value");
    }
}
