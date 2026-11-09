package fun.commons.template4j.core;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HashMap;

/**
 * 覆盖 {@link FuncLoad}：脚本内 load(url)。
 *
 * 注意：UString.relativeUrl 会把相对路径解为 basename（丢目录），
 * 所以 loader 需要对裸 URL 也能命中。
 */
public class FuncLoadTest {

    @Test
    public void testLoadJsonObject() throws Exception {
        TemplateLoader loader = url -> {
            if (url.endsWith("main.jpage")) {
                return "{\"v\": \"{% load('./data.json') %}\"}";
            }
            // data.json 路径解析后只剩 basename
            if (url.endsWith("data.json") || url.equals("data.json")) {
                return "{\"a\": 1, \"b\": \"x\"}";
            }
            return "{}";
        };
        RenderEngine e = RenderEngine.build().withLoader(loader);
        Object r = e.renderUrl("/main.jpage", new HashMap<>());
        Assertions.assertTrue(r instanceof JSONObject);
        JSONObject jo = (JSONObject) r;
        Assertions.assertEquals(1, jo.getJSONObject("v").getIntValue("a"));
        Assertions.assertEquals(jo.getJSONObject("v").getString("b"), "x");
    }

    @Test
    public void testLoadJsonArray() throws Exception {
        TemplateLoader loader = url -> {
            if (url.endsWith("main.jpage")) {
                return "{\"v\": \"{% load('./list.json') %}\"}";
            }
            if (url.endsWith("list.json") || url.equals("list.json")) {
                return "[1, 2, 3]";
            }
            return "[]";
        };
        RenderEngine e = RenderEngine.build().withLoader(loader);
        Object r = e.renderUrl("/main.jpage", new HashMap<>());
        JSONObject jo = (JSONObject) r;
        Assertions.assertTrue(jo.get("v") instanceof JSONArray);
        Assertions.assertEquals(3, ((JSONArray) jo.get("v")).size());
    }

    @Test
    public void testLoadNestedInclude() throws Exception {
        // 注：load() 返回原始 JSON 值，不会触发 &include 展开（&include 仅作用于 renderUrl）
        TemplateLoader loader = url -> {
            if (url.endsWith("main.jpage")) {
                return "{\"v\": \"{% load('./a.json') %}\"}";
            }
            if (url.endsWith("a.json") || url.equals("a.json")) {
                return "{\"a1\": 1}";
            }
            return "{}";
        };
        RenderEngine e = RenderEngine.build().withLoader(loader);
        Object r = e.renderUrl("/main.jpage", new HashMap<>());
        JSONObject jo = (JSONObject) r;
        Assertions.assertEquals(1, jo.getJSONObject("v").getIntValue("a1"));
    }
}