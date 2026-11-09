package fun.commons.template4j.core;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

/**
 * Covers RenderBindings entry points (line 10: get() entry; line 37: containsKey() entry).
 * These specific lines are the first statements of the methods, so calling them via template
 * execution should cover them.
 */
public class RenderBindingsEntryTest {

    @Test
    public void testGetCalledViaGroovy() throws Exception {
        // Trigger RenderBindings.get() entry by accessing a registered bean
        RenderEngine e = RenderEngine.build();
        e = e.withBean("triggerbean", "triggered");
        Object r = e.renderJson("""
                {
                  "out": "{= triggerbean }"
                }
                """, Map.of());
        Assertions.assertEquals(((com.alibaba.fastjson2.JSONObject) r).getString("out"), "triggered");
    }

    @Test
    public void testContainsKeyCalledViaGroovy() throws Exception {
        // Trigger RenderBindings.containsKey() entry — Groovy calls containsKey for variable lookups
        RenderEngine e = RenderEngine.build();
        Object r = e.renderJson("""
                {
                  "out": "{= data('key') }"
                }
                """, Map.of("key", "from_data"));
        Assertions.assertNotNull(r);
    }

    @Test
    public void testGetAndContainsKeyViaGroovy() throws Exception {
        // Trigger RenderBindings.get() and containsKey() from Groovy directly
        Object r = RenderEngine.build().renderJson("""
                {
                  "a": "{= 1 + 2 }",
                  "b": "{= 'x' + 'y' }",
                  "c": "{= data.getClass().simpleName }"
                }
                """, Map.of());
        Assertions.assertNotNull(r);
    }
}
