package fun.commons.template4j.core;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

/**
 * 覆盖 RenderRenderer 各分支。
 */
public class RenderRendererTest {

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
    public void testRenderWithNonJsonObjectData() {
        // data 是 String (非 JSONObject) — 触发 UType.castValue
        try {
            RenderEngine.build().renderJson("""
                    {
                      "@class": "render",
                      "data": "a string",
                      "select": "x"
                    }
                    """, Map.of());
        } catch (Exception ex) {
            // expected (castValue fails to convert String to JSONObject)
        }
    }

    @Test
    public void testRenderWithoutData() throws Exception {
        // 没有 data — 走 context.render(template) 分支
        Object r = RenderEngine.build().renderJson("""
                {
                  "@class": "render",
                  "select": "{@name}"
                }
                """, Map.of("name", "bob"));
        Assertions.assertEquals(r, "bob");
    }
}
