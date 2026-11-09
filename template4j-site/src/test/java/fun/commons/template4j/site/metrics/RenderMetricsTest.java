package fun.commons.template4j.site.metrics;

import fun.commons.template4j.site.SiteApplication;
import fun.commons.template4j.site.dto.RenderRequest;
import fun.commons.template4j.site.service.TemplateService;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;

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
 *   <li>Gauge {@code template4j.script.cache.size} (挂到 ScriptEnvironment 或等价挂载点),
 *       应用启动后即已注册, 值 >= 0</li>
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
    void yamlRenderTagsTypeYaml() {
        render("yaml", "a: \"{@a}\"");
        double count = meterRegistry.get(COUNTER).tag("type", "yaml").counter().count();
        assertTrue(count >= 1, "成功渲染 yaml 后 template4j.render.total{type=yaml} 应 >= 1, 实际: " + count);
    }
}
