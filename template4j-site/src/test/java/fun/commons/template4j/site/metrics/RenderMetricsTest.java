package fun.commons.template4j.site.metrics;

import fun.commons.template4j.core.RenderEngine;
import fun.commons.template4j.site.SiteApplication;
import fun.commons.template4j.site.dto.RenderRequest;
import fun.commons.template4j.site.service.TemplateService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RenderMetrics TDD 契约测试 (@SpringBootTest, webEnvironment=NONE, 复用真实上下文)。
 *
 * <p>实现契约 (业务代码需满足):
 * <ul>
 *   <li>@Component RenderMetrics, 构造注入 MeterRegistry, 在渲染路径注册/递增指标</li>
 *   <li>Counter {@code template4j.render.total}, tag {@code type} (json/yaml/string),
 *       每次 TemplateService.render 成功后 +1</li>
 *   <li>Timer {@code template4j.render.duration}, 记录每次渲染耗时 (@Timer 或手动 record 均可)</li>
 *   <li>Gauge {@code template4j.script.cache.size} (round2 §A3 真值接线): RenderMetrics 构造注入
 *       {@link RenderEngine.ScriptCacheStats} (TemplateEngineConfig 传入 engine bean),
 *       gauge 每次查询实时读取 {@code scriptCacheSize()} 真值 (非常量占位);
 *       渲染含 Groovy ({@code {= }}) 的模板后 &gt; 0; 不支持场景 (仅接口默认实现) 报 -1 且不 NPE</li>
 * </ul>
 *
 * <p>counter/timer 断言用 {@code >=} 而非 {@code ==}: 同一上下文被其它测试类复用时计数可累加。
 */
@SpringBootTest(classes = SiteApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class RenderMetricsTest {

    private static final String COUNTER = "template4j.render.total";
    private static final String TIMER = "template4j.render.duration";
    private static final String GAUGE = "template4j.script.cache.size";

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private TemplateService templateService;

    private void render(String type, String template) {
        templateService.render(new RenderRequest(type, template, Map.of("a", 1)));
    }

    @Test
    void renderIncrementsTotalCounterByType() {
        render("json", "{\"a\":{@a}}");
        double count = meterRegistry.get(COUNTER).tag("type", "json").counter().count();
        assertTrue(count >= 1, "成功渲染 json 后 template4j.render.total{type=json} 应 >= 1, 实际: " + count);
    }

    @Test
    void timerIsRegistered() {
        render("json", "{\"a\":{@a}}");
        // get(...).timer() 在指标缺失时抛 NoSuchElementException, 即测试失败
        long count = meterRegistry.get(TIMER).timer().count();
        assertTrue(count >= 1, "渲染一次后 template4j.render.duration 的 count 应 >= 1, 实际: " + count);
    }

    @Test
    void scriptCacheGaugeIsRegistered() {
        // 启动后即可查询, 无需先渲染
        double value = meterRegistry.get(GAUGE).gauge().value();
        assertTrue(value >= 0, "template4j.script.cache.size 应已注册且值 >= 0, 实际: " + value);
    }

    @Test
    void groovyRenderReflectsInScriptCacheGauge() {
        // round2 §A3: gauge 必须是 engine 的 scriptCacheSize() 真值, 渲染含 Groovy 的模板后 > 0
        // (常量占位 0 的旧实现在此失败)
        render("json", "{\"sum\":{=40+2}}");
        double value = meterRegistry.get(GAUGE).gauge().value();
        assertTrue(value > 0, "渲染含 Groovy 的模板后 gauge 应为引擎真值 > 0, 实际: " + value);
    }

    @Test
    void scriptCacheGaugeReadsThroughInjectedStats() {
        // 直接构造 (独立 registry, 不经 Spring 共享上下文): 构造器注入 RenderEngine.ScriptCacheStats,
        // gauge 每次查询实时读取 stats 真值, 而非注册时的常量
        AtomicInteger liveSize = new AtomicInteger(7);
        RenderEngine.ScriptCacheStats stats = new RenderEngine.ScriptCacheStats() {
            @Override
            public int scriptCacheSize() {
                return liveSize.get();
            }
        };
        SimpleMeterRegistry localRegistry = new SimpleMeterRegistry();
        new RenderMetrics(localRegistry, stats);

        assertEquals(7.0, localRegistry.get(GAUGE).gauge().value(), 0.0,
                "gauge 应读注入 stats 的真值 (真值接线, 非常量占位)");
        liveSize.set(9);
        assertEquals(9.0, localRegistry.get(GAUGE).gauge().value(), 0.0,
                "gauge 应随 stats 动态变化 (每次查询实时读取)");
    }

    @Test
    void unsupportedScriptCacheStatsGaugeReportsMinusOne() {
        // 不支持场景: 仅接口默认实现 (scriptCacheSize()=-1, clearScriptCache() 空操作),
        // 构造 / 记录指标 / 读取 gauge 均不得 NPE, gauge 报告 -1
        RenderEngine.ScriptCacheStats unsupported = new RenderEngine.ScriptCacheStats() {};
        SimpleMeterRegistry localRegistry = new SimpleMeterRegistry();
        RenderMetrics metrics = new RenderMetrics(localRegistry, unsupported);

        assertDoesNotThrow(() -> metrics.recordSuccess("json", 1));
        assertEquals(-1.0, localRegistry.get(GAUGE).gauge().value(), 0.0,
                "不支持场景 gauge 应报告 -1 且不 NPE");
    }

    @Test
    void yamlRenderTagsTypeYaml() {
        render("yaml", "a: \"{@a}\"");
        double count = meterRegistry.get(COUNTER).tag("type", "yaml").counter().count();
        assertTrue(count >= 1, "成功渲染 yaml 后 template4j.render.total{type=yaml} 应 >= 1, 实际: " + count);
    }
}
