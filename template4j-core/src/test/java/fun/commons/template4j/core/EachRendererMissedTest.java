package fun.commons.template4j.core;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

/**
 * Covers EachRenderer missed lines/branches:
 * - Line 64: array select (getClass().isArray())
 * - Line 99: Map iteration with merge
 * - Line 63, 101, 103: merge + nonnull branches with Map select
 */
public class EachRendererMissedTest {

    @Test
    public void testEachWithArraySelect() throws Exception {
        // Java array passed via data → getClass().isArray() branch
        Object r = RenderEngine.build().renderJson("""
                {
                  "@class": "each",
                  "select": "{@arr}",
                  "body": {
                    "item": "{$item}"
                  }
                }
                """, Map.of("arr", new String[]{"a", "b", "c"}));
        Assertions.assertTrue(r instanceof com.alibaba.fastjson2.JSONArray);
        com.alibaba.fastjson2.JSONArray arr = (com.alibaba.fastjson2.JSONArray) r;
        Assertions.assertEquals(3, arr.size());
    }

    @Test
    public void testEachWithMapSelect() throws Exception {
        // Map iteration
        Object r = RenderEngine.build().renderJson("""
                {
                  "@class": "each",
                  "select": {"x": 1, "y": 2},
                  "body": {
                    "key": "{$key}",
                    "val": "{$item}"
                  }
                }
                """, Map.of());
        Assertions.assertTrue(r instanceof com.alibaba.fastjson2.JSONArray);
        com.alibaba.fastjson2.JSONArray arr = (com.alibaba.fastjson2.JSONArray) r;
        Assertions.assertEquals(2, arr.size());
    }

    @Test
    public void testEachWithMapMerge() throws Exception {
        // Map select with merge:true — subTarget is a List
        Object r = RenderEngine.build().renderJson("""
                {
                  "@class": "each",
                  "select": {"a": [1,2]},
                  "body": "{$item}",
                  "merge": true
                }
                """, Map.of());
        Assertions.assertTrue(r instanceof com.alibaba.fastjson2.JSONArray);
    }

    @Test
    public void testEachWithNonnull() throws Exception {
        // nonnull: true removes null results
        Object r = RenderEngine.build().renderJson("""
                {
                  "@class": "each",
                  "select": [1, null, 3],
                  "body": "{$item}",
                  "nonnull": true
                }
                """, Map.of());
        com.alibaba.fastjson2.JSONArray arr = (com.alibaba.fastjson2.JSONArray) r;
        // null items should be removed
        Assertions.assertFalse(arr.contains(null));
    }

    @Test
    public void testEachWithoutBody() throws Exception {
        // No body → return item directly
        Object r = RenderEngine.build().renderJson("""
                {
                  "@class": "each",
                  "select": [10, 20, 30]
                }
                """, Map.of());
        com.alibaba.fastjson2.JSONArray arr = (com.alibaba.fastjson2.JSONArray) r;
        Assertions.assertEquals(3, arr.size());
        Assertions.assertEquals(10, arr.get(0));
    }

    @Test
    public void testEachCustomKeyNameItemName() throws Exception {
        Object r = RenderEngine.build().renderJson("""
                {
                  "@class": "each",
                  "select": ["a"],
                  "keyName": "$idx",
                  "itemName": "$val",
                  "body": {
                    "i": "{$idx}",
                    "v": "{$val}"
                  }
                }
                """, Map.of());
        com.alibaba.fastjson2.JSONArray arr = (com.alibaba.fastjson2.JSONArray) r;
        Assertions.assertEquals(1, arr.size());
    }

    @Test
    public void testEachNullSelect() throws Exception {
        Object r = RenderEngine.build().renderJson("""
                {
                  "@class": "each",
                  "select": "{@nonexistent}"
                }
                """, Map.of());
        com.alibaba.fastjson2.JSONArray arr = (com.alibaba.fastjson2.JSONArray) r;
        Assertions.assertEquals(0, arr.size());
    }
}
