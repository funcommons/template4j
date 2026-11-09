package fun.commons.template4j.utils;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JsonMergerTest {

    /**
     * JsonMerger semantics: {@code mergeJson(from, to)} returns a copy of `to`
     * with `from`'s values OVERRIDING on key collision. FROM wins. Arrays
     * union when arrayFill=true (the default).
     */
    @Test
    public void mergeJson_fromOverridesToOnKeyCollision() {
        JSONObject from = JSONObject.of("k", "from-a", "only-a", 1);
        JSONObject to = JSONObject.of("k", "from-b", "only-b", 2);
        JSONObject merged = JsonMerger.mergeJson(from, to);
        assertEquals(merged.get("k"), "from-a"); // from wins
        assertEquals(1, merged.get("only-a"));
        assertEquals(2, merged.get("only-b"));
    }

    @Test
    public void mergeJson_deeplyMergesNestedMaps() {
        JSONObject from = JSONObject.of("outer", JSONObject.of("x", 1, "y", 2));
        JSONObject to = JSONObject.of("outer", JSONObject.of("y", 99, "z", 3));
        JSONObject merged = JsonMerger.mergeJson(from, to);
        JSONObject outer = merged.getJSONObject("outer");
        // from wins on collision: y=2 not y=99
        assertEquals(1, outer.get("x"));
        assertEquals(2, outer.get("y"));
        assertEquals(3, outer.get("z"));
    }

    @Test
    public void mergeJson_arraysUnionByDefault() {
        // arrayFill=true default → arrays combine (deduped)
        JSONObject from = JSONObject.of("list", JSONArray.of(1, 2));
        JSONObject to = JSONObject.of("list", JSONArray.of(2, 3));
        JSONObject merged = JsonMerger.mergeJson(from, to);
        JSONArray list = merged.getJSONArray("list");
        // union of [1,2] and [2,3] → at least contains all 3 unique values
        assertTrue(list.contains(1), "expected 1 in list, got " + list);
        assertTrue(list.contains(2), "expected 2 in list, got " + list);
        assertTrue(list.contains(3), "expected 3 in list, got " + list);
    }

    @Test
    public void mergeJson_arrayFillFalse_replacesEntireArray() {
        JSONObject from = JSONObject.of("list", JSONArray.of(9));
        JSONObject to = JSONObject.of("list", JSONArray.of(1, 2, 3));
        JSONObject merged = JsonMerger.mergeJson(from, to, false, false);
        JSONArray list = merged.getJSONArray("list");
        assertEquals(1, list.size());
        assertEquals(9, list.get(0));
    }
}
