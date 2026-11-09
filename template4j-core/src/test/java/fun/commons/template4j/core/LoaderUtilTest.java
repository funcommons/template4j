package fun.commons.template4j.core;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;

/**
 * 覆盖 {@link LoaderUtil}：
 * - loadResource 三种回退（JSON / YAML / 纯文本）
 * - loadResource 后缀推断
 * - includeObject / includeArray / includeString
 * - &include 字符串与数组形式
 * - "...": "&include:..."
 */
public class LoaderUtilTest {

    @Test
    public void testLoadResourceJsonExtension() throws IOException {
        // 后缀 .json 应走 JSON 解析
        TemplateLoader loader = url -> """
                { "a": 1, "b": "x" }
                """;
        Object r = LoaderUtil.loadResource(loader, "classpath:/foo.json", null);
        Assertions.assertTrue(r instanceof JSONObject);
        Assertions.assertEquals(1, ((JSONObject) r).getIntValue("a"));
    }

    @Test
    public void testLoadResourceYamlExtension() throws IOException {
        TemplateLoader loader = url -> """
                a: 1
                b: hello
                """;
        Object r = LoaderUtil.loadResource(loader, "/foo.yml", null);
        Assertions.assertTrue(r instanceof JSONObject);
        Assertions.assertEquals(((JSONObject) r).getString("b"), "hello");
    }

    @Test
    public void testLoadResourceForceText() throws IOException {
        TemplateLoader loader = url -> "hello world";
        Object r = LoaderUtil.loadResource(loader, "/anything.unknown", "text");
        Assertions.assertEquals(r, "hello world");
    }

    @Test
    public void testLoadResourceFallbackJson() throws IOException {
        TemplateLoader loader = url -> """
                { "x": 1 }
                """;
        Object r = LoaderUtil.loadResource(loader, "/noext", null);
        Assertions.assertTrue(r instanceof JSONObject);
    }

    @Test
    public void testLoadResourceFallbackYaml() throws IOException {
        TemplateLoader loader = url -> """
                x: 1
                y: 2
                """;
        Object r = LoaderUtil.loadResource(loader, "/noext", null);
        Assertions.assertTrue(r instanceof JSONObject);
        Assertions.assertEquals(2, ((JSONObject) r).getIntValue("y"));
    }

    @Test
    public void testLoadResourceFallbackText() throws IOException {
        TemplateLoader loader = url -> "this is plain text\nwith newline";
        Object r = LoaderUtil.loadResource(loader, "/noext", null);
        Assertions.assertEquals(r, "this is plain text\nwith newline");
    }

    @Test
    public void testLoadResourceJsonObjectTemplate() throws IOException {
        TemplateLoader loader = url -> """
                { "name": "a" }
                """;
        JSONObject r = LoaderUtil.loadJSONObjectTemplate(loader, "/foo.json");
        Assertions.assertEquals(r.getString("name"), "a");
    }

    @Test
    public void testLoadJsonArrayTemplate() throws IOException {
        TemplateLoader loader = url -> """
                [1, 2, 3]
                """;
        JSONArray r = LoaderUtil.loadJSONArrayTemplate(loader, "/foo.json");
        Assertions.assertEquals(3, r.size());
    }

    // ========== &include 字符串形式 ==========

    @Test
    public void testIncludeStringForm() throws Exception {
        TemplateLoader loader = url -> {
            if (url.endsWith("partial.json")) return """
                    { "x": 1, "y": 2 }
                    """;
            return """
                    { "a": "&include:./partial.json" }
                    """;
        };
        RenderEngine e = RenderEngine.build().withLoader(loader);
        Object r = e.renderUrl("/main.json", Map.of());
        Assertions.assertTrue(r instanceof JSONObject);
        JSONObject jo = (JSONObject) r;
        // "a" 的值是 "&include:./partial.json"，被 LoaderUtil.includeString 替换为 partial.json 的内容
        Assertions.assertTrue(jo.containsKey("a"));
        Assertions.assertEquals(1, jo.getJSONObject("a").getIntValue("x"));
        Assertions.assertEquals(2, jo.getJSONObject("a").getIntValue("y"));
    }

    // ========== &include 对象形式（字符串值） ==========

    @Test
    public void testIncludeObjectStringValue() throws Exception {
        TemplateLoader loader = url -> {
            if (url.endsWith("header.json")) return """
                    { "h": "header-data" }
                    """;
            return """
                    {
                      "main": "value",
                      "&include": "./header.json"
                    }
                    """;
        };
        RenderEngine e = RenderEngine.build().withLoader(loader);
        Object r = e.renderUrl("/main.json", Map.of());
        JSONObject jo = (JSONObject) r;
        Assertions.assertEquals(jo.getString("main"), "value");
        Assertions.assertEquals(jo.getString("h"), "header-data");
    }

    @Test
    public void testIncludeObjectListValue() throws Exception {
        TemplateLoader loader = url -> {
            if (url.endsWith("a.json")) return """
                    { "a1": 1 }
                    """;
            if (url.endsWith("b.json")) return """
                    { "b1": 2 }
                    """;
            return """
                    {
                      "&include": ["./a.json", "./b.json"]
                    }
                    """;
        };
        RenderEngine e = RenderEngine.build().withLoader(loader);
        Object r = e.renderUrl("/main.json", Map.of());
        JSONObject jo = (JSONObject) r;
        Assertions.assertEquals(1, jo.getIntValue("a1"));
        Assertions.assertEquals(2, jo.getIntValue("b1"));
    }

    @Test
    public void testIncludeOverrideSameKey() throws Exception {
        TemplateLoader loader = url -> {
            if (url.endsWith("base.json")) return """
                    { "k": "base", "only_base": true }
                    """;
            if (url.endsWith("over.json")) return """
                    { "k": "over" }
                    """;
            return """
                    {
                      "k": "main",
                      "&include": ["./base.json", "./over.json"]
                    }
                    """;
        };
        RenderEngine e = RenderEngine.build().withLoader(loader);
        Object r = e.renderUrl("/main.json", Map.of());
        JSONObject jo = (JSONObject) r;
        // 后 include 覆盖前面的
        Assertions.assertEquals(jo.getString("k"), "over");
        Assertions.assertTrue(jo.getBoolean("only_base"));
    }

    @Test
    public void testRelativeUrlResolution() throws Exception {
        // 验证 UString.relativeUrl 在 include 时生效
        TemplateLoader loader = url -> {
            if (url.endsWith("/sub/inc.json")) return """
                    { "deep": "yes" }
                    """;
            return """
                    { "&include": "./sub/inc.json" }
                    """;
        };
        RenderEngine e = RenderEngine.build().withLoader(loader);
        Object r = e.renderUrl("/root/main.json", Map.of());
        JSONObject jo = (JSONObject) r;
        Assertions.assertEquals(jo.getString("deep"), "yes");
    }

    @Test
    public void testIncludeArrayInResult() throws Exception {
        // includeString 字符串值已被替换为对象/值
        TemplateLoader loader = url -> {
            if (url.endsWith("tpl.json")) return """
                    [ {"k":1}, {"k":2} ]
                    """;
            return """
                    {
                      "list": "&include:./tpl.json"
                    }
                    """;
        };
        RenderEngine e = RenderEngine.build().withLoader(loader);
        Object r = e.renderUrl("/main.json", Map.of());
        JSONObject jo = (JSONObject) r;
        Assertions.assertTrue(jo.get("list") instanceof JSONArray);
        Assertions.assertEquals(2, ((JSONArray) jo.get("list")).size());
    }
}