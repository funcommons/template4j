package fun.commons.template4j.core;

import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TDD 契约测试: micrometer {@code script.cache.size} gauge 真值接线 (round2 §A3,
 * {@link RenderEngine.ScriptCacheStats} 可选能力接口)。
 *
 * <p>契约 (由业务实现方保证):
 * <ul>
 *   <li>{@link RenderEngine#build()} 返回的引擎实现可选能力接口
 *       {@link RenderEngine.ScriptCacheStats} (JsonRenderer 把现有 package-private 的
 *       ScriptEnvironment 缓存探针提升为 public 能力)。</li>
 *   <li>{@code scriptCacheSize()} 返回当前编译脚本缓存条目数 (cleanUp 后 estimatedSize,
 *       引擎实例隔离)。渲染含 Groovy ({@code {= }}) 的模板后 &gt; 0; 重复渲染同一模板
 *       走缓存命中, 条目数有界 (≤ 初始 + 2), 不允许无限增长。</li>
 *   <li>{@code clearScriptCache()} 清空缓存 → 0; 再次渲染重新填充 &gt; 0。</li>
 *   <li>接口默认实现 = "不支持": {@code scriptCacheSize()} 返回 -1,
 *       {@code clearScriptCache()} 为空操作 (site 端 gauge 在不支持场景报 -1 且不 NPE)。</li>
 * </ul>
 *
 * <p>每个用例独立 {@code RenderEngine.build()}: script cache 挂在 JsonRenderer 实例上,
 * size 断言只依赖本用例自己渲染的条目, 与其他测试互不串扰。
 */
class ScriptCacheStatsTest {

    // ========== 1. 能力接口: build() 引擎实现 ScriptCacheStats ==========

    @Test
    void buildEngineImplementsScriptCacheStats() {
        RenderEngine engine = RenderEngine.build();
        assertTrue(engine instanceof RenderEngine.ScriptCacheStats,
                "build() 引擎应实现 RenderEngine.ScriptCacheStats 可选能力接口");
    }

    // ========== 2. 真值: Groovy 渲染填充缓存, 重复渲染不无限增长 ==========

    @Test
    void groovyRenderPopulatesCache_repeatedRenderStaysBounded() throws Exception {
        RenderEngine engine = RenderEngine.build();
        RenderEngine.ScriptCacheStats stats = (RenderEngine.ScriptCacheStats) engine;
        Map<String, Object> data = Map.of("a", 1);

        // 两个不同 Groovy 脚本 → 各占一条缓存条目
        engine.renderJson("{\"one\":\"{=1+1}\"}", data);
        engine.renderJson("{\"two\":\"{='x'+'y'}\"}", data);
        int initial = stats.scriptCacheSize();
        assertTrue(initial > 0, "渲染含 Groovy 的模板后 scriptCacheSize() 应 > 0, 实际: " + initial);

        // 重复渲染同一模板全部走缓存命中, 条目数必须有界 (≤ 初始 + 2), 不允许线性增长
        for (int i = 0; i < 20; i++) {
            engine.renderJson("{\"one\":\"{=1+1}\"}", data);
            engine.renderJson("{\"two\":\"{='x'+'y'}\"}", data);
        }
        int after = stats.scriptCacheSize();
        assertTrue(after <= initial + 2,
                "重复渲染同一模板不应新增缓存条目: initial=" + initial + ", after=" + after);
    }

    // ========== 3. 清空: clearScriptCache() → 0, 再渲染重新填充 ==========

    @Test
    void clearScriptCacheEmptiesCache_thenRenderRepopulates() throws Exception {
        RenderEngine engine = RenderEngine.build();
        RenderEngine.ScriptCacheStats stats = (RenderEngine.ScriptCacheStats) engine;
        // 全新引擎初始为空 (site 端启动后 gauge 初始可读且为 0 的依据)
        assertEquals(0, stats.scriptCacheSize(), "全新引擎 scriptCacheSize() 应为 0");

        Map<String, Object> data = Map.of("a", 1);
        engine.renderJson("{\"a\":\"{=21*2}\"}", data);
        assertTrue(stats.scriptCacheSize() > 0, "渲染含 Groovy 的模板后缓存应非空");

        stats.clearScriptCache();
        assertEquals(0, stats.scriptCacheSize(), "clearScriptCache() 后 scriptCacheSize() 应为 0");

        // 清空后同一模板重新编译入缓存, 结果仍正确
        JSONObject result = (JSONObject) engine.renderJson("{\"a\":\"{=21*2}\"}", data);
        // fastjson2 JSONObject 无 getInt(): 用 getIntValue (缺键返回 0 不抛错);
        // 此处键 "a" 必然存在 (引号形式单占位符原样返回 Integer 42), 语义不受影响
        assertEquals(42, result.getIntValue("a"), "清空后重新编译的脚本求值结果应仍正确");
        assertTrue(stats.scriptCacheSize() > 0, "清空后再渲染应重新填充缓存");
    }

    // ========== 4. 默认实现: 不支持 → -1 / 空操作 ==========

    @Test
    void unsupportedCapability_defaultsReportMinusOneAndClearIsNoOp() {
        // 只继承接口默认实现的匿名实现 = 不支持该能力的引擎
        RenderEngine.ScriptCacheStats unsupported = new RenderEngine.ScriptCacheStats() {};
        assertEquals(-1, unsupported.scriptCacheSize(), "不支持时 scriptCacheSize() 应返回 -1");
        assertDoesNotThrow(unsupported::clearScriptCache, "不支持时 clearScriptCache() 应为空操作");
    }
}
