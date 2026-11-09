package fun.commons.template4j.core;

import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

/**
 * Contract tests for the parseStrTemplate result cache inside JsonRenderer.
 *
 * The cache is instance-scoped (one per JsonRenderer), so each test builds its own
 * renderer via the package-private constructor and starts from an empty cache.
 * Behavior is exercised through the public render API: every String that passes
 * through convertSelf goes to renderString, the sole parseCache entry point.
 *
 * Note: template4j.parseCache.maxEntries is a static final read at class-load time,
 * so the cap cannot be changed at runtime here; the bound is only asserted loosely.
 */
public class ParseCacheTest {

    @Test
    public void testSameStringTemplateRenderedTwiceUsesSingleCacheEntry() {
        JsonRenderer renderer = new JsonRenderer();
        Map<String, Object> data = data("name", "World");
        Object first = renderer.render("Hello {@name}", data);
        Object second = renderer.render("Hello {@name}", data);
        Assertions.assertEquals("Hello World", first);
        Assertions.assertEquals(first, second);
        Assertions.assertEquals(1, renderer.parseCacheSize());
    }

    @Test
    public void testDistinctStringTemplatesCreateDistinctEntries() {
        JsonRenderer renderer = new JsonRenderer();
        Map<String, Object> data = data("a", "1", "b", "2");
        Assertions.assertEquals("A=1", renderer.render("A={@a}", data));
        Assertions.assertEquals("B=2", renderer.render("B={@b}", data));
        Assertions.assertEquals(2, renderer.parseCacheSize());
    }

    @Test
    public void testRepeatingRendersDoNotGrowCache() {
        JsonRenderer renderer = new JsonRenderer();
        Map<String, Object> data = data("n", 7);
        for (int i = 0; i < 3; i++) {
            Assertions.assertEquals("n=7", renderer.render("n={@n}", data));
        }
        Assertions.assertEquals(1, renderer.parseCacheSize());
    }

    @Test
    public void testClearParseCacheThenRerenderStillCorrect() {
        JsonRenderer renderer = new JsonRenderer();
        Map<String, Object> data = data("x", "ray");
        Assertions.assertEquals("x=ray", renderer.render("x={@x}", data));
        Assertions.assertTrue(renderer.parseCacheSize() > 0);
        renderer.clearParseCache();
        Assertions.assertEquals(0, renderer.parseCacheSize());
        Assertions.assertEquals("x=ray", renderer.render("x={@x}", data));
        Assertions.assertEquals(1, renderer.parseCacheSize());
    }

    @Test
    public void testCacheSizeBoundedAfterManyDistinctTemplates() {
        JsonRenderer renderer = new JsonRenderer();
        Map<String, Object> data = data("v", "ok");
        int distinct = 5;
        for (int i = 0; i < distinct; i++) {
            Assertions.assertEquals("t" + i + "=ok", renderer.render("t" + i + "={@v}", data));
        }
        int size = renderer.parseCacheSize();
        // Loose upper bound: at most one entry per rendered template (small headroom),
        // and never beyond the configured static cap.
        Assertions.assertTrue(size <= distinct * 2,
                "cache grew unbounded: " + size + " entries for " + distinct + " templates");
        Assertions.assertTrue(size <= JsonRenderer.MAX_PARSE_CACHE_ENTRIES);
    }

    @Test
    public void testObjectTemplateStringMembersPopulateCache() {
        // convertSelf recurses into Map/JSONObject values, so string members still
        // reach renderString; rendering an object template must grow the cache.
        JsonRenderer renderer = new JsonRenderer();
        Map<String, Object> data = data("name", "funcommons");
        int before = renderer.parseCacheSize();
        JSONObject template = new JSONObject();
        template.put("greeting", "Hi {@name}");
        Object result = renderer.render(template, data);
        Assertions.assertTrue(result instanceof JSONObject);
        Assertions.assertEquals("Hi funcommons",
                ((JSONObject) result).getString("greeting"));
        Assertions.assertTrue(renderer.parseCacheSize() > before,
                "string members of an object template should be cached");
    }

    private static Map<String, Object> data(Object... pairs) {
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((String) pairs[i], pairs[i + 1]);
        }
        return map;
    }
}
