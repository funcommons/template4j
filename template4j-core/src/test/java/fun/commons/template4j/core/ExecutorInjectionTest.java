package fun.commons.template4j.core;

import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * TDD 契约测试: 引擎级 Executor 注入。
 *
 * <p>契约:
 * <ol>
 *   <li>{@code RenderEngine.withExecutor(ExecutorService)} 引擎级注入; 传 null 恢复默认。</li>
 *   <li>{@code @class: parallel} 的 task 执行改用注入的 executor; 未注入时保持内部默认池行为不变。</li>
 *   <li>不注入时所有现有模板渲染行为不变; 注入也不影响同步 (非 parallel) 渲染路径。</li>
 *   <li>executor 按引擎实例隔离, 不跨引擎共享。</li>
 * </ol>
 *
 * <p>记录型 executor 基于 {@link AbstractExecutorService}: 只需实现 {@code execute()}
 * (计数后在调用线程同步执行), {@code submit()} 等默认方法会委托回 {@code execute()},
 * 因此无论实现走 execute 还是 submit, 计数都准确。
 */
public class ExecutorInjectionTest {

    /** 两个轻量 const task 的 parallel 模板, 结果经 body 用 {$taskName} 取出。 */
    private static final String PARALLEL_TEMPLATE = """
            {
              "@class": "parallel",
              "tasks": {
                "a": { "@class": "const", "select": "A1" },
                "b": { "@class": "const", "select": "B1" }
              },
              "body": {
                "x": "{$a}",
                "y": "{$b}"
              }
            }
            """;

    /** 记录型 executor: execute() 计数后在当前线程直接同步执行 (direct)。 */
    private static ExecutorService recordingExecutor(AtomicInteger counter) {
        return new AbstractExecutorService() {
            @Override public void execute(Runnable command) {
                counter.incrementAndGet();
                command.run();
            }
            @Override public void shutdown() { }
            @Override public List<Runnable> shutdownNow() { return List.of(); }
            @Override public boolean isShutdown() { return false; }
            @Override public boolean isTerminated() { return false; }
            @Override public boolean awaitTermination(long timeout, TimeUnit unit) { return true; }
        };
    }

    private static void assertParallelResult(Object r) {
        Assertions.assertTrue(r instanceof JSONObject, "parallel render should yield JSONObject, got: " + r);
        JSONObject obj = (JSONObject) r;
        Assertions.assertEquals("A1", obj.getString("x"));
        Assertions.assertEquals("B1", obj.getString("y"));
    }

    @Test
    public void injectedExecutorIsUsedForParallelTasks() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        RenderEngine engine = RenderEngine.build().withExecutor(recordingExecutor(calls));

        Object r = engine.renderJson(PARALLEL_TEMPLATE, Map.of());

        assertParallelResult(r);
        Assertions.assertTrue(calls.get() >= 2,
                "injected executor should run both parallel tasks, got " + calls.get() + " execute() calls");
    }

    @Test
    public void defaultEngineStillRendersParallelWithoutInjection() throws Exception {
        RenderEngine engine = RenderEngine.build();

        Object r = engine.renderJson(PARALLEL_TEMPLATE, Map.of());

        assertParallelResult(r);
    }

    @Test
    public void injectedExecutorRendersSequentialTemplateToo() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        RenderEngine engine = RenderEngine.build().withExecutor(recordingExecutor(calls));

        Object r = engine.renderJson("{\"hello\":\"{@who}\"}", Map.of("who", "world"));

        Assertions.assertTrue(r instanceof JSONObject);
        Assertions.assertEquals("world", ((JSONObject) r).getString("hello"));
        Assertions.assertEquals(0, calls.get(),
                "synchronous (non-parallel) render must not go through the injected executor");
    }

    @Test
    public void injectedExecutorIsNotSharedAcrossEngines() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        RenderEngine engineA = RenderEngine.build().withExecutor(recordingExecutor(calls));
        RenderEngine engineB = RenderEngine.build();

        assertParallelResult(engineA.renderJson(PARALLEL_TEMPLATE, Map.of()));
        int afterA = calls.get();
        Assertions.assertTrue(afterA >= 2, "engine A should use its own injected executor");

        assertParallelResult(engineB.renderJson(PARALLEL_TEMPLATE, Map.of()));
        Assertions.assertEquals(afterA, calls.get(),
                "engine B (default pool) must not touch engine A's injected executor");
    }

    @Test
    public void withExecutorNullRestoresDefault() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        RenderEngine engine = RenderEngine.build().withExecutor(recordingExecutor(calls));

        assertParallelResult(engine.renderJson(PARALLEL_TEMPLATE, Map.of()));
        int afterInjected = calls.get();
        Assertions.assertTrue(afterInjected >= 2);

        engine.withExecutor(null);

        assertParallelResult(engine.renderJson(PARALLEL_TEMPLATE, Map.of()));
        Assertions.assertEquals(afterInjected, calls.get(),
                "withExecutor(null) must fall back to the internal default pool");
    }
}
