package fun.commons.template4j.core;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * 超时渲染线程策略的行为契约测试 (round2 §P3: 超时路径每调用 {@code new Thread} 改为自适应线程策略)。
 *
 * <p>改造点: {@code JsonRenderer.render(template, data, RenderOptions)} 在 {@code timeoutMs > 0} 时,
 * 运行时 JDK &ge; 21 用虚拟线程 ({@code Thread.ofVirtual()}), 否则回退共享 daemon 池。
 * 对调用方的可观察语义必须一字不变, 因此本文件只做行为性断言:
 *
 * <ol>
 *   <li>超时抛 {@link RenderException}, message 含精确的 "timed out after Nms"。</li>
 *   <li>deadline 未触发的快渲染结果不受影响。</li>
 *   <li>并发下各次渲染的超时判定互不串扰。</li>
 *   <li>超时渲染与正常渲染并发混跑互不影响。</li>
 *   <li>{@code timeoutMs = 0} 不走超时路径, 慢模板也正常完成。</li>
 *   <li>同一引擎连续大量超时渲染不出现串行化 / 泄漏式劣化。</li>
 * </ol>
 *
 * <p>刻意<b>不断言</b>线程类型 / 线程名 &mdash; 实现按运行时 JDK 自适应
 * (虚拟线程或共享 daemon 池), 本文件对两种实现都必须绿。耗时断言只保留规格要求的粗上界。
 *
 * <p>慢模板写法选用 Groovy {@code Thread.sleep(N)} 而非 {@code while(true){}}。依据:
 * <ul>
 *   <li>沙箱白名单 ({@code GroovySandbox.Whitelist}) 对静态调用只放行 {@code Math} /
 *       {@code StringUtils}, 且禁全部循环语句 &mdash; 两种写法在沙箱开启时都会被拒。
 *       本测试用默认 {@code RenderEngine.build()} (沙箱默认关), {@code java.lang.Thread}
 *       经 Groovy 默认导入可直接使用, 无需顾虑。</li>
 *   <li>sleep 对 interrupt 响应: 超时路径 {@code future.cancel(true)} 的中断会让 worker
 *       立即退出; 死循环则每个超时渲染遗留一个永久空转线程, 会污染 200 连发的
 *       劣化观察测试 (测试自身的资源开销会淹没被测对象的表现)。</li>
 * </ul>
 */
public class TimeoutThreadStrategyTest {

    /** 慢模板: sleep 远超本文件所有 deadline (100ms ~ 5000ms); deadline 失效时会挂满 15s 后返回 'never'。 */
    private static final String HANGING_TEMPLATE = """
            {%
              Thread.sleep(15000)
              return 'never'
            %}
            """;

    private RenderEngine engine;

    @BeforeEach
    public void setUp() {
        engine = RenderEngine.build();
    }

    private static RenderOptions timeout(long ms) {
        return RenderOptions.builder().timeoutMs(ms).build();
    }

    /** 规格 1: 慢脚本 + timeoutMs=200 → RenderException, message 精确含 "timed out after 200ms"。 */
    @Test
    @Timeout(15)
    public void timeoutAbortsSlowScriptWithExactMessage() {
        RenderException ex = Assertions.assertThrows(RenderException.class,
                () -> engine.render(HANGING_TEMPLATE, Map.of(), timeout(200L)));

        Assertions.assertTrue(String.valueOf(ex.getMessage()).contains("timed out after 200ms"),
                "unexpected message: " + ex.getMessage());
    }

    /** 规格 2: 快模板 + 宽裕 deadline (5000ms) → 结果正确, 超时路径不干扰正常返回。 */
    @Test
    @Timeout(15)
    public void fastRenderCompletesUnderGenerousDeadline() {
        Object result = engine.render("Hello, {@name}!", Map.of("name", "alice"), timeout(5000L));

        Assertions.assertEquals("Hello, alice!", result);
    }

    /**
     * 规格 3: 16 并发各渲染一个 300ms 超时的慢模板 → 16 个全部抛超时异常且 message 各自正确
     * (线程策略切换后, 单次渲染的超时判定不被并发兄弟渲染影响)。
     */
    @Test
    @Timeout(60)
    public void concurrentTimeoutsEachReportOwnDeadline() throws Exception {
        int workers = 16;
        ExecutorService pool = Executors.newFixedThreadPool(workers);
        try {
            CountDownLatch start = new CountDownLatch(1);
            Object[] outcomes = new Object[workers]; // 各自索引写入, future.get 后读取 (happens-before)
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < workers; i++) {
                final int idx = i;
                futures.add(pool.submit(() -> {
                    start.await();
                    try {
                        outcomes[idx] = engine.render(HANGING_TEMPLATE, Map.of(), timeout(300L));
                    } catch (Exception e) {
                        outcomes[idx] = e;
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get(55, TimeUnit.SECONDS);
            }

            for (int i = 0; i < workers; i++) {
                Assertions.assertTrue(outcomes[i] instanceof RenderException,
                        "worker " + i + " 应超时, 实际: " + outcomes[i]);
                Assertions.assertTrue(
                        String.valueOf(((Throwable) outcomes[i]).getMessage()).contains("timed out after 300ms"),
                        "worker " + i + " message 错误: " + outcomes[i]);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    /**
     * 规格 4: 8 慢 (300ms 超时) + 8 快 (5000ms 正常完成) 交错并发 →
     * 快的正常返回、慢的超时, 互不影响 (两种 deadline 共存时不串值)。
     */
    @Test
    @Timeout(60)
    public void concurrentMixedLoadIsolatesSlowFromFast() throws Exception {
        int total = 16;
        ExecutorService pool = Executors.newFixedThreadPool(total);
        try {
            CountDownLatch start = new CountDownLatch(1);
            Object[] outcomes = new Object[total];
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < total; i++) {
                final int idx = i;
                final boolean slow = idx % 2 == 0;
                final String name = "n" + idx;
                futures.add(pool.submit(() -> {
                    start.await();
                    try {
                        outcomes[idx] = slow
                                ? engine.render(HANGING_TEMPLATE, Map.of(), timeout(300L))
                                : engine.render("v:{@n}", Map.of("n", name), timeout(5000L));
                    } catch (Exception e) {
                        outcomes[idx] = e;
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get(55, TimeUnit.SECONDS);
            }

            for (int i = 0; i < total; i++) {
                if (i % 2 == 0) {
                    Assertions.assertTrue(outcomes[i] instanceof RenderException,
                            "slow#" + i + " 应超时, 实际: " + outcomes[i]);
                    Assertions.assertTrue(
                            String.valueOf(((Throwable) outcomes[i]).getMessage()).contains("timed out after 300ms"),
                            "slow#" + i + " message 错误: " + outcomes[i]);
                } else {
                    Assertions.assertEquals("v:n" + i, outcomes[i],
                            "fast#" + i + " 应正常返回, 实际: " + outcomes[i]);
                }
            }
        } finally {
            pool.shutdownNow();
        }
    }

    /** 规格 5: timeoutMs=0 不限时 → 慢模板 (sleep 400ms) 正常完成且拿到脚本返回值。 */
    @Test
    @Timeout(30)
    public void timeoutMsZeroMeansUnbounded() {
        String template = """
                {%
                  Thread.sleep(400)
                  return 'done'
                %}
                """;

        Object result = engine.render(template, Map.of(), timeout(0L));

        Assertions.assertEquals("done", result);
    }

    /**
     * 规格 6: 同一引擎连续 200 次 100ms 超时渲染 → 全部正确抛超时
     * (共享池线程 / 虚拟线程复用不出现串行化或泄漏式劣化;
     * 粗断言: 期望 ~20s, 上界 60s 即 3 倍余量, 超过视为劣化)。
     */
    @Test
    @Timeout(90)
    public void repeatedTimeoutsOnSameEngineDoNotDegrade() {
        int rounds = 200;
        long begin = System.nanoTime();

        for (int i = 0; i < rounds; i++) {
            RenderException ex = Assertions.assertThrows(RenderException.class,
                    () -> engine.render(HANGING_TEMPLATE, Map.of(), timeout(100L)),
                    "round " + i + " 应超时");
            Assertions.assertTrue(String.valueOf(ex.getMessage()).contains("timed out after 100ms"),
                    "round " + i + " message 错误: " + ex.getMessage());
        }

        long elapsedMs = (System.nanoTime() - begin) / 1_000_000L;
        Assertions.assertTrue(elapsedMs < 60_000L,
                "200 次 100ms 超时渲染总耗时 " + elapsedMs + "ms, 疑似线程策略劣化");
    }
}
