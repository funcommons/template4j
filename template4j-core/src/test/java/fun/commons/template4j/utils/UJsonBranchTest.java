package fun.commons.template4j.utils;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import fun.commons.template4j.utils.UJson;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Covers missed branches in UJson:
 * - isJsonArray: string starts with [ but does not end with ] (or vice versa)
 * - toJsonArray: String that is not a JSON array falls through to single-value wrap
 * - toJsonObject: String that is not a JSON object falls through; non-String non-Map non-JSONObject uses JSONObject.from
 * - findMap: List containing non-Map non-List items (skipped)
 * - smartCast: auto mode with JSON array; auto mode with valid YAML; auto mode with null type
 * - mergeJson: mustExist=true and key not in target (skipped); arrayFill=true with JSONArray from and non-JSONArray to where value already present
 * - findMapWithName: no name matches (lambda returns false)
 */
public class UJsonBranchTest {

    // ========== isJsonArray: starts with [ but invalid JSON ==========

    @Test
    public void testIsJsonArrayStartsBracketButInvalid() {
        // Starts with [ but does not end with ] — hits the return false at line 55
        Assertions.assertFalse(UJson.isJsonArray("[1,2,3"));
        // Ends with ] but does not start with [
        Assertions.assertFalse(UJson.isJsonArray("1,2,3]"));
        // Starts with [ and ends with ] but invalid JSON inside
        Assertions.assertFalse(UJson.isJsonArray("[invalid json here]"));
    }

    // ========== toJsonArray: String that is not a JSON array ==========

    @Test
    public void testToJsonArrayNonJsonString() {
        // String that is not a JSON array falls through to array.add(value)
        JSONArray arr = UJson.toJsonArray("hello");
        Assertions.assertEquals(1, arr.size());
        Assertions.assertEquals(arr.getString(0), "hello");
    }

    @Test
    public void testToJsonArrayJsonObjectString() {
        // A JSON object string is not a JSON array, so it wraps as single element
        JSONArray arr = UJson.toJsonArray("{\"a\":1}");
        Assertions.assertEquals(1, arr.size());
    }

    // ========== toJsonObject: String that is not a JSON object ==========

    @Test
    public void testToJsonObjectNonJsonString() {
        // String that is not a JSON object — isJsonObject returns false,
        // so it does NOT parse, returns empty JSONObject
        JSONObject obj = UJson.toJsonObject("not json");
        Assertions.assertTrue(obj.isEmpty());
    }

    @Test
    public void testToJsonObjectJsonArrayString() {
        // A JSON array string is not a JSON object — returns empty JSONObject
        JSONObject obj = UJson.toJsonObject("[1,2,3]");
        Assertions.assertTrue(obj.isEmpty());
    }

    @Test
    public void testToJsonObjectNonStringNonMapNonJsonObject() {
        // Non-String, non-Map, non-JSONObject — hits the else branch: JSONObject.from(value)
        // Using a simple POJO-like object
        Object bean = new Object() {
            public String name = "test";
        };
        JSONObject obj = UJson.toJsonObject(bean);
        Assertions.assertEquals(obj.getString("name"), "test");
    }


    // ========== findMap: List with non-Map non-List items ==========

    @Test
    public void testFindMapListWithScalarItems() {
        // List containing scalars (not Map or List) — the instanceof check at line 374 is false
        List<Object> mixed = new ArrayList<>();
        mixed.add("string");
        mixed.add(42);
        mixed.add(null);
        mixed.add(Map.of("k", "v")); // one map

        List<Map<String, Object>> maps = UJson.findMapAll(mixed);
        // Only the Map item should be found; scalars are skipped
        Assertions.assertEquals(1, maps.size());
        Assertions.assertEquals(maps.get(0).get("k"), "v");
    }

    @Test
    public void testFindMapListWithOnlyScalars() {
        List<Object> scalars = new ArrayList<>();
        scalars.add("a");
        scalars.add(1);
        List<Map<String, Object>> maps = UJson.findMapAll(scalars);
        Assertions.assertEquals(0, maps.size());
    }

    // ========== smartCast: auto mode branches ==========

    @Test
    public void testSmartCastAutoJsonArray() throws Exception {
        // auto mode: isJsonObject false, isJsonArray true
        Object result = UJson.smartCast("[1,2,3]", "auto");
        Assertions.assertTrue(result instanceof JSONArray);
    }

    @Test
    public void testSmartCastAutoYamlObject() throws Exception {
        // auto mode: not JSON, but valid YAML — JSONFromYamlSafe returns non-null
        Object result = UJson.smartCast("a: 1\nb: 2", "auto");
        Assertions.assertTrue(result instanceof JSONObject);
    }

    @Test
    public void testSmartCastAutoYamlArray() throws Exception {
        // auto mode: not JSON, but valid YAML array
        Object result = UJson.smartCast("- 1\n- 2\n- 3", "auto");
        Assertions.assertTrue(result instanceof JSONArray);
    }

    @Test
    public void testSmartCastAutoNullType() throws Exception {
        // null type defaults to "auto" via defaultIfBlank
        Object result = UJson.smartCast("{\"a\":1}", null);
        Assertions.assertTrue(result instanceof JSONObject);
    }

    // ========== mergeJson: mustExist=true, key not in target ==========

    @Test
    public void testMergeJsonMustExistKeyNotInTarget() {
        // mustExist=true: key "b" is in from but not in to — should NOT be added
        JSONObject from = JSONObject.of("a", 1, "b", 2);
        JSONObject to = JSONObject.of("a", 10);
        JSONObject merged = UJson.mergeJson(from, to, true, false);
        Assertions.assertEquals(1, merged.size());
        Assertions.assertEquals(1, merged.getIntValue("a"));
        Assertions.assertFalse(merged.containsKey("b"));
    }

    @Test
    public void testMergeJsonMustExistFalseKeyNotInTarget() {
        // mustExist=false (default): key "b" is in from but not in to — should be added
        JSONObject from = JSONObject.of("a", 1, "b", 2);
        JSONObject to = JSONObject.of("a", 10);
        JSONObject merged = UJson.mergeJson(from, to, false, false);
        Assertions.assertEquals(2, merged.size());
        Assertions.assertEquals(1, merged.getIntValue("a"));
        Assertions.assertEquals(2, merged.getIntValue("b"));
    }

    // ========== mergeJson: arrayFill=true, non-JSONArray from, value already in array ==========

    @Test
    public void testMergeJsonArrayFillNonArrayValueAlreadyPresent() {
        // to is JSONArray [1, 2], from value is 1 (already in array)
        // arrayFill=true: !toObjValue.contains(fromValue) is false, so nothing added
        JSONObject from = JSONObject.of("arr", 1);
        JSONObject to = JSONObject.of("arr", JSONArray.of(1, 2));
        JSONObject merged = UJson.mergeJson(from, to, false, true);
        // 1 is already in the array, so size stays 2
        Assertions.assertEquals(2, merged.getJSONArray("arr").size());
    }

    // ========== findMapWithName: no name matches ==========

    @Test
    public void testFindMapWithNameNoMatch() {
        // No map has the given name — lambda returns false for all
        Map<String, Object> root = new LinkedHashMap<>();
        Map<String, Object> child1 = new LinkedHashMap<>();
        child1.put("id", 1);
        Map<String, Object> child2 = new LinkedHashMap<>();
        child2.put("id", 2);
        root.put("x", child1);
        root.put("y", child2);

        List<Map<String, Object>> maps = UJson.findMapWithName(root, "z", "w");
        Assertions.assertEquals(0, maps.size());
    }

    @Test
    public void testFindMapWithNamePartialMatch() {
        // One name matches, one doesn't — exercises the || in the lambda
        Map<String, Object> root = new LinkedHashMap<>();
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("id", 1);
        root.put("a", a);

        List<Map<String, Object>> maps = UJson.findMapWithName(root, "a", "z");
        Assertions.assertEquals(1, maps.size());
    }

    // ========== mergeJson: JSONArray from with arrayFill=true ==========

    @Test
    public void testMergeJsonArrayFillWithArrayFrom() {
        // Both from and to are JSONArray, arrayFill=true
        // fromObjValue.removeAll(toObjValue) then toObjValue.addAll(fromObjValue)
        JSONObject from = JSONObject.of("arr", JSONArray.of(5, 6));
        JSONObject to = JSONObject.of("arr", JSONArray.of(1, 2, 3));
        JSONObject merged = UJson.mergeJson(from, to, false, true);
        // from [5,6] minus to [1,2,3] = [5,6], then to [1,2,3] + [5,6] = [1,2,3,5,6]
        JSONArray arr = merged.getJSONArray("arr");
        Assertions.assertEquals(5, arr.size());
        Assertions.assertTrue(arr.contains(5));
        Assertions.assertTrue(arr.contains(6));
    }

    // ========== mergeJson: toValue is basic type ==========

    @Test
    public void testMergeJsonBasicTypeOverwrite() {
        // toValue is a basic type (not JSONObject, not JSONArray) — hits the else at line 451
        JSONObject from = JSONObject.of("a", "new");
        JSONObject to = JSONObject.of("a", "old");
        JSONObject merged = UJson.mergeJson(from, to);
        Assertions.assertEquals(merged.getString("a"), "new");
    }
}
