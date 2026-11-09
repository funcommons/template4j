package fun.commons.template4j.core;

import com.alibaba.fastjson2.JSON;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * RenderOptions 契约测试 (TDD, 按契约先行编写, 业务实现落地前为红灯):
 * 1. RenderOptions.builder().traceConsumer(...).timeoutMs(...).build(), 字段均可选 (默认 null / 0 = 不限制)
 * 2. RenderEngine 新增重载 render(Object, Map&lt;String,Object&gt;, RenderOptions)
 * 3. options.traceConsumer 收到本次渲染的全部 trace 事件, 渲染结束后引擎不残留全局 consumer;
 *    options.timeoutMs 超时抛 RenderException, message 含 "timed out" (忽略大小写)
 * 4. 旧 API withTraceConsumer + render(t,d) 行为不变; 多次带不同 consumer 的渲染互不串扰
 */
public class RenderOptionsTest {

    private RenderEngine engine;

    @BeforeEach
    public void setUp() {
        engine = RenderEngine.build();
    }

    private static Consumer<RenderEngine.TraceEvent> collector(List<RenderEngine.TraceEvent> into) {
        return into::add;
    }

    /** 契约 2+3: options.traceConsumer 收到本次渲染的 SCRIPT 事件。 */
    @Test
    public void optionsTraceConsumerReceivesScriptEvents() throws Exception {
        List<RenderEngine.TraceEvent> events = new CopyOnWriteArrayList<>();
        RenderOptions options = RenderOptions.builder().traceConsumer(collector(events)).build();

        Object result = engine.render("{= 1 + 2 }", Map.of(), options);

        Assertions.assertEquals(3, ((Number) result).intValue());
        Assertions.assertTrue(events.stream().anyMatch(ev -> "SCRIPT".equals(ev.type())),
                "missing SCRIPT event: " + events);
    }

    /** 契约 3: 组件 (COMPONENT) 事件同样进入 options.traceConsumer。 */
    @Test
    public void optionsTraceConsumerReceivesComponentEvents() throws Exception {
        List<RenderEngine.TraceEvent> events = new CopyOnWriteArrayList<>();
        Object template = JSON.parse("""
                { "@class": "if", "select": "true", "then": "yes" }
                """);

        engine.render(template, Map.of(),
                RenderOptions.builder().traceConsumer(collector(events)).build());

        Assertions.assertTrue(events.stream().anyMatch(ev -> "COMPONENT".equals(ev.type())),
                "missing COMPONENT event: " + events);
    }

    /** 契约 3: 渲染结束后引擎上不残留全局 consumer (后续普通 render 不再收到事件)。 */
    @Test
    public void noResidualGlobalConsumerAfterOptionsRender() throws Exception {
        List<RenderEngine.TraceEvent> events = new CopyOnWriteArrayList<>();
        engine.render("first:{@v}", Map.of("v", "one"),
                RenderOptions.builder().traceConsumer(collector(events)).build());
        Assertions.assertFalse(events.isEmpty(), "options consumer 应收到本次渲染事件");

        engine.render("post:{@v}", Map.of("v", "leakcheck"));

        boolean leaked = events.stream()
                .anyMatch(ev -> String.valueOf(ev.value()).contains("leakcheck"));
        Assertions.assertFalse(leaked, "consumer 残留在引擎上, 泄漏到后续渲染: " + events);
    }

    /** 契约 3: timeoutMs=200 + 死循环脚本 → RenderException, message 含 "timed out"。 */
    @Test
    @Timeout(10)
    public void timeoutMsAbortsInfiniteScript() {
        RenderOptions options = RenderOptions.builder().timeoutMs(200L).build();
        String template = """
                {%
                  while (true) { }
                %}
                """;

        RenderException ex = Assertions.assertThrows(RenderException.class,
                () -> engine.render(template, Map.of(), options));

        String message = String.valueOf(ex.getMessage()).toLowerCase();
        Assertions.assertTrue(message.contains("timed out"), "unexpected message: " + message);
    }

    /** 契约 3: timeoutMs 未触发时渲染正常完成 (0/小阈值对快模板无影响)。 */
    @Test
    public void timeoutNotTriggeredOnFastRender() throws Exception {
        RenderOptions options = RenderOptions.builder().timeoutMs(200L).build();
        Object result = engine.render("{= 21 * 2 }", Map.of(), options);
        Assertions.assertEquals(42, ((Number) result).intValue());
    }

    /** 契约 1: 字段均可选 — 空 options 与旧 API 行为等价。 */
    @Test
    public void emptyOptionsRendersLikeLegacyApi() throws Exception {
        Object result = engine.render("Hello, {@name}!", Map.of("name", "alice"),
                RenderOptions.builder().build());
        Assertions.assertEquals("Hello, alice!", result);
    }

    /** 契约 4: 同一引擎多次带不同 traceConsumer 的渲染互不污染。 */
    @Test
    public void separateRendersDoNotCrossPolluteConsumers() throws Exception {
        List<RenderEngine.TraceEvent> gotA = new CopyOnWriteArrayList<>();
        List<RenderEngine.TraceEvent> gotB = new CopyOnWriteArrayList<>();

        engine.render("A:{@name}", Map.of("name", "alice"),
                RenderOptions.builder().traceConsumer(collector(gotA)).build());
        engine.render("B:{@name}", Map.of("name", "bob"),
                RenderOptions.builder().traceConsumer(collector(gotB)).build());

        Assertions.assertTrue(gotA.stream().anyMatch(ev -> String.valueOf(ev.value()).contains("alice")),
                "A missing own event: " + gotA);
        Assertions.assertTrue(gotB.stream().anyMatch(ev -> String.valueOf(ev.value()).contains("bob")),
                "B missing own event: " + gotB);
        Assertions.assertTrue(gotA.stream().noneMatch(ev -> String.valueOf(ev.value()).contains("bob")),
                "A 的 consumer 收到了 B 的事件: " + gotA);
        Assertions.assertTrue(gotB.stream().noneMatch(ev -> String.valueOf(ev.value()).contains("alice")),
                "B 的 consumer 收到了 A 的事件: " + gotB);
    }

    /** 契约 3: 旧 API withTraceConsumer + render(t,d) 行为不变 (回归)。 */
    @Test
    public void legacyWithTraceConsumerStillWorks() throws Exception {
        List<RenderEngine.TraceEvent> events = new CopyOnWriteArrayList<>();
        engine.withTraceConsumer(collector(events));

        Object result = engine.render("Hello, {@name}!", Map.of("name", "carol"));

        Assertions.assertEquals("Hello, carol!", result);
        Assertions.assertTrue(events.stream().anyMatch(ev -> "RENDER_STRING".equals(ev.type())
                        && String.valueOf(ev.value()).contains("carol")),
                "missing RENDER_STRING event: " + events);
    }
}
