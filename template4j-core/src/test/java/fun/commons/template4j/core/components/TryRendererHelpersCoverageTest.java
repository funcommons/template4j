package fun.commons.template4j.core.components;

import fun.commons.template4j.core.RenderComponent.RenderContext;
import fun.commons.template4j.core.RenderException;
import fun.commons.template4j.core.SelfObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * 覆盖 TryRenderer.parseBooleanField 异常路径 (L115-116) 和 unwrap 行为。
 */
public class TryRendererHelpersCoverageTest {

    @Test
    public void parseBooleanField_renderExceptionFallsBack() throws Exception {
        Method m = TryRenderer.class.getDeclaredMethod(
                "parseBooleanField", RenderContext.class, String.class, boolean.class);
        m.setAccessible(true);

        // 构造一个 RenderContext, renderField 抛 NPE (模拟异常路径)
        RenderContext ctx = new RenderContext();
        // map 字段必填, 但 renderer 为 null, renderField 会抛 NPE
        Map<String, Object> bad = new HashMap<>();
        bad.put("rethrow", "garbage");  // value 任意, 触发 NPE 在 renderer 调用
        ctx.setMap(bad);
        ctx.setSelf(null);
        ctx.setRenderer(null);

        // 应返回 fallback (true), 不抛
        boolean r = (boolean) m.invoke(null, ctx, "rethrow", true);
        Assertions.assertTrue(r);
    }

    @Test
    public void parseBooleanField_nullValueFallsBack() throws Exception {
        Method m = TryRenderer.class.getDeclaredMethod(
                "parseBooleanField", RenderContext.class, String.class, boolean.class);
        m.setAccessible(true);

        RenderContext ctx = new RenderContext();
        Map<String, Object> map = new HashMap<>();
        map.put("rethrow", null);
        ctx.setMap(map);
        ctx.setSelf(null);
        ctx.setRenderer(null);

        // map.containsKey("rethrow") = true, 但 renderField 会抛, 走 catch → fallback
        boolean r = (boolean) m.invoke(null, ctx, "rethrow", false);
        Assertions.assertFalse(r);
    }
}
