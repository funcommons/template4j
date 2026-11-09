package fun.commons.template4j.utils;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import fun.commons.template4j.utils.UJson;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 覆盖本地 UJson 各方法，主要针对 template4j 实际使用的方法。
 */
public class UJsonTest {

    // ========== 类型检测 ==========

    @Test
    public void testIsJsonObject() {
        Assertions.assertTrue(UJson.isJsonObject("{\"a\":1}"));
        Assertions.assertTrue(UJson.isJsonObject("  {\"a\":1}  "));
        Assertions.assertFalse(UJson.isJsonObject("[1,2]"));
        Assertions.assertFalse(UJson.isJsonObject(""));
        Assertions.assertFalse(UJson.isJsonObject(null));
        Assertions.assertFalse(UJson.isJsonObject("plain text"));
        Assertions.assertFalse(UJson.isJsonObject("{\"a\":"));
    }

    @Test
    public void testIsJsonArray() {
        Assertions.assertTrue(UJson.isJsonArray("[1,2,3]"));
        Assertions.assertTrue(UJson.isJsonArray("  []  "));
        Assertions.assertFalse(UJson.isJsonArray("{\"a\":1}"));
        Assertions.assertFalse(UJson.isJsonArray(""));
        Assertions.assertFalse(UJson.isJsonArray(null));
    }

    @Test
    public void testIsJson() {
        Assertions.assertTrue(UJson.isJson("{\"a\":1}"));
        Assertions.assertTrue(UJson.isJson("[1]"));
        Assertions.assertFalse(UJson.isJson("abc"));
    }

    // ========== JSONFromYamlSafe / JSONFromYaml ==========

    @Test
    public void testJSONFromYamlSafeObject() {
        String yaml = "a: 1\nb: hello";
        Object result = UJson.JSONFromYamlSafe(yaml);
        Assertions.assertTrue(result instanceof JSONObject);
        Assertions.assertEquals(1, ((JSONObject) result).getIntValue("a"));
    }

    @Test
    public void testJSONFromYamlSafeArray() {
        String yaml = "- 1\n- 2\n- 3";
        Object result = UJson.JSONFromYamlSafe(yaml);
        Assertions.assertTrue(result instanceof JSONArray);
        Assertions.assertEquals(3, ((JSONArray) result).size());
    }

    @Test
    public void testJSONFromYamlSafeInvalid() {
        // 无效 YAML 返回 null 而非抛异常
        Assertions.assertNull(UJson.JSONFromYamlSafe("{invalid: yaml:"));
    }

    @Test
    public void testJSONFromYamlObject() throws Exception {
        String yaml = "a: 1\nb: hello";
        Object result = UJson.JSONFromYaml(yaml);
        Assertions.assertTrue(result instanceof JSONObject);
    }

    @Test
    public void testJSONFromYamlArray() throws Exception {
        String yaml = "- 1\n- 2";
        Object result = UJson.JSONFromYaml(yaml);
        Assertions.assertTrue(result instanceof JSONArray);
    }

    @Test
    public void testJSONFromYamlScalarThrows() {
        // 标量 YAML 会抛 IOException
        try {
            UJson.JSONFromYaml("just a string");
            Assertions.fail("scalar yaml should throw");
        } catch (Exception ex) {
            // expected
        }
    }

    @Test
    public void testJSONObjectFromYaml() throws Exception {
        JSONObject obj = UJson.JSONObjectFromYaml("a: 1\nb: 2");
        Assertions.assertEquals(1, obj.getIntValue("a"));
        Assertions.assertEquals(2, obj.getIntValue("b"));
    }

    @Test
    public void testJSONArrayFromYaml() throws Exception {
        JSONArray arr = UJson.JSONArrayFromYaml("- 1\n- 2\n- 3");
        Assertions.assertEquals(3, arr.size());
    }

    // ========== JSONFromJson ==========

    @Test
    public void testJSONFromJson() throws Exception {
        Object obj = UJson.JSONFromJson("{\"a\":1}");
        Assertions.assertTrue(obj instanceof JSONObject);

        Object arr = UJson.JSONFromJson("[1,2]");
        Assertions.assertTrue(arr instanceof JSONArray);
    }

    @Test
    public void testJSONFromJsonInvalidThrows() {
        try {
            UJson.JSONFromJson("\"just a string\"");
            Assertions.fail("non-object/array should throw");
        } catch (Exception ex) {
            // expected
        }
    }

    // ========== toJsonArray ==========

    @Test
    public void testToJsonArrayNull() {
        JSONArray arr = UJson.toJsonArray(null);
        Assertions.assertNotNull(arr);
        Assertions.assertEquals(0, arr.size());
    }

    @Test
    public void testToJsonArrayExisting() {
        JSONArray orig = JSONArray.of(1, 2, 3);
        JSONArray result = UJson.toJsonArray(orig);
        Assertions.assertSame(orig, result);
    }

    @Test
    public void testToJsonArrayCollection() {
        List<Integer> list = Arrays.asList(1, 2, 3);
        JSONArray arr = UJson.toJsonArray(list);
        Assertions.assertEquals(3, arr.size());
    }

    @Test
    public void testToJsonArrayObjectArray() {
        Object[] objs = {1, "two", 3.0};
        JSONArray arr = UJson.toJsonArray(objs);
        Assertions.assertEquals(3, arr.size());
    }

    @Test
    public void testToJsonArrayFromJsonString() {
        JSONArray arr = UJson.toJsonArray("[1,2,3]");
        Assertions.assertEquals(3, arr.size());
    }

    @Test
    public void testToJsonArraySingleValue() {
        JSONArray arr = UJson.toJsonArray(42);
        Assertions.assertEquals(1, arr.size());
        Assertions.assertEquals(42, arr.getIntValue(0));
    }

    // ========== toJsonObject ==========

    @Test
    public void testToJsonObjectNull() {
        JSONObject obj = UJson.toJsonObject(null);
        Assertions.assertNotNull(obj);
        Assertions.assertTrue(obj.isEmpty());
    }

    @Test
    public void testToJsonObjectExisting() {
        JSONObject orig = JSONObject.of("a", 1);
        JSONObject result = UJson.toJsonObject(orig);
        Assertions.assertEquals(1, result.getIntValue("a"));
    }

    @Test
    public void testToJsonObjectMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("k", "v");
        JSONObject obj = UJson.toJsonObject(map);
        Assertions.assertEquals(obj.getString("k"), "v");
    }

    @Test
    public void testToJsonObjectFromJsonString() {
        JSONObject obj = UJson.toJsonObject("{\"k\":\"v\"}");
        Assertions.assertEquals(obj.getString("k"), "v");
    }

    @Test
    public void testToJsonObjectFromPojo() {
        // 非 Map/String 会用 JSONObject.from() 反射
        Object bean = new Object() {
            public String name = "alice";
        };
        JSONObject obj = UJson.toJsonObject(bean);
        Assertions.assertEquals(obj.getString("name"), "alice");
    }

    // ========== toYAMLString / toPrettyJSONString / toWebJSONString ==========

    @Test
    public void testToYAMLString() throws Exception {
        JSONObject obj = JSONObject.of("a", 1, "b", "hello");
        String yaml = UJson.toYAMLString(obj);
        Assertions.assertNotNull(yaml);
        Assertions.assertTrue(yaml.contains("a"));
    }

    @Test
    public void testToPrettyJSONString() throws Exception {
        JSONObject obj = JSONObject.of("a", 1);
        String pretty = UJson.toPrettyJSONString(obj);
        Assertions.assertTrue(pretty.contains("\n"));
    }

    @Test
    public void testToWebJSONString() {
        JSONObject obj = JSONObject.of("a", "<script>");
        String web = UJson.toWebJSONString(obj);
        // BrowserSecure 转义特殊字符
        Assertions.assertNotNull(web);
    }

    // ========== copy / copyJSON / copyJSONObject / copyJSONArray ==========

    @Test
    public void testCopy() {
        JSONObject orig = JSONObject.of("a", 1);
        JSONObject copy = UJson.copy(orig);
        Assertions.assertEquals(1, copy.getIntValue("a"));
        Assertions.assertNotSame(orig, copy);
    }

    @Test
    public void testCopyJSON() {
        JSONObject orig = JSONObject.of("a", 1);
        JSONObject copy = UJson.copyJSON(orig);
        Assertions.assertEquals(1, copy.getIntValue("a"));
    }

    @Test
    public void testCopyJSONObject() {
        JSONObject orig = JSONObject.of("a", 1);
        JSONObject copy = UJson.copyJSONObject(orig);
        Assertions.assertEquals(1, copy.getIntValue("a"));
    }

    @Test
    public void testCopyJSONArray() {
        JSONArray orig = JSONArray.of(1, 2, 3);
        JSONArray copy = UJson.copyJSONArray(orig);
        Assertions.assertEquals(3, copy.size());
    }

    // ========== string2json ==========

    @Test
    public void testString2jsonJsonObject() throws Exception {
        Object result = UJson.string2json("{\"a\":1}");
        Assertions.assertTrue(result instanceof JSONObject);
    }

    @Test
    public void testString2jsonJsonArray() throws Exception {
        Object result = UJson.string2json("[1,2,3]");
        Assertions.assertTrue(result instanceof JSONArray);
    }

    @Test
    public void testString2jsonYaml() throws Exception {
        Object result = UJson.string2json("a: 1\nb: 2");
        Assertions.assertTrue(result instanceof JSONObject);
    }

    @Test
    public void testString2jsonInvalidThrows() {
        try {
            UJson.string2json("plain text not json or yaml at all !!!");
            Assertions.fail("invalid input should throw");
        } catch (Exception ex) {
            // expected
        }
    }

    // ========== findMap / findMapAll / findMapWithName ==========

    @Test
    public void testFindMapAll() {
        Map<String, Object> root = new LinkedHashMap<>();
        Map<String, Object> child = new LinkedHashMap<>();
        child.put("a", 1);
        root.put("child", child);

        List<Map<String, Object>> maps = UJson.findMapAll(root);
        Assertions.assertTrue(maps.size() >= 2);
    }

    @Test
    public void testFindMapWithName() {
        Map<String, Object> root = new LinkedHashMap<>();
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("id", 1);
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("id", 2);
        root.put("a", a);
        root.put("b", b);

        List<Map<String, Object>> maps = UJson.findMapWithName(root, "a");
        Assertions.assertEquals(1, maps.size());
        Assertions.assertEquals(1, maps.get(0).get("id"));
    }

    @Test
    public void testFindMapContainsKey() {
        Map<String, Object> root = new LinkedHashMap<>();
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", 1);
        item.put("name", "alice");
        root.put("item", item);

        List<Map<String, Object>> maps = UJson.findMapContainsKey(root, "id", "name");
        Assertions.assertEquals(1, maps.size());
    }

    @Test
    public void testFindMapContainsPath() {
        // 用 fastjson2 JSONPath 语法，简化测试
        Map<String, Object> root = new LinkedHashMap<>();
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("type", "product");
        root.put("item", item);

        List<Map<String, Object>> maps = UJson.findMapContainsPath(root, "$.type");
        Assertions.assertEquals(1, maps.size());
    }

    @Test
    public void testFindMapCustom() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("k", "v");
        List<Map<String, Object>> maps = UJson.findMap(root, (name, map) -> map.containsKey("k"));
        Assertions.assertTrue(maps.size() >= 1);
    }

    @Test
    public void testFindMapWithListOfMaps() {
        // 触发 findMap 的 List 分支
        List<Map<String, Object>> list = new ArrayList<>();
        list.add(Map.of("a", 1));
        list.add(Map.of("a", 2));
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("list", list);

        // findMapAll 返回所有 map（含 root 和 list 里的）
        List<Map<String, Object>> maps = UJson.findMapAll(root);
        Assertions.assertTrue(maps.size() >= 2);
    }

    @Test
    public void testFindMapWithNestedList() {
        // 嵌套 list 触发深度递归
        List<List<Object>> nested = new ArrayList<>();
        nested.add(List.of(Map.of("k", "v")));
        List<Map<String, Object>> maps = UJson.findMapAll(nested);
        Assertions.assertEquals(1, maps.size());
    }

    @Test
    public void testFindMapNonMatch() {
        // matcher 一直 false — 验证 false 分支
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("x", 1);
        List<Map<String, Object>> maps = UJson.findMap(root, (name, map) -> false);
        Assertions.assertEquals(0, maps.size());
    }

    @Test
    public void testFindMapMatchAll() {
        // matcher 一直 true — 收集所有 map
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("a", 1);
        root.put("b", 2);
        List<Map<String, Object>> maps = UJson.findMap(root, (name, map) -> true);
        Assertions.assertEquals(1, maps.size()); // root itself
    }

    // ========== mergeJson ==========

    @Test
    public void testMergeJsonSimple() {
        JSONObject from = JSONObject.of("a", 1, "b", 2);
        JSONObject to = JSONObject.of("b", 20, "c", 30);
        JSONObject merged = UJson.mergeJson(from, to);
        Assertions.assertEquals(1, merged.getIntValue("a"));
        Assertions.assertEquals(2, merged.getIntValue("b")); // from 覆盖
        Assertions.assertEquals(30, merged.getIntValue("c"));
    }

    @Test
    public void testMergeJsonNestedObject() {
        JSONObject from = JSONObject.of("nested", JSONObject.of("a", 1));
        JSONObject to = JSONObject.of("nested", JSONObject.of("b", 2));
        JSONObject merged = UJson.mergeJson(from, to);
        Assertions.assertEquals(1, merged.getJSONObject("nested").getIntValue("a"));
        Assertions.assertEquals(2, merged.getJSONObject("nested").getIntValue("b"));
    }

    @Test
    public void testMergeJsonArrayReplace() {
        JSONObject from = JSONObject.of("arr", JSONArray.of(1, 2));
        JSONObject to = JSONObject.of("arr", JSONArray.of(3, 4));
        JSONObject merged = UJson.mergeJson(from, to, false, false);
        // arrayFill=false 替换
        Assertions.assertEquals(2, merged.getJSONArray("arr").size());
        Assertions.assertEquals(1, merged.getJSONArray("arr").getIntValue(0));
    }

    @Test
    public void testMergeJsonArrayFill() {
        JSONObject from = JSONObject.of("arr", JSONArray.of(1, 2));
        JSONObject to = JSONObject.of("arr", JSONArray.of(3, 4));
        JSONObject merged = UJson.mergeJson(from, to, false, true);
        // arrayFill=true 合并去重
        Assertions.assertTrue(merged.getJSONArray("arr").size() >= 4);
    }

    @Test
    public void testMergeJsonMustExist() {
        JSONObject from = JSONObject.of("a", 1, "b", 2);
        JSONObject to = JSONObject.of("a", 10);
        JSONObject merged = UJson.mergeJson(from, to, true, false);
        // mustExist=true 只覆盖存在的 key
        Assertions.assertEquals(1, merged.size());
        Assertions.assertEquals(1, merged.getIntValue("a"));
    }

    @Test
    public void testMergeJsonNonJsonObjectFromJsonValue() {
        // to 是 JSONObject, from 不是 JSONObject (else 分支)
        JSONObject from = JSONObject.of("nested", 42);
        JSONObject to = JSONObject.of("nested", JSONObject.of("a", 1));
        JSONObject merged = UJson.mergeJson(from, to);
        // fromValue 不为 JSONObject，所以直接覆盖
        Assertions.assertEquals(42, merged.getIntValue("nested"));
    }

    @Test
    public void testMergeJsonNonArrayFromNonArrayValue() {
        // to 是 JSONArray, from 不是 JSONArray, arrayFill=true (else 分支)
        JSONObject from = JSONObject.of("arr", 99);
        JSONObject to = JSONObject.of("arr", JSONArray.of(1, 2));
        JSONObject merged = UJson.mergeJson(from, to, false, true);
        // 单个 value 添加到数组
        Assertions.assertEquals(3, merged.getJSONArray("arr").size());
    }

    @Test
    public void testMergeJsonNonArrayFromNonArrayValueNoFill() {
        // to 是 JSONArray, from 不是 JSONArray, arrayFill=false (else else 分支)
        JSONObject from = JSONObject.of("arr", 99);
        JSONObject to = JSONObject.of("arr", JSONArray.of(1, 2));
        JSONObject merged = UJson.mergeJson(from, to, false, false);
        // arrayFill=false → else 分支 → target.put(k, fromValue) → 替换为 99
        Assertions.assertEquals(99, merged.get("arr"));
    }

    // ========== smartCast ==========

    @Test
    public void testSmartCastJson() throws Exception {
        Object result = UJson.smartCast("{\"a\":1}", "json");
        Assertions.assertTrue(result instanceof JSONObject);
    }

    @Test
    public void testSmartCastJsonObject() throws Exception {
        Object result = UJson.smartCast("{\"a\":1}", "json-object");
        Assertions.assertTrue(result instanceof JSONObject);
    }

    @Test
    public void testSmartCastJsonArray() throws Exception {
        Object result = UJson.smartCast("[1,2]", "json-array");
        Assertions.assertTrue(result instanceof JSONArray);
    }

    @Test
    public void testSmartCastYaml() throws Exception {
        Object result = UJson.smartCast("a: 1\nb: 2", "yaml");
        Assertions.assertTrue(result instanceof JSONObject);
    }

    @Test
    public void testSmartCastYamlObject() throws Exception {
        Object result = UJson.smartCast("a: 1", "yaml-object");
        Assertions.assertTrue(result instanceof JSONObject);
    }

    @Test
    public void testSmartCastYamlArray() throws Exception {
        Object result = UJson.smartCast("- 1\n- 2", "yaml-array");
        Assertions.assertTrue(result instanceof JSONArray);
    }

    @Test
    public void testSmartCastString() throws Exception {
        Assertions.assertEquals(UJson.smartCast("hello", "string"), "hello");
        Assertions.assertEquals(UJson.smartCast("hello", "text"), "hello");
    }

    @Test
    public void testSmartCastAutoJson() throws Exception {
        Object result = UJson.smartCast("{\"a\":1}", "auto");
        Assertions.assertTrue(result instanceof JSONObject);
    }

    @Test
    public void testSmartCastAutoPlain() throws Exception {
        // 不是 json/yaml 时，auto 模式返回原字符串
        Assertions.assertEquals(UJson.smartCast("just text", "auto"), "just text");
    }

    @Test
    public void testSmartCastAutoBlank() throws Exception {
        // 空字符串既不是 json/yaml，auto 模式返回原字符串
        Assertions.assertEquals(UJson.smartCast("", "auto"), "");
    }

    @Test
    public void testSmartCastAutoJsonObject() throws Exception {
        Object result = UJson.smartCast("{\"k\":1}", "auto");
        Assertions.assertTrue(result instanceof JSONObject);
    }

    @Test
    public void testSmartCastAutoJsonArray() throws Exception {
        Object result = UJson.smartCast("[1,2,3]", "auto");
        Assertions.assertTrue(result instanceof JSONArray);
    }

    @Test
    public void testSmartCastUnsupported() {
        try {
            UJson.smartCast("x", "xml");
            Assertions.fail("xml should throw IllegalArgumentException");
        } catch (Exception ex) {
            // expected
        }
    }
}
