package fun.commons.template4j.core;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * 覆盖 RenderContext.render 的 4 个重载。
 */
public class RenderContextRenderOverloadsTest {

    @Test
    public void testRenderWithSingleVar() throws Exception {
        // ctx.render(template, varName, varObject) — 2 变元
        RenderEngine e = RenderEngine.build().withComponent(new RenderComponent() {
            @Override public String name() { return "r1"; }
            @Override public Object render(RenderComponent.RenderContext ctx) throws Exception {
                // 在 render 中调用 ctx.render(template, varName, varObject)
                return ctx.render("hello {$name}", "name", "alice");
            }
        });
        Assertions.assertEquals(e.renderJson("{\"@class\":\"r1\"}", java.util.Map.of()), "hello alice");
    }

    @Test
    public void testRenderWithTwoVars() throws Exception {
        // ctx.render(template, varName1, varObject1, varName2, varObject2)
        RenderEngine e = RenderEngine.build().withComponent(new RenderComponent() {
            @Override public String name() { return "r2"; }
            @Override public Object render(RenderComponent.RenderContext ctx) throws Exception {
                return ctx.render("{$a}-{$b}", "a", "X", "b", "Y");
            }
        });
        Assertions.assertEquals(e.renderJson("{\"@class\":\"r2\"}", java.util.Map.of()), "X-Y");
    }

    @Test
    public void testRenderNoVars() throws Exception {
        // ctx.render(template) — 1 变元（无 var）
        RenderEngine e = RenderEngine.build().withComponent(new RenderComponent() {
            @Override public String name() { return "r3"; }
            @Override public Object render(RenderComponent.RenderContext ctx) throws Exception {
                return ctx.render("plain string");
            }
        });
        Assertions.assertEquals(e.renderJson("{\"@class\":\"r3\"}", java.util.Map.of()), "plain string");
    }

    @Test
    public void testRenderWithMap() throws Exception {
        // ctx.render(template, Map<String,Object>) — Map 形式
        RenderEngine e = RenderEngine.build().withComponent(new RenderComponent() {
            @Override public String name() { return "r4"; }
            @Override public Object render(RenderComponent.RenderContext ctx) throws Exception {
                java.util.Map<String, Object> vars = new java.util.HashMap<>();
                vars.put("k", "V");
                return ctx.render("k={$k}", vars);
            }
        });
        Assertions.assertEquals(e.renderJson("{\"@class\":\"r4\"}", java.util.Map.of()), "k=V");
    }
}
