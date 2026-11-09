package fun.commons.template4j.core;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

/**
 * 覆盖 RenderEngine 全部便捷重载。
 *
 * 约定：
 * - render(String, Map)  → String（占位符替换后原样返回字符串）
 * - renderJson(String, Map) → JSONObject（先 JSON 解析，再渲染）
 * - renderYaml(String, Map) → JSONObject（先 YAML 解析）
 * - renderUrl(...) → 走 LoaderUtil
 */
public class RenderEngineOverloadTest {

    private final Map<String, Object> data = Map.of("name", "alice", "age", 30);

    @Test
    public void testRenderObjectMap() throws Exception {
        JSONObject t = JSONObject.parse("{\"x\":\"{@name}\"}");
        Object r = RenderEngine.build().render(t, data);
        Assertions.assertEquals(((JSONObject) r).getString("x"), "alice");
    }

    @Test
    public void testRenderObjectString() throws Exception {
        JSONObject t = JSONObject.parse("{\"x\":\"{@name}\"}");
        Object r = RenderEngine.build().render(t, "{\"name\":\"bob\"}");
        Assertions.assertEquals(((JSONObject) r).getString("x"), "bob");
    }

    @Test
    public void testRenderObjectObject() throws Exception {
        JSONObject t = JSONObject.parse("{\"x\":\"{@name}\"}");
        Object r = RenderEngine.build().render(t, (Object) Map.of("name", "carol"));
        Assertions.assertEquals(((JSONObject) r).getString("x"), "carol");
    }

    @Test
    public void testRenderStringMapReturnsString() throws Exception {
        // render(String, Map) 返回 String（带占位符替换的字符串）
        Object r = RenderEngine.build().render("{@name}", data);
        Assertions.assertEquals(r, "alice");
    }

    @Test
    public void testRenderStringStringJsonData() throws Exception {
        // render(String, String) 数据被自动解析（JSON / YAML）
        Object r = RenderEngine.build().render("{\"x\":\"{@name}\"}", "{\"name\":\"dan\"}");
        // String 模板返回 String，但 String 值会被嵌入到 JSONObject 形状时作为引号内容替换
        Assertions.assertTrue(r instanceof String);
        Assertions.assertTrue(r.toString().contains("dan"));
    }

    @Test
    public void testRenderStringStringYamlData() throws Exception {
        Object r = RenderEngine.build().render("x: '{@name}'", "name: eric");
        Assertions.assertTrue(r instanceof String);
        Assertions.assertTrue(r.toString().contains("eric"));
    }

    // ========== renderJson ==========
    @Test
    public void testRenderJsonMap() throws Exception {
        Object r = RenderEngine.build().renderJson("{\"x\":\"{@name}\"}", data);
        Assertions.assertEquals(((JSONObject) r).getString("x"), "alice");
    }

    @Test
    public void testRenderJsonString() throws Exception {
        Object r = RenderEngine.build().renderJson("{\"x\":\"{@name}\"}", "{\"name\":\"f\"}");
        Assertions.assertEquals(((JSONObject) r).getString("x"), "f");
    }

    @Test
    public void testRenderJsonObject() throws Exception {
        Object r = RenderEngine.build().renderJson("{\"x\":\"{@name}\"}", (Object) Map.of("name", "g"));
        Assertions.assertEquals(((JSONObject) r).getString("x"), "g");
    }

    @Test
    public void testRenderJsonInvalidTemplateThrows() {
        RenderEngine e = RenderEngine.build();
        try {
            e.renderJson("not a json", Map.of());
            Assertions.fail("invalid JSON 应抛 RenderException");
        } catch (Exception ex) {
            Assertions.assertTrue(ex.getMessage().contains("not json"));
        }
    }

    // ========== renderYaml ==========
    @Test
    public void testRenderYamlMap() throws Exception {
        Object r = RenderEngine.build().renderYaml("x: '{@name}'", data);
        Assertions.assertEquals(((JSONObject) r).getString("x"), "alice");
    }

    @Test
    public void testRenderYamlString() throws Exception {
        Object r = RenderEngine.build().renderYaml("x: '{@name}'", "name: h");
        Assertions.assertEquals(((JSONObject) r).getString("x"), "h");
    }

    @Test
    public void testRenderYamlObject() throws Exception {
        Object r = RenderEngine.build().renderYaml("x: '{@name}'", (Object) Map.of("name", "i"));
        Assertions.assertEquals(((JSONObject) r).getString("x"), "i");
    }

    @Test
    public void testRenderYamlInvalidThrows() {
        RenderEngine e = RenderEngine.build();
        try {
            e.renderYaml(":invalid::yaml", Map.of());
            Assertions.fail("invalid YAML 应抛 RenderException");
        } catch (Exception ex) {
            // 期望
        }
    }

    // ========== renderUrl ==========

    @Test
    public void testRenderUrlWithType() throws Exception {
        TemplateLoader loader = url -> "{\"y\":\"{@name}\"}";
        RenderEngine e = RenderEngine.build().withLoader(loader);
        Object r = e.renderUrl("/x.json", "json", data);
        Assertions.assertEquals(((JSONObject) r).getString("y"), "alice");
    }

    @Test
    public void testRenderUrlNoTypeByExtension() throws Exception {
        TemplateLoader loader = url -> "{\"y\":\"{@name}\"}";
        RenderEngine e = RenderEngine.build().withLoader(loader);
        Object r = e.renderUrl("/x.json", data);
        Assertions.assertEquals(((JSONObject) r).getString("y"), "alice");
    }

    @Test
    public void testRenderUrlYamlExtension() throws Exception {
        TemplateLoader loader = url -> "y: '{@name}'";
        RenderEngine e = RenderEngine.build().withLoader(loader);
        Object r = e.renderUrl("/x.yml", data);
        Assertions.assertEquals(((JSONObject) r).getString("y"), "alice");
    }

    @Test
    public void testRenderUrlStringData() throws Exception {
        TemplateLoader loader = url -> "{\"y\":\"{@name}\"}";
        RenderEngine e = RenderEngine.build().withLoader(loader);
        Object r = e.renderUrl("/x.json", "json", "{\"name\":\"j\"}");
        Assertions.assertEquals(((JSONObject) r).getString("y"), "j");
    }

    @Test
    public void testRenderUrlObjectData() throws Exception {
        TemplateLoader loader = url -> "{\"y\":\"{@name}\"}";
        RenderEngine e = RenderEngine.build().withLoader(loader);
        Object r = e.renderUrl("/x.json", "json", (Object) Map.of("name", "k"));
        Assertions.assertEquals(((JSONObject) r).getString("y"), "k");
    }

    @Test
    public void testRenderUrlStringDataString() throws Exception {
        TemplateLoader loader = url -> "{\"y\":\"{@name}\"}";
        RenderEngine e = RenderEngine.build().withLoader(loader);
        Object r = e.renderUrl("/x.json", "{\"name\":\"l\"}");
        Assertions.assertEquals(((JSONObject) r).getString("y"), "l");
    }

    @Test
    public void testRenderUrlObjectDataString() throws Exception {
        TemplateLoader loader = url -> "{\"y\":\"{@name}\"}";
        RenderEngine e = RenderEngine.build().withLoader(loader);
        Object r = e.renderUrl("/x.json", (Object) Map.of("name", "m"));
        Assertions.assertEquals(((JSONObject) r).getString("y"), "m");
    }

    // ========== renderJsonUrl / renderYamlUrl ==========

    @Test
    public void testRenderJsonUrlMap() throws Exception {
        TemplateLoader loader = url -> "{\"y\":\"{@name}\"}";
        RenderEngine e = RenderEngine.build().withLoader(loader);
        Assertions.assertEquals(((JSONObject) e.renderJsonUrl("/x", data)).getString("y"), "alice");
    }

    @Test
    public void testRenderJsonUrlString() throws Exception {
        TemplateLoader loader = url -> "{\"y\":\"{@name}\"}";
        RenderEngine e = RenderEngine.build().withLoader(loader);
        Assertions.assertEquals(((JSONObject) e.renderJsonUrl("/x", "{\"name\":\"n\"}")).getString("y"), "n");
    }

    @Test
    public void testRenderJsonUrlObject() throws Exception {
        TemplateLoader loader = url -> "{\"y\":\"{@name}\"}";
        RenderEngine e = RenderEngine.build().withLoader(loader);
        Assertions.assertEquals(((JSONObject) e.renderJsonUrl("/x", (Object) Map.of("name", "o"))).getString("y"), "o");
    }

    @Test
    public void testRenderYamlUrlMap() throws Exception {
        TemplateLoader loader = url -> "y: '{@name}'";
        RenderEngine e = RenderEngine.build().withLoader(loader);
        Assertions.assertEquals(((JSONObject) e.renderYamlUrl("/x", data)).getString("y"), "alice");
    }

    @Test
    public void testRenderYamlUrlString() throws Exception {
        TemplateLoader loader = url -> "y: '{@name}'";
        RenderEngine e = RenderEngine.build().withLoader(loader);
        Assertions.assertEquals(((JSONObject) e.renderYamlUrl("/x", "name: p")).getString("y"), "p");
    }

    @Test
    public void testRenderYamlUrlObject() throws Exception {
        TemplateLoader loader = url -> "y: '{@name}'";
        RenderEngine e = RenderEngine.build().withLoader(loader);
        Assertions.assertEquals(((JSONObject) e.renderYamlUrl("/x", (Object) Map.of("name", "q"))).getString("y"), "q");
    }

    // ========== withBeans ==========

    @Test
    public void testWithBeans() throws Exception {
        RenderEngine e = RenderEngine.build().withBeans(Map.of(
                "svc", new Object() { public String greet(String n) { return "hi " + n; } }
        ));
        Assertions.assertEquals(e.render("{% svc.greet(@name) %}", data), "hi alice");
    }

    // ========== 数据解析失败 ==========

    @Test
    public void testParseDataStringThrowsOnGarbage() {
        RenderEngine e = RenderEngine.build();
        try {
            e.render("{}", "this is not json and not yaml");
            Assertions.fail("不可解析的数据应抛 RenderException");
        } catch (Exception ex) {
            // 期望
        }
    }

    @Test
    public void testParseDataObjectConverts() throws Exception {
        // 任意 Object 通过 JSONObject.from 转 Map
        RenderEngine e = RenderEngine.build();
        Object r = e.renderJson("{\"x\":\"{@name}\"}", new JSONObject(Map.of("name", "r")));
        Assertions.assertEquals(((JSONObject) r).getString("x"), "r");
    }

    // ========== 数组 / 非对象模板 ==========

    @Test
    public void testRenderArrayTemplate() throws Exception {
        JSONArray arr = JSONArray.parse("[\"{@name}\",\"{@age}\"]");
        Object r = RenderEngine.build().render(arr, data);
        JSONArray out = (JSONArray) r;
        Assertions.assertEquals(out.get(0), "alice");
        Assertions.assertEquals(30, out.get(1));
    }
}