package fun.commons.template4j.utils;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONWriter;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * JSON 工具门面：保留所有历史 API（isJsonObject / JSONFromYaml / mergeJson / findMap / smartCast 等），
 * 内部委托给 {@link JsonValidator} / {@link YamlConverter} / {@link JsonMerger} / {@link JsonFinder} 等专职类。
 */
public class UJson {

    private UJson() {
    }

    // ========== JSON 验证（委托 JsonValidator） ==========

    public static boolean isJsonObject(String str) {
        return JsonValidator.isJsonObject(str);
    }

    public static boolean isJsonArray(String str) {
        return JsonValidator.isJsonArray(str);
    }

    public static boolean isJson(String str) {
        return JsonValidator.isJson(str);
    }

    // ========== YAML ↔ JSON 转换（委托 YamlConverter） ==========

    public static Object JSONFromYamlSafe(String str) {
        return YamlConverter.toJsonObjectSafe(str);
    }

    public static String toYAMLString(Object object) throws IOException {
        return YamlConverter.toYAMLString(object);
    }

    public static Object JSONFromYaml(String yamlString) throws IOException {
        return YamlConverter.fromYaml(yamlString);
    }

    public static JSONObject JSONObjectFromYaml(String yamlString) throws IOException {
        return YamlConverter.jsonObjectFromYaml(yamlString);
    }

    public static JSONArray JSONArrayFromYaml(String yamlString) throws IOException {
        return YamlConverter.jsonArrayFromYaml(yamlString);
    }

    // ========== 类型转换 ==========

    public static JSONArray toJsonArray(Object value) {
        if (value == null) {
            return new JSONArray();
        }
        if (value instanceof JSONArray jsonArray) {
            return jsonArray;
        }
        JSONArray array = new JSONArray();
        if (value instanceof Collection collection) {
            array.addAll(collection);
            return array;
        }
        if (value instanceof Object[] objects) {
            array.addAll(Arrays.asList(objects));
            return array;
        }
        if (value instanceof String str) {
            if (isJsonArray(str)) {
                return JSONArray.parseArray(str);
            }
        }
        array.add(value);
        return array;
    }

    public static JSONObject toJsonObject(Object value) {
        JSONObject obj = new JSONObject();
        if (value == null) {
            return obj;
        }
        if (value instanceof JSONObject jsonObject) {
            obj.putAll(jsonObject);
        }
        if (value instanceof Map map) {
            obj.putAll(map);
        }
        if (value instanceof String str) {
            if (isJsonObject(str)) {
                obj = JSONObject.parseObject(str);
            }
        } else {
            obj = JSONObject.from(value);
        }
        return obj;
    }

    public static Object JSONFromJson(String jsonString) throws IOException {
        Object result = JSON.parse(jsonString);
        if (result instanceof JSONObject || result instanceof JSONArray) {
            return result;
        }
        throw new IOException("invalid json object or array string");
    }

    public static String toPrettyJSONString(Object object) throws IOException {
        return JSON.toJSONString(object, JSONWriter.Feature.PrettyFormat);
    }

    public static String toWebJSONString(Object object) {
        return JSON.toJSONString(object, JSONWriter.Feature.BrowserCompatible, JSONWriter.Feature.BrowserSecure);
    }

    // ========== 深拷贝 ==========

    public static JSONObject copyJSONObject(JSONObject json) {
        return copyJSON(json);
    }

    public static JSONArray copyJSONArray(JSONArray json) {
        return copyJSON(json);
    }

    public static <T> T copyJSON(T json) {
        return (T) JSON.parse(JSON.toJSONString(json));
    }

    public static <T> T copy(Object json) {
        return (T) JSON.parse(JSON.toJSONString(json));
    }

    // ========== 混合解析 ==========

    public static Object string2json(String src) throws IOException {
        Object object;
        if (isJsonObject(src)) {
            object = JSON.parseObject(src);
        } else if (isJsonArray(src)) {
            object = JSON.parseArray(src);
        } else {
            object = JSONFromYamlSafe(src);
            if (object == null) {
                throw new IOException("invalid json|yaml string");
            }
        }
        return object;
    }

    // ========== 树搜索（委托 JsonFinder） ==========

    public static List<Map<String, Object>> findMapAll(Object root) {
        return JsonFinder.findMapAll(root);
    }

    public static List<Map<String, Object>> findMapWithName(Object root, String... names) {
        return JsonFinder.findMapWithName(root, names);
    }

    public static List<Map<String, Object>> findMapContainsPath(Object root, String path) {
        return JsonFinder.findMapContainsPath(root, path);
    }

    public static List<Map<String, Object>> findMapContainsKey(Object root, String... keys) {
        return JsonFinder.findMapContainsKey(root, keys);
    }

    public static List<Map<String, Object>> findMap(Object root, java.util.function.BiPredicate<String, Map<String, Object>> matcher) {
        return JsonFinder.findMap(root, matcher);
    }

    // ========== 深合并（委托 JsonMerger） ==========

    public static JSONObject mergeJson(JSONObject fromObj, JSONObject toObj) {
        return JsonMerger.mergeJson(fromObj, toObj);
    }

    public static JSONObject mergeJson(JSONObject fromObj, JSONObject toObj, boolean mustExist, boolean arrayFill) {
        return JsonMerger.mergeJson(fromObj, toObj, mustExist, arrayFill);
    }

    // ========== 类型智能转换 ==========

    public static Object smartCast(String str, String type) throws IOException {
        Object result = str;
        String dataType = StringUtils.defaultIfBlank(type, "auto").toLowerCase();
        if (dataType.equals("json")) {
            result = JSON.parse(str);
        } else if (dataType.equals("json-object")) {
            result = JSONObject.parseObject(str);
        } else if (dataType.equals("json-array")) {
            result = JSONArray.parseArray(str);
        } else if (dataType.equals("yaml")) {
            result = UJson.JSONFromYaml(str);
        } else if (dataType.equals("yaml-object")) {
            result = UJson.JSONObjectFromYaml(str);
        } else if (dataType.equals("yaml-array")) {
            result = UJson.JSONArrayFromYaml(str);
        } else if (dataType.equals("string") || dataType.equals("text")) {
            result = str;
        } else if (dataType.equals("auto")) {
            if (isJsonObject(str)) {
                result = JSON.parseObject(str);
            } else if (isJsonArray(str)) {
                result = JSON.parseArray(str);
            } else {
                result = JSONFromYamlSafe(str);
                if (result == null) {
                    result = str;
                }
            }
        } else {
            throw new IllegalArgumentException("Unsupported data type: " + type);
        }
        return result;
    }
}
