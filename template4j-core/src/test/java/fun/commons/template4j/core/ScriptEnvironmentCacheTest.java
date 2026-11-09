package fun.commons.template4j.core;

import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TDD contract tests for the Groovy compiled-script cache in {@link ScriptEnvironment}.
 *
 * <p>Contract under test:
 * <ul>
 *   <li>package-private {@code scriptCacheSize()} / {@code clearScriptCache()}</li>
 *   <li>{@code eval(script, self)} external behavior unchanged (results identical, `@` rewrite unaffected)</li>
 *   <li>same script twice → 1 entry; distinct scripts → distinct entries; cache stores compiled
 *       scripts (not results), so bindings are re-bound per call</li>
 *   <li>{@code template4j.scriptCache.maxEntries} (default 2000) caps entry count via eviction</li>
 *   <li>{@code template4j.scriptCache.enabled=false} → results still correct, cache does not grow</li>
 * </ul>
 *
 * <p>Cache config properties are expected to be read when a ScriptEnvironment instance is
 * constructed (NOT in a static initializer), so tests set them before {@code new ScriptEnvironment()}
 * and restore them in finally. Each size-sensitive test calls {@code clearScriptCache()} on a fresh
 * instance first so assertions hold whether the store is per-instance or shared.
 */
class ScriptEnvironmentCacheTest {

    private static SelfObject self(Map<String, Object> data) {
        return new SelfObject(new JsonRenderer(), data);
    }

    private static void restoreProp(String key, String previous) {
        if (previous == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, previous);
        }
    }

    @Test
    void sameScriptEvaluatedTwice_yieldsSingleCacheEntryAndConsistentResult() throws Exception {
        ScriptEnvironment env = new ScriptEnvironment();
        env.clearScriptCache();
        Object first = env.eval("1 + 2", self(new HashMap<>()));
        Object second = env.eval("1 + 2", self(new HashMap<>()));
        assertEquals(3, first);
        assertEquals(first, second);
        assertEquals(1, env.scriptCacheSize());
    }

    @Test
    void distinctScripts_getDistinctEntriesWithCorrectResults() throws Exception {
        ScriptEnvironment env = new ScriptEnvironment();
        env.clearScriptCache();
        assertEquals(3, env.eval("1 + 2", self(new HashMap<>())));
        assertEquals("ab", env.eval("'a' + 'b'", self(new HashMap<>())));
        assertEquals(2, env.scriptCacheSize());
    }

    @Test
    void sameScriptOnDifferentSelf_reusesEntryButRebindsPerCall() throws Exception {
        // The cache is keyed by script text only; bindings (data/vars/...) must be rebuilt per
        // eval call, so two selves with different data get their own correct results from one entry.
        ScriptEnvironment env = new ScriptEnvironment();
        env.clearScriptCache();
        Map<String, Object> d1 = new HashMap<>();
        d1.put("n", 1);
        Map<String, Object> d2 = new HashMap<>();
        d2.put("n", 2);
        assertEquals(1, env.eval("data.get('n')", self(d1)));
        assertEquals(2, env.eval("data.get('n')", self(d2)));
        assertEquals(1, env.scriptCacheSize());
    }

    @Test
    void clearScriptCache_emptiesEntriesAndStillEvaluatesCorrectly() throws Exception {
        ScriptEnvironment env = new ScriptEnvironment();
        env.clearScriptCache();
        env.eval("40 + 2", self(new HashMap<>()));
        env.eval("'x' + 'y'", self(new HashMap<>()));
        assertEquals(2, env.scriptCacheSize());
        env.clearScriptCache();
        assertEquals(0, env.scriptCacheSize());
        assertEquals(42, env.eval("40 + 2", self(new HashMap<>())));
    }

    @Test
    void maxEntriesProperty_evictsSoSizeNeverExceedsLimit() throws Exception {
        String key = "template4j.scriptCache.maxEntries";
        String previous = System.getProperty(key);
        System.setProperty(key, "2");
        try {
            ScriptEnvironment env = new ScriptEnvironment();
            env.clearScriptCache();
            Map<String, Object> data = new HashMap<>();
            assertEquals(1, env.eval("1", self(data)));
            assertEquals(2, env.eval("1 + 1", self(data)));
            assertEquals(3, env.eval("1 + 1 + 1", self(data)));
            assertTrue(env.scriptCacheSize() <= 2,
                    "size " + env.scriptCacheSize() + " exceeds maxEntries=2");
            // an evicted script must still evaluate correctly on re-eval
            assertEquals(1, env.eval("1", self(data)));
        } finally {
            restoreProp(key, previous);
            new ScriptEnvironment().clearScriptCache();
        }
    }

    @Test
    void disabledProperty_resultsStayCorrectAndCacheDoesNotGrow() throws Exception {
        String key = "template4j.scriptCache.enabled";
        String previous = System.getProperty(key);
        System.setProperty(key, "false");
        try {
            ScriptEnvironment env = new ScriptEnvironment();
            env.clearScriptCache();
            int sizeBefore = env.scriptCacheSize();
            Map<String, Object> data = new HashMap<>();
            data.put("n", 21);
            assertEquals(42, env.eval("data.get('n') * 2", self(data)));
            int sizeAfter = env.scriptCacheSize();
            assertTrue(sizeAfter == 0 || sizeAfter == sizeBefore,
                    "cache grew while disabled: " + sizeBefore + " -> " + sizeAfter);
        } finally {
            restoreProp(key, previous);
            new ScriptEnvironment().clearScriptCache();
        }
    }

    @Test
    void atRewritePipelineBehaviorUnchangedByCaching() {
        // `@` → data. rewrite happens upstream of eval; cached eval must keep identical results
        RenderEngine engine = RenderEngine.build();
        Map<String, Object> data = new HashMap<>();
        data.put("n", 21);
        for (int i = 0; i < 2; i++) {
            Object r = engine.renderJson("{\"v\":\"{= @n * 2 }\"}", data);
            assertEquals(42, ((JSONObject) r).getIntValue("v"));
        }
    }
}
