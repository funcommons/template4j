package fun.commons.template4j.core;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

/**
 * 覆盖 RenderBindings.get 中 vars 注入的分支。
 */
public class RenderBindingsVarsTest {

    @Test
    public void testVarsLookupViaBinding() throws Exception {
        // 通过 Groovy 脚本访问 vars.get() — RenderBindings.get 内部分支
        Object r = RenderEngine.build().renderJson("""
                {
                  "$myvar": "myvalue",
                  "out": "{= vars['myvar'] }"
                }
                """, Map.of());
        Assertions.assertEquals(((com.alibaba.fastjson2.JSONObject) r).getString("out"), "myvalue");
    }

    @Test
    public void testVarsLookupWithDollarPrefix() throws Exception {
        // vars['$xxx'] 应该去除 $ 前缀
        Object r = RenderEngine.build().renderJson("""
                {
                  "$myvar": "myvalue",
                  "out": "{= vars['$myvar'] }"
                }
                """, Map.of());
        Assertions.assertEquals(((com.alibaba.fastjson2.JSONObject) r).getString("out"), "myvalue");
    }

    @Test
    public void testVarsCallable() throws Exception {
        // vars 是 Callable — 通过 FuncObject.call 触发
        Object r = RenderEngine.build().renderJson("""
                {
                  "$myfunc()": "{% return 'computed' %}",
                  "out": "{=$myfunc()}"
                }
                """, Map.of());
        Assertions.assertNotNull(r);
    }

    @Test
    public void testVarsWithRestCall() throws Exception {
        // vars.foo 形式访问
        Object r = RenderEngine.build().renderJson("""
                {
                  "$x": "val",
                  "out": "{= vars['x'] }"
                }
                """, Map.of());
        Assertions.assertEquals(((com.alibaba.fastjson2.JSONObject) r).getString("out"), "val");
    }

    @Test
    public void testVarsWithNullValue() throws Exception {
        // vars['xxx'] 但变量存在且 value 是 null
        Object r = RenderEngine.build().renderJson("""
                {
                  "$xxx": null,
                  "out": "{= vars['xxx'] }"
                }
                """, Map.of());
        Assertions.assertNotNull(r);
    }

    @Test
    public void testVarsWithNumberValue() throws Exception {
        // vars['xxx'] 但变量是 Number
        Object r = RenderEngine.build().renderJson("""
                {
                  "$xxx": 42,
                  "out": "{= vars['xxx'] }"
                }
                """, Map.of());
        Assertions.assertEquals(42, ((com.alibaba.fastjson2.JSONObject) r).getIntValue("out"));
    }
}
