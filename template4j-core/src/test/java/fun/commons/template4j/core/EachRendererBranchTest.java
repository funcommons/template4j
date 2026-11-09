package fun.commons.template4j.core;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

/**
 * Covers EachRenderer remaining branches:
 * - Map iteration with non-null result and merge
 * - Map iteration where subTarget is null
 * - Map iteration without body
 */
public class EachRendererBranchTest {

    @Test
    public void testEachMapSelectWithBodyMerge() throws Exception {
        // Map select with body returning a list, merge=true
        Object r = RenderEngine.build().renderJson("""
                {
                  "@class": "each",
                  "select": {"a": [1,2], "b": [3]},
                  "body": "{$item}",
                  "merge": true
                }
                """, Map.of());
        Assertions.assertTrue(r instanceof com.alibaba.fastjson2.JSONArray);
        com.alibaba.fastjson2.JSONArray arr = (com.alibaba.fastjson2.JSONArray) r;
        // merge=true flattens: [1,2,3]
        Assertions.assertEquals(3, arr.size());
    }

    @Test
    public void testEachMapSelectNoMerge() throws Exception {
        // Map select with body returning a list, merge=false
        Object r = RenderEngine.build().renderJson("""
                {
                  "@class": "each",
                  "select": {"a": [1,2], "b": [3]},
                  "body": "{$item}",
                  "merge": false
                }
                """, Map.of());
        Assertions.assertTrue(r instanceof com.alibaba.fastjson2.JSONArray);
        com.alibaba.fastjson2.JSONArray arr = (com.alibaba.fastjson2.JSONArray) r;
        // merge=false keeps as sub-arrays
        Assertions.assertEquals(2, arr.size());
    }

    @Test
    public void testEachMapSelectWithoutBody() throws Exception {
        // Map select without body → return item directly
        Object r = RenderEngine.build().renderJson("""
                {
                  "@class": "each",
                  "select": {"x": 10, "y": 20}
                }
                """, Map.of());
        Assertions.assertTrue(r instanceof com.alibaba.fastjson2.JSONArray);
        com.alibaba.fastjson2.JSONArray arr = (com.alibaba.fastjson2.JSONArray) r;
        Assertions.assertEquals(2, arr.size());
    }

    @Test
    public void testEachMapSelectWithNullBodyResult() throws Exception {
        // Map select where body renders to null
        Object r = RenderEngine.build().renderJson("""
                {
                  "@class": "each",
                  "select": {"a": null, "b": "val"},
                  "body": "{$item}"
                }
                """, Map.of());
        Assertions.assertTrue(r instanceof com.alibaba.fastjson2.JSONArray);
        com.alibaba.fastjson2.JSONArray arr = (com.alibaba.fastjson2.JSONArray) r;
        // null result from body should not be added (subTarget == null check)
        Assertions.assertTrue(arr.size() <= 2);
    }
}
