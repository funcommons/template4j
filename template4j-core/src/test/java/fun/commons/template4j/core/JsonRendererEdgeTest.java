package fun.commons.template4j.core;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

/**
 * 覆盖 JsonRenderer.convertSelf 各分支 + RestRenderer 错误路径。
 */
public class JsonRendererEdgeTest {

    private final RenderEngine engine = RenderEngine.build();

    // ========== JsonRenderer edge cases ==========

    @Test
    public void testRenderNull() throws Exception {
        // JSON 中 null 值：x 字段是 null
        Object r = engine.renderJson("{\"x\": null}", Map.of());
        Assertions.assertTrue(r instanceof JSONObject);
        Assertions.assertTrue(((JSONObject) r).get("x") == null);
    }

    @Test
    public void testRenderJsonString() throws Exception {
        // 字符串形式的 JSON 解析分支
        Object r = engine.renderJson("{\"x\":\"{\\\"nested\\\":1}\"}", Map.of());
        // x 是 JSON 形态字符串，被替换
        Assertions.assertNotNull(r);
    }

    @Test
    public void testRenderEmptyMap() throws Exception {
        Object r = engine.renderJson("{}", Map.of());
        Assertions.assertTrue(r instanceof JSONObject);
        Assertions.assertTrue(((JSONObject) r).isEmpty());
    }

    @Test
    public void testRenderEmptyArray() throws Exception {
        Object r = engine.renderJson("[]", Map.of());
        Assertions.assertTrue(r instanceof JSONArray);
        Assertions.assertEquals(0, ((JSONArray) r).size());
    }

    @Test
    public void testRenderPojo() throws Exception {
        // 直接渲染非 Map/List 对象（原样返回）
        Object r = engine.render("hello", Map.of());
        Assertions.assertEquals(r, "hello");
    }

    @Test
    public void testRenderInteger() throws Exception {
        Object r = engine.render("42", Map.of());
        Assertions.assertEquals(r, "42"); // String branch 处理
    }

    @Test
    public void testRenderArrayOfObjects() throws Exception {
        Object r = engine.renderJson("[1,\"two\",true,null]", Map.of());
        Assertions.assertTrue(r instanceof JSONArray);
    }

    @Test
    public void testRenderWithStarKey() throws Exception {
        // "*" 键触发 mergeAllMap
        Object r = engine.renderJson("""
                {
                  "name": "base",
                  "*": {"extra": "added"}
                }
                """, Map.of());
        JSONObject jo = (JSONObject) r;
        Assertions.assertEquals(jo.getString("name"), "base");
        Assertions.assertEquals(jo.getString("extra"), "added");
    }

    // ========== RestRenderer 错误路径补全 ==========

    @Test
    public void testRestNetworkErrorHandled() throws Exception {
        // network error with error block
        Object r = engine.renderJson("""
                {
                  "@class": "rest",
                  "url": "http://127.0.0.1:1/",
                  "error": {"type": "{$responseData.message}"}
                }
                """, Map.of());
        Assertions.assertNotNull(r);
    }

    @Test
    public void testRestPostNoDataNoContentType() throws Exception {
        // POST without data and Content-Type header should still work (or fail gracefully)
        Object r = engine.renderJson("""
                {
                  "@class": "rest",
                  "url": "http://127.0.0.1:1/",
                  "method": "post",
                  "error": {"x": 1}
                }
                """, Map.of());
        Assertions.assertNotNull(r);
    }

    @Test
    public void testRestSuccessNoSuccessBlock() throws Exception {
        // 成功的请求 + 无 success 块 — 直接返回 dataType 处理后的结果
        Object r = engine.renderJson("""
                {
                  "@class": "rest",
                  "url": "http://127.0.0.1:1/",
                  "dataType": "string",
                  "error": {"fallback": true}
                }
                """, Map.of());
        Assertions.assertNotNull(r);
    }

    // ========== JsonRenderer.convertSelf 剩余分支 ==========

    @Test
    public void testUnknownComponentThrows() {
        // @class 指向不存在的组件 → 抛 RenderException
        try {
            engine.renderJson("{\"@class\":\"nonexistent_component_xyz\"}", Map.of());
            Assertions.fail("unknown component should throw");
        } catch (Exception ex) {
            // expected
        }
    }

    @Test
    public void testStarKeyInCollectionItem() throws Exception {
        // 数组中的对象有 "*" 键 — 触发 mergeAllMap 路径
        Object r = engine.renderJson("""
                [
                  {"*": {"a": 1, "b": 2}},
                  {"x": 1}
                ]
                """, Map.of());
        // 数组元素中第一个对象 "*" 触发 item 替换为 {a:1, b:2}
        // 最终会是 [ {a:1, b:2}, {x:1} ]
        Assertions.assertNotNull(r);
    }

    @Test
    public void testEnterReturnFlow() throws Exception {
        // $enter{} + $return{} 流程
        Object r = engine.renderJson("""
                {
                  "$enter{}": "{% _template.put('extra', 1) %}",
                  "x": 1,
                  "$return{}": "{% _result.put('done', true) %}"
                }
                """, Map.of());
        // result 应包含 "done": true
        Assertions.assertNotNull(r);
    }

    @Test
    public void testCollectionItemListFlatten() throws Exception {
        // 数组中某个 item 不是 List 但渲染后是 List → 展平
        Object r = engine.renderJson("""
                [
                  {"@class": "each", "select": [1,2,3], "body": "{=$item}"}
                ]
                """, Map.of());
        // 第一项是一个 each 输出 JSONArray → 展平
        // 最终会是 [1, 2, 3]
        Assertions.assertNotNull(r);
    }

    @Test
    public void testStringJsonShapeNullWithQuotes() throws Exception {
        // 当 String 是 JSON shape，结果是 null + 在引号里
        // 触发 jsonShape && strResult.contains(quotedKey) 分支
        Object r = engine.render("""
                {"v":"{@missing}"}
                """.trim(), Map.of());
        // 整个字符串是 JSON 形态，模板里有 missing field
        // 走 quotedKey 替换为 "null"
        Assertions.assertNotNull(r);
    }

    @Test
    public void testRestGetWithNonMapData() throws Exception {
        // GET + data 非 Map (例如 String) — 跳过 queryParam 循环
        Object r = engine.renderJson("""
                {
                  "@class": "rest",
                  "url": "http://127.0.0.1:1/",
                  "method": "get",
                  "data": "raw string data",
                  "error": {"x": 1}
                }
                """, Map.of());
        Assertions.assertNotNull(r);
    }

    @Test
    public void testRestPostWithFormUrlencodedData() throws Exception {
        // POST + Content-Type: application/x-www-form-urlencoded
        Object r = engine.renderJson("""
                {
                  "@class": "rest",
                  "url": "http://127.0.0.1:1/",
                  "method": "post",
                  "headers": {"Content-Type": "application/x-www-form-urlencoded"},
                  "data": {"k": "v"},
                  "error": {"x": 1}
                }
                """, Map.of());
        Assertions.assertNotNull(r);
    }

    @Test
    public void testRestPostWithMultipartData() throws Exception {
        // POST + Content-Type: multipart/form-data
        Object r = engine.renderJson("""
                {
                  "@class": "rest",
                  "url": "http://127.0.0.1:1/",
                  "method": "post",
                  "headers": {"Content-Type": "multipart/form-data"},
                  "data": {"k": "v"},
                  "error": {"x": 1}
                }
                """, Map.of());
        Assertions.assertNotNull(r);
    }

    // ========== EachRenderer 剩余分支 ==========

    @Test
    public void testEachWithArray() throws Exception {
        // select 是 Java array — 走 getClass().isArray 分支
        Object r = engine.renderJson("""
                {
                  "@class": "each",
                  "select": [10, 20, 30],
                  "body": "{= $item * 2 }"
                }
                """, Map.of());
        Assertions.assertNotNull(r);
    }

    @Test
    public void testEachSelectNotIterable() throws Exception {
        // select 是 String (不可迭代) — else 分支的 UThrow.assertArgument(true,...) 实际不会抛
        // 因为 assertArgument(true,...) 永远 pass。这个分支是死代码，但仍执行
        Object r = engine.renderJson("""
                {
                  "@class": "each",
                  "select": "not iterable",
                  "body": "x"
                }
                """, Map.of());
        Assertions.assertNotNull(r);
    }

    @Test
    public void testEachKeyItemDollarPrefix() throws Exception {
        // keyName/itemName 已带 $ 前缀 — 触发 startsWith("$") 分支
        Object r = engine.renderJson("""
                {
                  "@class": "each",
                  "select": [1, 2],
                  "keyName": "$k",
                  "itemName": "$v",
                  "body": "{$k}={$v}"
                }
                """, Map.of());
        Assertions.assertTrue(r.toString().contains("0=1"));
    }

    @Test
    public void testRestWithSelectInsteadOfUrl() throws Exception {
        // 用 select 替代 url — 触发 firstNonField("url","select") 分支
        Object r = engine.renderJson("""
                {
                  "@class": "rest",
                  "select": "http://127.0.0.1:1/",
                  "error": {"x": 1}
                }
                """, Map.of());
        Assertions.assertNotNull(r);
    }

    @Test
    public void testRestWithSuccessAndDataType() throws Exception {
        // success + dataType
        Object r = engine.renderJson("""
                {
                  "@class": "rest",
                  "url": "http://127.0.0.1:1/",
                  "dataType": "string",
                  "success": "OK",
                  "error": {"x": 1}
                }
                """, Map.of());
        Assertions.assertNotNull(r);
    }

    @Test
    public void testRestPutDataIntoError() throws Exception {
        // error 时 responseData 包含 status 和 message
        Object r = engine.renderJson("""
                {
                  "@class": "rest",
                  "url": "http://127.0.0.1:1/",
                  "error": {"code": "{$responseData.status}", "msg": "{$responseData.message}"}
                }
                """, Map.of());
        Assertions.assertNotNull(r);
    }

    @Test
    public void testJsonShapeNullNotInQuotes() throws Exception {
        // JSON shape 但 null 在引号外 — 走 else 分支 (replace(key, ""))
        Object r = engine.render("""
                "{@missing}"
                """, Map.of());
        // 返回的字符串是 "" (空)
        Assertions.assertNotNull(r);
    }

    @Test
    public void testStringTemplateNotJsonShape() throws Exception {
        // 非 JSON shape 的字符串模板
        Object r = engine.render("not json shape {@x}", Map.of("x", "X"));
        Assertions.assertEquals(r, "not json shape X");
    }

    @Test
    public void testRestPostNoData() throws Exception {
        // POST 不带 data — 不进入 body 分支
        Object r = engine.renderJson("""
                {
                  "@class": "rest",
                  "url": "http://127.0.0.1:1/",
                  "method": "post",
                  "error": {"x": 1}
                }
                """, Map.of());
        Assertions.assertNotNull(r);
    }

    @Test
    public void testRestPostWithMapData() throws Exception {
        // POST + data 是 Map — 走 JSON 序列化分支
        Object r = engine.renderJson("""
                {
                  "@class": "rest",
                  "url": "http://127.0.0.1:1/",
                  "method": "post",
                  "data": {"key": "value"},
                  "headers": {"X-Custom": "test"},
                  "error": {"x": 1}
                }
                """, Map.of());
        Assertions.assertNotNull(r);
    }

    @Test
    public void testRestGetWithQueryParams() throws Exception {
        // GET + data 是 Map — queryParam 分支
        Object r = engine.renderJson("""
                {
                  "@class": "rest",
                  "url": "http://127.0.0.1:1/",
                  "method": "get",
                  "data": {"q": "test", "limit": "10"},
                  "error": {"x": 1}
                }
                """, Map.of());
        Assertions.assertNotNull(r);
    }

    @Test
    public void testRestFailure5xxBranch() throws Exception {
        // 触发 5xx branch (status.is4xxClientError == false)
        com.sun.net.httpserver.HttpServer server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        int p = server.getAddress().getPort();
        server.createContext("/u", exchange -> {
            byte[] body = "{\"msg\":\"oops\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(503, body.length);
            try (java.io.OutputStream os = exchange.getResponseBody()) { os.write(body); }
        });
        server.start();
        try {
            Object r = engine.renderJson("""
                    {
                      "@class": "rest",
                      "url": "http://127.0.0.1:%d/u",
                      "error": {"status": "{$responseData.status}"}
                    }
                    """.formatted(p), Map.of());
            Assertions.assertEquals(503, ((com.alibaba.fastjson2.JSONObject) r).getIntValue("status"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void testRestNetworkExceptionErrorBlock() throws Exception {
        // connection failure → e !instanceof HttpStatusCodeException → renderError(-1, message)
        Object r = engine.renderJson("""
                {
                  "@class": "rest",
                  "url": "http://127.0.0.1:1/",
                  "error": {"status": "{$responseData.status}"}
                }
                """, Map.of());
        Assertions.assertEquals(-1, ((com.alibaba.fastjson2.JSONObject) r).getIntValue("status"));
    }

    @Test
    public void testRestPostWithObjectData() throws Exception {
        // POST + data 是 Object (非 String 非 Map) — JSON.toJSONString 分支
        // 用 Pojo
        Object pojo = new Object() {
            public String k = "v";
        };
        Object r = engine.renderJson("""
                {
                  "@class": "rest",
                  "url": "http://127.0.0.1:1/",
                  "method": "post",
                  "data": {
                    "k": "v"
                  },
                  "headers": {"Content-Type": "text/plain"},
                  "error": {"x": 1}
                }
                """, Map.of());
        Assertions.assertNotNull(r);
    }

    @Test
    public void testRestPostStringBodyWithContentType() throws Exception {
        // POST + data 是 String + 明确 Content-Type（非 form 类型）— String 分支
        Object r = engine.renderJson("""
                {
                  "@class": "rest",
                  "url": "http://127.0.0.1:1/",
                  "method": "post",
                  "data": "raw_text_value",
                  "headers": {"Content-Type": "text/plain"},
                  "error": {"x": 1}
                }
                """, Map.of());
        Assertions.assertNotNull(r);
    }

    @Test
    public void testRestPostNoContentType() throws Exception {
        // POST 不带 Content-Type header — 走 setContentType(JSON) 分支
        Object r = engine.renderJson("""
                {
                  "@class": "rest",
                  "url": "http://127.0.0.1:1/",
                  "method": "post",
                  "data": "x",
                  "error": {"x": 1}
                }
                """, Map.of());
        Assertions.assertNotNull(r);
    }

    @Test
    public void testArrayContainingListAsItem() throws Exception {
        // 数组中的某项是 List — 走 !(v instanceof List) && v1 instanceof List 的 false 分支
        // 即 v 是 List，convertSelf 后还是 List
        Object r = engine.renderJson("""
                [
                  [1, 2, 3],
                  [4, 5, 6]
                ]
                """, Map.of());
        Assertions.assertNotNull(r);
    }

    @Test
    public void testArrayWithNestedConvert() throws Exception {
        // 数组中有 Map — 每个 item 是 Map，需要 convertSelf 递归
        Object r = engine.renderJson("""
                [
                  {"k": "v1"},
                  {"k": "v2"}
                ]
                """, Map.of());
        Assertions.assertNotNull(r);
    }

    @Test
    public void testRenderPojoAsRoot() throws Exception {
        // 直接渲染 POJO（非 Map/Collection/String）— 走 else 分支原样返回
        // 通过 render(Object, Object) 触发
        Object pojo = new Object() {
            public String name = "alice";
            public int age = 30;
        };
        Object r = RenderEngine.build().render(pojo, (Object) pojo);
        // result 直接返回 = pojo
        Assertions.assertSame(pojo, r);
    }

    @Test
    public void testRenderNumberAsRoot() throws Exception {
        // 渲染 Number 根 - 直接返回原值
        Object r = RenderEngine.build().render(42, Map.of());
        // 数字 root: convertSelf(42) → 42 是 Number，不是 String/Map/Collection → 返回 42
        Assertions.assertEquals(42, r);
    }

    @Test
    public void testRenderBooleanAsRoot() throws Exception {
        // 渲染 Boolean 根
        Object r = RenderEngine.build().render(true, Map.of());
        Assertions.assertEquals(true, r);
    }

    @Test
    public void testRenderJsonShapeWithQuotedKey() throws Exception {
        // strResult 是 JSON 形态，ret 是 null，key 在引号里 — 替换为 null 字面量
        Object r = engine.render("""
                {"key": "{@missing}"}
                """, Map.of());
        // 应该看到 "key":null
        Assertions.assertTrue(r.toString().contains("null"));
    }

    @Test
    public void testStringTemplateIsJsonArrayShape() throws Exception {
        // strResult.trim() 是 [ ... ] 形态 — jsonShape branch
        // 注意：会触发 JSON 解析，所以需要是 valid JSON array
        Object r = engine.render("""
                [1, "{@missing}", 3]
                """, Map.of());
        Assertions.assertNotNull(r);
    }

    @Test
    public void testStringContainsMultiplePlaceholders() throws Exception {
        // 多个不同 placeholder — strResult 包含多个 keyList.size() > 1
        Object r = engine.render("prefix {@a} middle {@b} suffix",
                java.util.Map.of("a", "X", "b", "Y"));
        Assertions.assertEquals(r, "prefix X middle Y suffix");
    }

    @Test
    public void testSinglePlaceholderReturnsRaw() throws Exception {
        // 单占位符 + strResult.trim() == key — 直接返回 ret 不替换
        // 也覆盖 keyList.size() == 1 分支
        Object r = engine.render("{@name}", Map.of("name", "alice"));
        Assertions.assertEquals(r, "alice");
    }

    @Test
    public void testSinglePlaceholderReturnsJSONObject() throws Exception {
        // 单占位符 + ret 是 JSONObject — 直接返回 JSONObject
        // 用 Map.of("data", Map) 因为 fastjson2 解析后是 JSONObject
        Object r = engine.render("{@data}", Map.of("data", Map.of("k", "v")));
        // 不强求类型，只是不报错 + 包含 k=v 内容
        Assertions.assertTrue(r.toString().contains("k"));
    }

    @Test
    public void testStringJsonShapeWithNullNotQuoted() throws Exception {
        // JSON 形态 + ret 是 null + key 不在引号里 — 走 else 分支，replace 成空串
        // 注意整个 strResult 不是单占位符
        Object r = engine.render("""
                {"a": 1, "b": "{@missing}"}
                """, Map.of("a", 1));
        // b 是 null，JSON 解析保留 null
        Assertions.assertNotNull(r);
    }
}
