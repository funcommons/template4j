package fun.commons.template4j.core;

import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

/**
 * 覆盖 {@link FuncRender} / {@link FuncVars} / {@link FuncData}
 */
public class FuncRenderAndVarsTest {

    // ========== FuncRender ==========

    @Test
    public void testRenderInlineTemplate() throws Exception {
        // 通过字符串调用 render
        Object r = RenderEngine.build().renderJson(
                "{\"x\":\"{% render('hello') %}\"}", Map.of());
        JSONObject jo = (JSONObject) r;
        Assertions.assertEquals(jo.getString("x"), "hello");
    }

    @Test
    public void testRenderJsonObject() throws Exception {
        // render 一个 JSONObject 模板对象
        Object r = RenderEngine.build().renderJson(
                "{\"x\":\"{% render(@tpl) %}\"}",
                Map.of("tpl", JSONObject.parse("{\"k\":1}")));
        Assertions.assertTrue(r instanceof JSONObject);
    }

    @Test
    public void testRenderWithVar() throws Exception {
        Object r = RenderEngine.build().renderJson(
                "{\"x\":\"{% render('Hello, ' + @name) %}\"}", Map.of("name", "alice"));
        Assertions.assertEquals(((JSONObject) r).getString("x"), "Hello, alice");
    }

    // ========== FuncVars ==========

    @Test
    public void testVarsRead() throws Exception {
        Object r = RenderEngine.build().renderJson(
                "{\"$name\":\"{@name}\",\"out\":\"{% return vars.get('name') %}\"}",
                Map.of("name", "alice"));
        Assertions.assertEquals(((JSONObject) r).getString("out"), "alice");
    }

    @Test
    public void testVarsWrite() throws Exception {
        Object r = RenderEngine.build().renderJson(
                "{\"out\":\"{% vars.put('x', 42); return vars.get('x') %}\"}",
                Map.of());
        Assertions.assertEquals(42, ((JSONObject) r).getIntValue("out"));
    }

    @Test
    public void testVarsCallMissing() throws Exception {
        Object r = RenderEngine.build().renderJson(
                "{\"out\":\"{% return vars.get('nonexistent') %}\"}",
                Map.of());
        Assertions.assertNull(((JSONObject) r).get("out"));
    }

    @Test
    public void testVarsPropertyAccess() throws Exception {
        Object r = RenderEngine.build().renderJson(
                "{\"$name\":\"{@name}\",\"out\":\"{% return vars.name %}\"}",
                Map.of("name", "alice"));
        Assertions.assertEquals(((JSONObject) r).getString("out"), "alice");
    }

    @Test
    public void testVarsPutAt() throws Exception {
        Object r = RenderEngine.build().renderJson(
                "{\"out\":\"{% vars.put('x', 99); return vars.get('x') %}\"}",
                Map.of());
        Assertions.assertEquals(99, ((JSONObject) r).getIntValue("out"));
    }

    @Test
    public void testVarsRemove() throws Exception {
        // FuncVars 没有暴露 remove()，但 vars 直接返回的 FuncObject 实例上也没有 remove
        // 改为测试 vars.get 对未定义 key 返回 null
        Object r = RenderEngine.build().renderJson(
                "{\"out\":\"{% return vars.get('nonexistent') %}\"}",
                Map.of());
        Assertions.assertNull(((JSONObject) r).get("out"));
    }

    // ========== FuncData ==========

    @Test
    public void testDataCall() throws Exception {
        Object r = RenderEngine.build().render(
                "{% data(@p) %}", Map.of("p", "users[0].name",
                "users", Map.of(0, Map.of("name", "alice"))));
        Assertions.assertEquals(r, "alice");
    }

    @Test
    public void testDataCallNonExistent() throws Exception {
        Object r = RenderEngine.build().render(
                "{% data('nonexistent.path') %}", Map.of());
        Assertions.assertNull(r);
    }

    @Test
    public void testDataPropertyAccess() throws Exception {
        Object r = RenderEngine.build().render(
                "{% data.users[0].name %}", Map.of("users", Map.of(0, Map.of("name", "alice"))));
        Assertions.assertEquals(r, "alice");
    }
}