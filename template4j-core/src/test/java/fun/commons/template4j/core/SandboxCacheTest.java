package fun.commons.template4j.core;

import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TDD 契约测试: 沙箱路径的编译缓存 (round2 §P1, {@link ScriptEnvironment} 沙箱分支)。
 *
 * <p>契约 (由业务实现方保证):
 * <ul>
 *   <li>沙箱分支缓存 Groovy {@code Script} 编译产物, key = {@code importsVersion + "\0" + script};
 *       命中后每线程 {@code newInstance(bindings)} 克隆执行 (Script 实例非线程安全但可反复 newInstance)。</li>
 *   <li>包级探针 {@code sandboxCacheSize()} (cleanUp 后 estimatedSize) / {@code clearSandboxCache()}。</li>
 *   <li>缓存只省编译 + SandboxTransformer 重写成本: 拦截器仍每 eval 注册/注销,
 *       缓存命中路径下违规调用 (File / Runtime / ...) 依旧被白名单拦截 (message 含 "sandbox")。</li>
 *   <li>{@code template4j.scriptCache.enabled=false} → 沙箱路径不缓存 (size 恒 0) 且沙箱语义不变。</li>
 *   <li>{@code template4j.scriptCache.maxEntries} 同样约束沙箱缓存, 淘汰脚本重新编译后仍正确。</li>
 *   <li>addImport 递增 importsVersion → 同一脚本文本按新 key 重新编译, 新 import 的类可用。</li>
 * </ul>
 *
 * <p>属性在 {@code new ScriptEnvironment()} 构造时读取, 测试用 save/restore 模式 (finally 恢复原值)。
 * 注意: surefire 会注入 {@code template4j.rest.allowed-hosts} 等他人属性, 只允许 save/restore
 * 本测试设置的 key, 绝不能 {@code System.clearProperty} 别人的属性。
 * 与 {@link ScriptEnvironmentCacheTest} 同构: 每个 size 敏感测试先 {@code clearSandboxCache()},
 * 属性敏感测试在 finally 里再建一个默认实例清缓存, 保证断言不依赖实例是否共享存储。
 */
class SandboxCacheTest {

    private static SelfObject self(Map<String, Object> data) {
        return new SelfObject(new JsonRenderer(), data);
    }

    private static Map<String, Object> dataOf(String key, Object value) {
        Map<String, Object> data = new HashMap<>();
        data.put(key, value);
        return data;
    }

    /** 沙箱开启 + 缓存已清空的全新实例 (size 断言只依赖本测试产生的条目)。 */
    private static ScriptEnvironment sandboxedEnv() {
        ScriptEnvironment env = new ScriptEnvironment();
        env.setSandboxEnabled(true);
        env.clearSandboxCache();
        return env;
    }

    private static void restoreProp(String key, String previous) {
        if (previous == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, previous);
        }
    }

    private static void assertSandboxBlocked(ScriptEnvironment env, Map<String, Object> data, String script) {
        RenderException ex = assertThrows(RenderException.class, () -> env.eval(script, self(data)));
        assertNotNull(ex.getMessage(), "RenderException message should not be null");
        assertTrue(ex.getMessage().toLowerCase().contains("sandbox"),
                "message should mention sandbox: " + ex.getMessage());
    }

    // ========== 1. 正确性: 沙箱开启 + 缓存下, 同一脚本多次 eval 结果一致且正确 ==========

    @Test
    void sandboxedScripts_evaluatedTwicePerScript_resultsConsistentAndCorrect() throws Exception {
        ScriptEnvironment env = sandboxedEnv();
        Map<String, Object> data = dataOf("n", 21);
        // 第二轮全部走缓存命中路径; 脚本形态与 SandboxRenderTest 的引擎级用例一一对应
        for (int round = 0; round < 2; round++) {
            assertEquals(3, env.eval("1 + 2", self(data)));
            assertEquals("ab", env.eval("'a' + 'b'", self(data)));
            assertEquals(42, ((Number) env.eval("data.get('n') * 2", self(data))).intValue());
            assertEquals(22, ((Number) env.eval("data.n + 1", self(data))).intValue());
            assertEquals(Boolean.TRUE, env.eval("StringUtils.isBlank('')", self(data)));
            assertEquals(Boolean.TRUE, env.eval("new JSONObject().isEmpty()", self(data)));
            assertEquals("a", env.eval("fne(null, 'a')", self(data)));
        }
        assertTrue(env.sandboxCacheSize() >= 7,
                "distinct scripts should occupy distinct entries, got " + env.sandboxCacheSize());
    }

    // ========== 2. 缓存生效: 同一脚本两次 → 1 条; 不同脚本 → 增加 ==========

    @Test
    void sameScriptEvaluatedTwice_singleSandboxEntry_distinctScriptGrowsEntry() throws Exception {
        ScriptEnvironment env = sandboxedEnv();
        Map<String, Object> data = dataOf("n", 21);
        assertEquals(42, ((Number) env.eval("data.get('n') * 2", self(data))).intValue());
        assertEquals(1, env.sandboxCacheSize());
        assertEquals(42, ((Number) env.eval("data.get('n') * 2", self(data))).intValue());
        assertEquals(1, env.sandboxCacheSize(), "hit path must reuse the entry, not add one");
        assertEquals("ab", env.eval("'a' + 'b'", self(data)));
        assertEquals(2, env.sandboxCacheSize(), "distinct script text must get its own entry");
    }

    @Test
    void clearSandboxCache_emptiesEntriesAndStillEvaluatesCorrectly() throws Exception {
        ScriptEnvironment env = sandboxedEnv();
        Map<String, Object> data = dataOf("n", 21);
        assertEquals(42, ((Number) env.eval("data.get('n') * 2", self(data))).intValue());
        assertEquals("xy", env.eval("'x' + 'y'", self(data)));
        assertEquals(2, env.sandboxCacheSize());
        env.clearSandboxCache();
        assertEquals(0, env.sandboxCacheSize());
        // 清空后重新编译, 结果仍正确
        assertEquals(42, ((Number) env.eval("data.get('n') * 2", self(data))).intValue());
        assertEquals(1, env.sandboxCacheSize());
    }

    // ========== 3. imports 失效: addImport 递增版本 → 同文本重新编译, 新类可用 ==========

    @Test
    void addImport_bumpsImportsVersion_recompilesSameTextAndExposesNewClass() throws Exception {
        ScriptEnvironment env = sandboxedEnv();
        Map<String, Object> data = new HashMap<>();
        assertEquals("ab", env.eval("'a' + 'b'", self(data)));
        assertEquals(1, env.sandboxCacheSize());

        // java.time 不在默认 imports 中; addImport 后 importsVersion++ → 同文本 key 变化
        env.addImport("java.time.LocalTime");

        // 同一脚本文本重新编译 (旧条目按 key 自然失效, 新条目追加)
        assertEquals("ab", env.eval("'a' + 'b'", self(data)));
        assertEquals(2, env.sandboxCacheSize(),
                "same text after addImport must recompile into a new entry");
        // 新 import 的类在脚本中可解析 (裸类名引用, 返回 Class 对象)
        assertSame(LocalTime.class, env.eval("LocalTime", self(data)));
        assertEquals(3, env.sandboxCacheSize());
    }

    // ========== 4. 沙箱语义保持: 缓存命中路径下违规调用仍被拦截 ==========

    @Test
    void cacheHitPath_violatingCallsStillBlockedByWhitelist() throws Exception {
        ScriptEnvironment env = sandboxedEnv();
        Map<String, Object> data = dataOf("n", 21);
        // 良性脚本先入缓存并命中一次, 保证后续断言发生在缓存已生效的实例上
        assertEquals("ab", env.eval("'a' + 'b'", self(data)));
        assertEquals("ab", env.eval("'a' + 'b'", self(data)));

        // 违规脚本: 编译 (含 SandboxTransformer 重写) 成功并按 miss 入缓存, 运行期被白名单拦截
        assertSandboxBlocked(env, data, "new File('x')");
        assertEquals(2, env.sandboxCacheSize(),
                "violating script must be cached at compile time so the next eval is a hit");

        // 缓存命中路径的第二次 eval 仍被拦截 — 缓存只省编译, 不绕过白名单
        assertSandboxBlocked(env, data, "new File('x')");

        // 违规 eval 前后, 良性脚本与拦截器状态不受影响
        assertEquals("ab", env.eval("'a' + 'b'", self(data)));
        assertEquals(2, env.sandboxCacheSize());
    }

    // ========== 5. 开关: enabled=false → 沙箱路径不缓存但行为 (含拦截) 不变 ==========

    @Test
    void scriptCacheDisabled_sandboxPathCorrectNoEntriesStillBlocksViolations() throws Exception {
        String key = "template4j.scriptCache.enabled";
        String previous = System.getProperty(key);
        System.setProperty(key, "false");
        try {
            ScriptEnvironment env = new ScriptEnvironment();
            env.setSandboxEnabled(true);
            env.clearSandboxCache();
            Map<String, Object> data = dataOf("n", 21);
            assertEquals(42, ((Number) env.eval("data.get('n') * 2", self(data))).intValue());
            assertEquals(22, ((Number) env.eval("data.n + 1", self(data))).intValue());
            assertEquals(42, ((Number) env.eval("data.get('n') * 2", self(data))).intValue());
            assertEquals(0, env.sandboxCacheSize(), "disabled cache must never grow");
            // 不缓存不等于削弱沙箱: 违规调用依旧拦截
            assertSandboxBlocked(env, data, "new File('x')");
        } finally {
            restoreProp(key, previous);
            new ScriptEnvironment().clearSandboxCache();
        }
    }

    // ========== 6. 并发: 多线程 eval 同一脚本, 结果全部正确 (newInstance 克隆语义) ==========

    @Test
    void concurrentEvals_sameScript_allResultsCorrectFromClonedInstances() throws Exception {
        ScriptEnvironment env = sandboxedEnv();
        // 预热一次, 让并发阶段全部走 "缓存命中 + newInstance 克隆" 路径
        assertEquals(2, ((Number) env.eval("data.get('n') * 2", self(dataOf("n", 1)))).intValue());

        int threads = 8;
        int iterations = 25;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<?>> futures = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                final int n = t + 1;
                futures.add(pool.submit(() -> {
                    start.await();
                    for (int i = 0; i < iterations; i++) {
                        Object result = env.eval("data.get('n') * 2", self(dataOf("n", n)));
                        assertEquals(n * 2, ((Number) result).intValue(),
                                "cloned script returned wrong value for n=" + n);
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }
        assertEquals(1, env.sandboxCacheSize(), "all concurrent evals must share one cache entry");
    }

    // ========== 7. 克隆隔离: 两次 eval 间 bindings 不串 ==========

    @Test
    void clonedInstances_dataAndVarsBindingsDoNotLeakBetweenEvals() throws Exception {
        ScriptEnvironment env = sandboxedEnv();
        // 同一脚本交替使用不同 data: 命中路径每次重绑, 不得残留上一次的值
        for (int i = 0; i < 3; i++) {
            assertEquals(10, ((Number) env.eval("data.get('n') + 5", self(dataOf("n", 5)))).intValue());
            assertEquals(25, ((Number) env.eval("data.get('n') + 5", self(dataOf("n", 20)))).intValue());
            assertEquals(10, ((Number) env.eval("data.get('n') + 5", self(dataOf("n", 5)))).intValue());
        }
        // vars 纬度同样独立: 不同 SelfObject 的变量各自可见、互不串
        SelfObject s1 = self(dataOf("n", 1));
        SelfObject s2 = self(dataOf("n", 2));
        s1.getVarMap().put("k", 9);
        s2.getVarMap().put("k", 99);
        assertEquals(9, env.eval("vars.get('k')", s1));
        assertEquals(99, env.eval("vars.get('k')", s2));
        assertEquals(9, env.eval("vars.get('k')", s1));
        // 整个测试只用了同一脚本文本两次 + vars 脚本一次, 共 2 个条目
        assertEquals(2, env.sandboxCacheSize());
    }

    // ========== 8. maxEntries 同样约束沙箱缓存, 淘汰后重编译仍正确 ==========

    @Test
    void maxEntriesProperty_capsSandboxCache_evictedScriptStillEvaluates() throws Exception {
        String key = "template4j.scriptCache.maxEntries";
        String previous = System.getProperty(key);
        System.setProperty(key, "2");
        try {
            ScriptEnvironment env = sandboxedEnv();
            Map<String, Object> data = new HashMap<>();
            assertEquals(1, env.eval("1", self(data)));
            assertEquals(2, env.eval("1 + 1", self(data)));
            assertEquals(3, env.eval("1 + 1 + 1", self(data)));
            assertTrue(env.sandboxCacheSize() <= 2,
                    "size " + env.sandboxCacheSize() + " exceeds maxEntries=2");
            // 被淘汰的脚本重新编译后仍正确
            assertEquals(1, env.eval("1", self(data)));
        } finally {
            restoreProp(key, previous);
            new ScriptEnvironment().clearSandboxCache();
        }
    }
}
