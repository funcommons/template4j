package fun.commons.template4j.core;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

/**
 * Covers Lombok-generated getter methods on RenderComponent.RenderContext and FuncObject
 * that are not exercised through the normal render pipeline.
 */
public class LombokGetterCoverageTest {

    @Test
    public void testRenderContextGetName() throws Exception {
        RenderEngine e = RenderEngine.build().withComponent(new RenderComponent() {
            @Override public String name() { return "gname"; }
            @Override public Object render(RenderComponent.RenderContext ctx) throws Exception {
                Assertions.assertEquals(ctx.getName(), "gname");
                return "ok";
            }
        });
        e.renderJson("{\"@class\":\"gname\"}", Map.of());
    }

    @Test
    public void testRenderContextGetMap() throws Exception {
        RenderEngine e = RenderEngine.build().withComponent(new RenderComponent() {
            @Override public String name() { return "gmap"; }
            @Override public Object render(RenderComponent.RenderContext ctx) throws Exception {
                Assertions.assertNotNull(ctx.getMap());
                Assertions.assertTrue(ctx.getMap().containsKey("select"));
                return "ok";
            }
        });
        e.renderJson("{\"@class\":\"gmap\",\"select\":\"val\"}", Map.of());
    }

    @Test
    public void testRenderContextGetRenderer() throws Exception {
        RenderEngine e = RenderEngine.build().withComponent(new RenderComponent() {
            @Override public String name() { return "grenderer"; }
            @Override public Object render(RenderComponent.RenderContext ctx) throws Exception {
                Assertions.assertNotNull(ctx.getRenderer());
                return "ok";
            }
        });
        e.renderJson("{\"@class\":\"grenderer\"}", Map.of());
    }

    @Test
    public void testFuncObjectGetSelf() throws Exception {
        // FuncObject.getSelf() is a Lombok-generated getter not called by the render pipeline.
        // Exercise it by calling a $method() from Groovy, then accessing .self on the result.
        Object r = RenderEngine.build().renderJson("""
                {
                  "$hello()": "hi",
                  "out": "{= hello() }"
                }
                """, Map.of());
        Assertions.assertEquals(((com.alibaba.fastjson2.JSONObject) r).getString("out"), "hi");

        // Directly call getSelf() on a FuncObject instance
        FuncObject fo = new FuncObject("testFunc", "template");
        fo.setSelf(new SelfObject((JsonRenderer) RenderEngine.build(), Map.of()));
        Assertions.assertNotNull(fo.getSelf());
    }
}
