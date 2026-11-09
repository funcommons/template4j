package fun.commons.template4j.core;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import fun.commons.template4j.core.RenderEngine.Renderer;
import fun.commons.template4j.core.RenderEngine.TraceEvent;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * TraceEvent 零分配门 (traceEnabled) 契约测试 (TDD, 按规格先行编写):
 *
 * 1. Renderer 接口新增 default boolean traceEnabled() { return true; };
 *    JsonRenderer 覆写为 !traceConsumers.isEmpty()。
 * 2. 各 trace 调用点 (renderString / renderMap 组件计时 / RestRenderer / RenderStream /
 *    ExpressionEvaluator) 先判 traceEnabled() 再构造 TraceEvent —— 无 consumer 时零事件对象分配。
 * 3. 行为不变: 有 consumer 时事件流与守卫前完全一致 (守卫只在无 consumer 时生效)。
 *
 * 对应优化计划第二轮 P2。trace() 内部的 isEmpty 检查发生在事件对象分配之后,
 * 本测试把 "注册/反注册 ⇄ traceEnabled 翻转" 与 "事件流不受守卫影响" 钉死。
 */
public class TraceEnabledTest {

    /** 经 Renderer 接口读取 (与调用点 self.renderer.traceEnabled() 同一形态)。 */
    private static Renderer rendererOf(RenderEngine engine) {
        return (Renderer) engine;
    }

    private static Consumer<TraceEvent> collector(List<TraceEvent> into) {
        return into::add;
    }

    /** 含组件 + 字符串 + 脚本的模板: 覆盖 COMPONENT / RENDER_STRING / SCRIPT 三类事件路径。 */
    private static final String COMPONENT_TEMPLATE = """
            {
              "greeting": "Hi, {@name}!",
              "calc": "{= 6 * 7 }",
              "flag": { "@class": "if", "select": "true", "then": "yes", "else": "no" },
              "list": { "@class": "each", "select": "{@nums}", "body": "n={$item}" }
            }
            """;

    private static final Map<String, Object> DATA =
            Map.of("name", "alice", "nums", List.of(1, 2, 3));

    private static void assertComponentTemplateResult(Object result) {
        JSONObject obj = (JSONObject) result;
        Assertions.assertEquals("Hi, alice!", obj.getString("greeting"));
        Assertions.assertEquals(42, ((Number) obj.get("calc")).intValue());
        Assertions.assertEquals("yes", obj.getString("flag"));
        Assertions.assertEquals(JSONArray.of("n=1", "n=2", "n=3"), obj.get("list"));
    }

    /** 规格 1: build() 默认无 consumer → traceEnabled()==false; 注册后 true; close 后回到 false。 */
    @Test
    public void engineDefaultsToDisabledUntilConsumerAddedAndClosed() throws Exception {
        RenderEngine engine = RenderEngine.build();
        Assertions.assertFalse(rendererOf(engine).traceEnabled(),
                "默认无 consumer, traceEnabled 应为 false");

        List<TraceEvent> events = new CopyOnWriteArrayList<>();
        RenderEngine.TraceHandle handle = engine.addTraceConsumer(collector(events));
        Assertions.assertTrue(rendererOf(engine).traceEnabled(),
                "注册 consumer 后 traceEnabled 应为 true");

        handle.close();
        Assertions.assertFalse(rendererOf(engine).traceEnabled(),
                "handle.close() 反注册后 traceEnabled 应回到 false");

        // 行为侧钉死: 关闭后继续渲染, 已反注册的 consumer 不再收到任何事件 (门真正生效)
        engine.render("post:{@v}", Map.of("v", "afterClose"));
        Assertions.assertTrue(events.isEmpty(),
                "close 之后 trace 门应挡住事件, 不应有事件流入: " + events);
    }

    /** 规格 1: 两个 consumer 关一个仍 true, 全关才 false。 */
    @Test
    public void traceStaysEnabledWhileAnyConsumerRemains() throws Exception {
        RenderEngine engine = RenderEngine.build();
        RenderEngine.TraceHandle h1 = engine.addTraceConsumer(ev -> {});
        RenderEngine.TraceHandle h2 = engine.addTraceConsumer(ev -> {});
        Assertions.assertTrue(rendererOf(engine).traceEnabled());

        h1.close();
        Assertions.assertTrue(rendererOf(engine).traceEnabled(),
                "还剩一个 consumer 时 traceEnabled 应保持 true");

        h2.close();
        Assertions.assertFalse(rendererOf(engine).traceEnabled(),
                "全部反注册后 traceEnabled 应为 false");
    }

    /** 规格 2: withTraceConsumer 兼容路径注册后 ==true (常驻, 无 handle, 不自动移除)。 */
    @Test
    public void legacyWithTraceConsumerEnablesTracePermanently() throws Exception {
        RenderEngine engine = RenderEngine.build();
        List<TraceEvent> events = new CopyOnWriteArrayList<>();
        engine.withTraceConsumer(collector(events));

        Assertions.assertTrue(rendererOf(engine).traceEnabled(),
                "withTraceConsumer(常驻) 注册后 traceEnabled 应为 true");

        // 常驻语义: 多次渲染事件持续流入, traceEnabled 不回落
        engine.render("a:{@v}", Map.of("v", 1));
        engine.render("b:{@v}", Map.of("v", 2));
        Assertions.assertFalse(events.isEmpty());
        Assertions.assertTrue(rendererOf(engine).traceEnabled(),
                "常驻 consumer 不应被自动移除");
    }

    /** 规格 3: 行为不变 —— 注册 consumer 渲染组件模板, 事件流仍含 COMPONENT 与 RENDER_STRING。 */
    @Test
    public void enabledTraceStillDeliversComponentAndStringEvents() throws Exception {
        RenderEngine engine = RenderEngine.build();
        List<TraceEvent> events = new CopyOnWriteArrayList<>();
        engine.addTraceConsumer(collector(events));

        Object result = engine.renderJson(COMPONENT_TEMPLATE, DATA);

        // 事件流不因守卫改变: 各类事件照常到达
        Assertions.assertTrue(events.stream().anyMatch(ev -> "COMPONENT".equals(ev.type())),
                "missing COMPONENT event: " + events);
        Assertions.assertTrue(events.stream().anyMatch(ev -> "RENDER_STRING".equals(ev.type())),
                "missing RENDER_STRING event: " + events);
        Assertions.assertTrue(events.stream().anyMatch(ev -> "SCRIPT".equals(ev.type())),
                "missing SCRIPT event: " + events);

        // 同时渲染结果正确 (守卫不得改变渲染行为)
        assertComponentTemplateResult(result);
    }

    /** 规格 4: 请求级 RenderOptions.traceConsumer —— 渲染期间 traceEnabled==true 且事件流入;
     *  渲染结束后引擎 traceEnabled() 回到 false (无残留)。 */
    @Test
    public void requestScopedConsumerEnablesTraceDuringRenderOnly() throws Exception {
        RenderEngine engine = RenderEngine.build();
        Assertions.assertFalse(rendererOf(engine).traceEnabled());

        List<TraceEvent> events = new CopyOnWriteArrayList<>();
        AtomicBoolean enabledDuringRender = new AtomicBoolean(false);
        Consumer<TraceEvent> consumer = ev -> {
            enabledDuringRender.set(rendererOf(engine).traceEnabled());
            events.add(ev);
        };

        engine.renderJson(COMPONENT_TEMPLATE, DATA,
                RenderOptions.builder().traceConsumer(consumer).build());

        Assertions.assertFalse(events.isEmpty(), "options consumer 应收到本次渲染事件");
        Assertions.assertTrue(enabledDuringRender.get(),
                "渲染期间 (consumer 回调内) traceEnabled 应为 true");

        // 渲染结束自动反注册: 引擎无残留
        Assertions.assertFalse(rendererOf(engine).traceEnabled(),
                "渲染结束后 traceEnabled 应回到 false (请求级订阅零残留)");
    }

    /** 规格 5: 无 consumer 渲染正确性 —— 守卫路径不破坏渲染。 */
    @Test
    public void renderWithoutConsumersProducesCorrectResult() throws Exception {
        RenderEngine engine = RenderEngine.build();
        Assertions.assertFalse(rendererOf(engine).traceEnabled());

        Object result = engine.renderJson(COMPONENT_TEMPLATE, DATA);

        assertComponentTemplateResult(result);
    }

    /** 规格 6: 多 consumer 广播 —— 两个 consumer 都收到事件。 */
    @Test
    public void multipleConsumersAllReceiveEvents() throws Exception {
        RenderEngine engine = RenderEngine.build();
        List<TraceEvent> gotA = new CopyOnWriteArrayList<>();
        List<TraceEvent> gotB = new CopyOnWriteArrayList<>();
        engine.addTraceConsumer(collector(gotA));
        engine.addTraceConsumer(collector(gotB));
        Assertions.assertTrue(rendererOf(engine).traceEnabled());

        engine.render("Hi, {@name}!", Map.of("name", "bob"));

        Assertions.assertFalse(gotA.isEmpty(), "consumer A 未收到事件");
        Assertions.assertFalse(gotB.isEmpty(), "consumer B 未收到事件");
        Assertions.assertTrue(gotA.stream().anyMatch(ev ->
                        "RENDER_STRING".equals(ev.type())
                                && String.valueOf(ev.value()).contains("bob")));
        Assertions.assertTrue(gotB.stream().anyMatch(ev ->
                        "RENDER_STRING".equals(ev.type())
                                && String.valueOf(ev.value()).contains("bob")));
    }
}
