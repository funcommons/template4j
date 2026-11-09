package fun.commons.template4j.core;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * 覆盖 SelfObject.getRoot() 和 path() 各分支。
 */
public class SelfObjectMoreTest {

    @Test
    public void testGetRootAtTop() throws Exception {
        // 通过 renderUrl 创建 root self (parent == null)
        TemplateLoader loader = url -> "{\"v\":\"{@name}\"}";
        RenderEngine e = RenderEngine.build().withLoader(loader);
        // top-level SelfObject.parent is null
        Object r = e.renderUrl("/x.json", java.util.Map.of("name", "alice"));
        Assertions.assertEquals(((com.alibaba.fastjson2.JSONObject) r).getString("v"), "alice");
    }

    @Test
    public void testGetRootDeep() throws Exception {
        // 嵌套结构触发 SelfObject.parent 链
        Object r = RenderEngine.build().renderJson(
                "{\"a\":{\"b\":{\"c\":\"{@name}\"}}}", java.util.Map.of("name", "deep"));
        Assertions.assertEquals(((com.alibaba.fastjson2.JSONObject)
                 ((com.alibaba.fastjson2.JSONObject)
                  ((com.alibaba.fastjson2.JSONObject) r).getJSONObject("a")).getJSONObject("b")).getString("c"), "deep");
    }

    @Test
    public void testPathWithParent() throws Exception {
        // 嵌套字段的 path() 应包含所有 key
        Object r = RenderEngine.build().renderJson(
                "{\"outer\":{\"inner\":\"{@name}\"}}", java.util.Map.of("name", "X"));
        Assertions.assertEquals(((com.alibaba.fastjson2.JSONObject)
                ((com.alibaba.fastjson2.JSONObject) r).getJSONObject("outer")).getString("inner"), "X");
    }

    @Test
    public void testPathTopLevelEmpty() throws Exception {
        // 顶层模板 path 为空
        Object r = RenderEngine.build().render("\"{@name}\"", java.util.Map.of("name", "x"));
        Assertions.assertEquals(r, "\"x\"");
    }

    @Test
    public void testGetResourceUrlInheritance() throws Exception {
        // 通过 renderUrl 设置 rootSelf.resourceUrl，子节点通过 parent 链继承
        TemplateLoader loader = url -> "{\"child\":{\"v\":\"{@name}\"}}";
        RenderEngine e = RenderEngine.build().withLoader(loader);
        Object r = e.renderUrl("/root.json", java.util.Map.of("name", "alice"));
        Assertions.assertEquals(((com.alibaba.fastjson2.JSONObject) r).getJSONObject("child").getString("v"), "alice");
    }
}
