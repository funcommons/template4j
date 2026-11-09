package fun.commons.template4j.utils;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JsonFinderTest {

    private static JSONObject sample() {
        // root
        //   user: { name: "Alice", address: { city: "Shanghai" } }
        //   items: [{ id: 1 }, { id: 2, name: "X" }]
        return JSONObject.of(
            "user", JSONObject.of(
                "name", "Alice",
                "address", JSONObject.of("city", "Shanghai")
            ),
            "items", JSONArray.of(
                JSONObject.of("id", 1),
                JSONObject.of("id", 2, "name", "X")
            )
        );
    }

    @Test
    public void findMapAll_collectsEveryMapInTheTree() {
        List<Map<String, Object>> all = JsonFinder.findMapAll(sample());
        // root + user + user.address + items[0] + items[1] = 5 maps
        assertEquals(5, all.size());
    }

    @Test
    public void findMapWithName_matchesByKey() {
        List<Map<String, Object>> matches = JsonFinder.findMapWithName(sample(), "user");
        assertEquals(1, matches.size());
        assertEquals(matches.get(0).get("name"), "Alice");
    }

    @Test
    public void findMapWithName_matchesMultipleOccurrences() {
        JSONObject root = JSONObject.of(
            "a", JSONObject.of("k", 1),
            "b", JSONObject.of("a", JSONObject.of("k", 2))
        );
        List<Map<String, Object>> matches = JsonFinder.findMapWithName(root, "a");
        assertEquals(2, matches.size());
    }

    @Test
    public void findMapContainsKey_matchesByKeyPresence() {
        List<Map<String, Object>> matches = JsonFinder.findMapContainsKey(sample(), "city");
        assertEquals(1, matches.size());
        assertEquals(matches.get(0).get("city"), "Shanghai");
    }

    @Test
    public void findMapContainsPath_resolvesJsonPath() {
        List<Map<String, Object>> matches = JsonFinder.findMapContainsPath(sample(), "user.address.city");
        assertEquals(1, matches.size());
    }

    @Test
    public void findMapAll_emptyForScalarRoot() {
        assertEquals(0, JsonFinder.findMapAll("just a string").size());
        assertEquals(0, JsonFinder.findMapAll(42).size());
        assertEquals(0, JsonFinder.findMapAll(null).size());
    }

    @Test
    public void findMap_withCustomPredicate_findsByValueContent() {
        List<Map<String, Object>> matches = JsonFinder.findMap(sample(), (k, m) -> m.containsKey("id"));
        // items[0] and items[1] both contain id
        assertEquals(2, matches.size());
        assertTrue(matches.stream().anyMatch(m -> Integer.valueOf(1).equals(m.get("id"))));
        assertTrue(matches.stream().anyMatch(m -> Integer.valueOf(2).equals(m.get("id"))));
    }
}
