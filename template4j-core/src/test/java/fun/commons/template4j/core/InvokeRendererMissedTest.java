package fun.commons.template4j.core;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

/**
 * Covers InvokeRenderer missed branches.
 */
public class InvokeRendererMissedTest {

    @Test
    public void testInvokeWithMethodAndSingleArg() throws Exception {
        // select + method + args (single non-collection value)
        Object r = RenderEngine.build().renderJson("""
                {
                  "@class": "invoke",
                  "select": "{= StringUtils }",
                  "method": "capitalize",
                  "args": "hello"
                }
                """, Map.of());
        Assertions.assertEquals(r, "Hello");
    }

    @Test
    public void testInvokeWithMethodAndListArgs() throws Exception {
        Object r = RenderEngine.build().renderJson("""
                {
                  "@class": "invoke",
                  "select": "{= StringUtils }",
                  "method": "firstNonBlank",
                  "args": [" ", null, "found"]
                }
                """, Map.of());
        Assertions.assertEquals(r, "found");
    }

    @Test
    public void testInvokeWithMethodAndNoArgs() throws Exception {
        // select + method but no args field — method called with no arguments
        Object r = RenderEngine.build().renderJson("""
                {
                  "@class": "invoke",
                  "select": "{= Math }",
                  "method": "random"
                }
                """, Map.of());
        Assertions.assertNotNull(r);
    }

    @Test
    public void testInvokeSelectAsScript() throws Exception {
        Object r = RenderEngine.build().renderJson("""
                {
                  "@class": "invoke",
                  "select": "Math.abs(-5)"
                }
                """, Map.of());
        Assertions.assertEquals(5, r);
    }

    @Test
    public void testInvokeNullSelect() throws Exception {
        Object r = RenderEngine.build().renderJson("""
                {
                  "@class": "invoke",
                  "select": "{@nonexistent}"
                }
                """, Map.of());
        Assertions.assertNull(r);
    }

    @Test
    public void testInvokeWithMethodAndCollectionArg() throws Exception {
        // args is a Collection — iterated and each rendered
        Object r = RenderEngine.build().renderJson("""
                {
                  "@class": "invoke",
                  "select": "{= StringUtils }",
                  "method": "join",
                  "args": ["a", "b", "c"]
                }
                """, Map.of());
        Assertions.assertNotNull(r);
    }
}
