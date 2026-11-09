package fun.commons.template4j.core.components;

import com.alibaba.fastjson2.JSONArray;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import fun.commons.template4j.core.RenderEngine;

/**
 * Covers the 1 missed branch in EachRenderer.render() line 101:
 * the false branch of "if (subTarget != null)" in the Map iteration path.
 *
 * This branch is taken when the rendered subTarget is null while iterating
 * a Map select. We produce a null subTarget by:
 * - Using a Groovy script body that returns null for certain keys
 * - Using a Map value that is null with no body (isBody=false, subTarget=obj=null)
 */
public class EachRendererBranchTest {

    /**
     * Map select with a Groovy body that returns null for one key.
     * For key "skip", the body returns null -> subTarget is null -> false branch of line 101.
     * For key "keep", the body returns the item -> subTarget is non-null -> true branch.
     */
    @Test
    public void testMapSelectBodyReturnsNull() throws Exception {
        Object r = RenderEngine.build().renderJson("""
                {
                  "@class": "each",
                  "select": {"skip": "a", "keep": "b"},
                  "body": "{= $key == 'keep' ? $item : null }"
                }
                """, Map.of());
        Assertions.assertTrue(r instanceof JSONArray);
        JSONArray arr = (JSONArray) r;
        // Only the "keep" entry should survive
        Assertions.assertEquals(1, arr.size());
        Assertions.assertEquals(arr.get(0), "b");
    }

    /**
     * Map select with null value and no body (isBody=false).
     * When obj is null, subTarget = obj = null -> false branch of line 101.
     */
    @Test
    public void testMapSelectNullValueNoBody() throws Exception {
        // Use Java Map via data to ensure null value is preserved
        HashMap<String, Object> mapWithNull = new HashMap<>();
        mapWithNull.put("present", "hello");
        mapWithNull.put("absent", null);

        Object r = RenderEngine.build().renderJson("""
                {
                  "@class": "each",
                  "select": "{@mymap}"
                }
                """, Map.of("mymap", mapWithNull));
        Assertions.assertTrue(r instanceof JSONArray);
        JSONArray arr = (JSONArray) r;
        // Only the non-null value should be in the result
        Assertions.assertEquals(1, arr.size());
        Assertions.assertEquals(arr.get(0), "hello");
    }

    /**
     * Map select with body that always returns null via Groovy {% %} script.
     * Every iteration produces subTarget=null -> false branch of line 101.
     */
    @Test
    public void testMapSelectBodyAlwaysNull() throws Exception {
        Object r = RenderEngine.build().renderJson("""
                {
                  "@class": "each",
                  "select": {"a": 1, "b": 2},
                  "body": "{% return null %}"
                }
                """, Map.of());
        Assertions.assertTrue(r instanceof JSONArray);
        JSONArray arr = (JSONArray) r;
        Assertions.assertEquals(0, arr.size());
    }

    /**
     * Map select with null value and body that renders the item.
     * When the Map value is null, $item is null, body returns null.
     */
    @Test
    public void testMapSelectNullValueWithBody() throws Exception {
        HashMap<String, Object> mapWithNull = new HashMap<>();
        mapWithNull.put("x", "exists");
        mapWithNull.put("y", null);

        Object r = RenderEngine.build().renderJson("""
                {
                  "@class": "each",
                  "select": "{@mymap}",
                  "body": "{$item}"
                }
                """, Map.of("mymap", mapWithNull));
        Assertions.assertTrue(r instanceof JSONArray);
        JSONArray arr = (JSONArray) r;
        // null $item renders to null, so only "exists" survives
        Assertions.assertEquals(1, arr.size());
        Assertions.assertEquals(arr.get(0), "exists");
    }
}
