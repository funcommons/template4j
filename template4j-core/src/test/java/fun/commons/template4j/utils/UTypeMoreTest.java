package fun.commons.template4j.utils;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import fun.commons.template4j.utils.UType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 覆盖本地 UType 各方法 — 补足 isTrue/isFalse/castValue/loadClass/testBoolean 等分支。
 */
public class UTypeMoreTest {

    // ========== isTrue / isFalse ==========

    @Test
    public void testIsTrueNull() {
        Assertions.assertFalse(UType.isTrue(null));
        Assertions.assertFalse(UType.isFalse(null));
    }

    @Test
    public void testIsTrueBoolean() {
        Assertions.assertTrue(UType.isTrue(true));
        Assertions.assertFalse(UType.isTrue(false));
        Assertions.assertFalse(UType.isFalse(true));
        Assertions.assertTrue(UType.isFalse(false));
    }

    @Test
    public void testIsTrueInteger() {
        Assertions.assertTrue(UType.isTrue(1));
        Assertions.assertFalse(UType.isTrue(0));
        Assertions.assertFalse(UType.isFalse(1));
        Assertions.assertTrue(UType.isFalse(0));
    }

    @Test
    public void testIsFalseBooleanAndInteger() {
        // isFalse Boolean 分支
        Assertions.assertTrue(UType.isFalse(false));
        Assertions.assertFalse(UType.isFalse(true));
        // isFalse Integer 分支
        Assertions.assertTrue(UType.isFalse(0));
        Assertions.assertFalse(UType.isFalse(1));
    }

    @Test
    public void testIsFalseNonBooleanNonInteger() {
        // else 分支：转 String 再用 BooleanUtils.toBooleanObject
        Assertions.assertTrue(UType.isFalse("false"));
        Assertions.assertTrue(UType.isFalse("no"));
        Assertions.assertFalse(UType.isFalse("true"));
        Assertions.assertFalse(UType.isFalse("anything"));
    }

    @Test
    public void testIsTrueString() {
        Assertions.assertTrue(UType.isTrue("true"));
        Assertions.assertTrue(UType.isTrue("yes"));
        Assertions.assertFalse(UType.isTrue("false"));
        Assertions.assertFalse(UType.isTrue("no"));
    }

    // ========== testBoolean ==========

    @Test
    public void testTestBooleanNull() {
        Assertions.assertFalse(UType.testBoolean(null));
    }

    @Test
    public void testTestBooleanBoolean() {
        Assertions.assertTrue(UType.testBoolean(true));
        Assertions.assertFalse(UType.testBoolean(false));
    }

    @Test
    public void testTestBooleanNumber() {
        Assertions.assertTrue(UType.testBoolean(1L));
        Assertions.assertFalse(UType.testBoolean(0L));
        Assertions.assertFalse(UType.testBoolean(-1L));
    }

    @Test
    public void testTestBooleanString() {
        Assertions.assertTrue(UType.testBoolean("true"));
        Assertions.assertTrue(UType.testBoolean("yes"));
        Assertions.assertTrue(UType.testBoolean("y"));
        Assertions.assertTrue(UType.testBoolean("1"));
        Assertions.assertTrue(UType.testBoolean("on"));
        Assertions.assertFalse(UType.testBoolean("false"));
        Assertions.assertFalse(UType.testBoolean(""));
        Assertions.assertTrue(UType.testBoolean("真")); // matches "真|是"
    }

    @Test
    public void testTestBooleanCollection() {
        Assertions.assertTrue(UType.testBoolean(new ArrayList<Object>() {{ add(1); }}));
        Assertions.assertFalse(UType.testBoolean(new ArrayList<>()));
    }

    @Test
    public void testTestBooleanMap() {
        Map<String, Object> m = new HashMap<>();
        m.put("k", "v");
        Assertions.assertTrue(UType.testBoolean(m));
        Assertions.assertFalse(UType.testBoolean(new HashMap<>()));
    }

    @Test
    public void testTestBooleanArray() {
        Assertions.assertFalse(UType.testBoolean(new String[]{}));
        Assertions.assertTrue(UType.testBoolean(new String[]{"x"}));
        Assertions.assertFalse(UType.testBoolean(new int[]{}));
        Assertions.assertTrue(UType.testBoolean(new int[]{1}));
    }

    @Test
    public void testTestBooleanPojo() {
        // 其它对象且不为 null，返回 true
        Assertions.assertTrue(UType.testBoolean(new Exception("test")));
    }

    // ========== castValue ==========

    @Test
    public void testCastValueNull() {
        Assertions.assertNull(UType.castValue(null, String.class));
        Assertions.assertNull(UType.castValue(null, "String"));
    }

    @Test
    public void testCastValueToJSON() {
        Object result = UType.castValue("{\"a\":1}", JSON.class);
        Assertions.assertTrue(result instanceof JSONObject);
        Assertions.assertEquals(1, ((JSONObject) result).getIntValue("a"));
    }

    @Test
    public void testCastValueToJSONBlank() {
        Assertions.assertNull(UType.castValue("", JSON.class));
        Assertions.assertNull(UType.castValue("  ", JSON.class));
    }

    @Test
    public void testCastValueToJSONObjectBlank() {
        Assertions.assertNull(UType.castValue("", JSONObject.class));
        Assertions.assertNull(UType.castValue("  ", JSONObject.class));
    }

    @Test
    public void testCastValueToJSONArrayBlank() {
        Assertions.assertNull(UType.castValue("", JSONArray.class));
        Assertions.assertNull(UType.castValue("  ", JSONArray.class));
    }

    @Test
    public void testCastValueToOtherClass() {
        // 非 String/JSON/JSONObject/JSONArray → 走 TypeUtils.cast
        Assertions.assertEquals(42, (int) UType.castValue(42, Integer.class));
        // String 不转换数字 → 不写死测试
    }

    @Test
    public void testTestBooleanOtherType() {
        // 不属于任何 if-else 类型的对象 — fall through 后 test 保持为 true
        Object pojo = new Object();
        Assertions.assertTrue(UType.testBoolean(pojo));
    }

    @Test
    public void testTestBooleanArrayEmpty() {
        // 空数组 — test = 0 > 0 = false
        Assertions.assertFalse(UType.testBoolean(new int[]{}));
        Assertions.assertFalse(UType.testBoolean(new String[]{}));
    }

    @Test
    public void testTestBooleanArrayNonEmpty() {
        // 非空数组
        Assertions.assertTrue(UType.testBoolean(new int[]{1}));
        Assertions.assertTrue(UType.testBoolean(new String[]{"x"}));
    }

    @Test
    public void testTestBooleanZero() {
        // 0 视为 false
        Assertions.assertFalse(UType.testBoolean(0));
        Assertions.assertFalse(UType.testBoolean(0L));
        Assertions.assertFalse(UType.testBoolean((short) 0));
    }

    @Test
    public void testTestBooleanNegative() {
        // 负数视为 false
        Assertions.assertFalse(UType.testBoolean(-1L));
    }

    @Test
    public void testTestBooleanPositive() {
        // 正数视为 true
        Assertions.assertTrue(UType.testBoolean(1L));
    }

    @Test
    public void testTestBooleanCollectionEmpty() {
        Assertions.assertFalse(UType.testBoolean(java.util.Collections.emptyList()));
        Assertions.assertFalse(UType.testBoolean(java.util.Collections.emptySet()));
    }

    @Test
    public void testTestBooleanCollectionNonEmpty() {
        Assertions.assertTrue(UType.testBoolean(java.util.Collections.singleton("x")));
    }

    @Test
    public void testTestBooleanMapEmpty() {
        Assertions.assertFalse(UType.testBoolean(new java.util.HashMap<>()));
    }

    @Test
    public void testTestBooleanMapNonEmpty() {
        java.util.Map<String, Object> m = new java.util.HashMap<>();
        m.put("k", "v");
        Assertions.assertTrue(UType.testBoolean(m));
    }

    @Test
    public void testTestBooleanBooleanTrue() {
        Assertions.assertTrue(UType.testBoolean(true));
        Assertions.assertFalse(UType.testBoolean(false));
    }

    @Test
    public void testTestBooleanStringBlank() {
        Assertions.assertFalse(UType.testBoolean(""));
        Assertions.assertFalse(UType.testBoolean("   "));
    }

    @Test
    public void testTestBooleanStringTrue() {
        Assertions.assertTrue(UType.testBoolean("true"));
        Assertions.assertTrue(UType.testBoolean("yes"));
        Assertions.assertTrue(UType.testBoolean("y"));
        Assertions.assertTrue(UType.testBoolean("on"));
        Assertions.assertTrue(UType.testBoolean("1"));
    }

    @Test
    public void testTestBooleanChinese() {
        // "真" 和 "是" 被识别为 true
        Assertions.assertTrue(UType.testBoolean("真"));
        Assertions.assertTrue(UType.testBoolean("是"));
    }

    @Test
    public void testTestBooleanStringFalse() {
        Assertions.assertFalse(UType.testBoolean("false"));
        Assertions.assertFalse(UType.testBoolean("no"));
    }

    @Test
    public void testCastValueToJSONObject() {
        Object result = UType.castValue("{\"a\":1}", JSONObject.class);
        Assertions.assertTrue(result instanceof JSONObject);
    }

    @Test
    public void testCastValueToJSONArray() {
        Object result = UType.castValue("[1,2,3]", JSONArray.class);
        Assertions.assertTrue(result instanceof JSONArray);
    }

    @Test
    public void testCastValueStringToInt() {
        Assertions.assertEquals(42, (int) UType.castValue("42", Integer.class));
    }

    @Test
    public void testCastValueJSONString() {
        // JSONString 类名返回 JSON 字符串
        Map<String, Object> map = new HashMap<>();
        map.put("a", 1);
        Object result = UType.castValue(map, "JSONString");
        Assertions.assertEquals(result, "{\"a\":1}");
    }

    @Test
    public void testCastValueStringToString() {
        Assertions.assertEquals(UType.castValue("hello", "String"), "hello");
    }

    // ========== loadClass ==========

    @Test
    public void testLoadClassKnown() {
        Class<?> c = UType.loadClass("String");
        Assertions.assertEquals(String.class, c);
    }

    @Test
    public void testLoadClassLowercase() {
        Class<?> c = UType.loadClass("string");
        Assertions.assertEquals(String.class, c);
    }

    @Test
    public void testLoadClassByFQN() {
        Class<?> c = UType.loadClass("java.lang.Integer");
        Assertions.assertEquals(Integer.class, c);
    }

    @Test
    public void testLoadClassUnknown() {
        // 未知类返回 null（TypeUtils.loadClass 行为）或抛异常
        Object result;
        try {
            result = UType.loadClass("NonExistentClass123");
            Assertions.assertNull(result, "expected null or exception, got: " + result);
        } catch (Exception ex) {
            // 也接受异常
        }
    }

    // ========== getClass ==========

    @Test
    public void testGetClass() {
        Assertions.assertEquals(String.class, UType.getClass("hello"));
        Assertions.assertNull(UType.getClass(null));
    }
}
