package fun.commons.template4j.core.components;

import com.alibaba.fastjson2.JSONObject;
import fun.commons.template4j.core.RenderEngine;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Coverage for {@link ConstRenderer}. The component is invoked via the
 * template4j-core API by setting {@code "@class": "const"} — it must return
 * the {@code select} value untouched (no rendering pass through convertSelf).
 */
public class ConstRendererTest {

    @Test
    public void constReturnsSelectUntouched_whenSelectIsJsonObject() throws Exception {
        Object r = RenderEngine.build().renderJson(
            "{\"@class\":\"const\",\"select\":{\"a\":{\"b\":\"{@hidden}\"}}}",
            Map.of("hidden", "should-not-substitute"));
        assertTrue(r instanceof JSONObject);
        assertEquals(((JSONObject) r).getJSONObject("a").getString("b"), "{@hidden}");
    }

    @Test
    public void constReturnsSelectUntouched_whenSelectIsString() throws Exception {
        Object r = RenderEngine.build().renderJson(
            "{\"@class\":\"const\",\"select\":\"{@x}\"}",
            Map.of("x", "substituted-or-not"));
        assertEquals(r, "{@x}");
    }

    @Test
    public void constReturnsSelect_whenSelectIsScalar() throws Exception {
        Object r = RenderEngine.build().renderJson(
            "{\"@class\":\"const\",\"select\":42}",
            Map.of());
        assertEquals(42, r);
    }

    @Test
    public void constRequiresSelectField() {
        Assertions.assertThrows(Exception.class, () ->
            RenderEngine.build().renderJson("{\"@class\":\"const\"}", Map.of()));
    }
}
