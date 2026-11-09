package fun.commons.template4j.core;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * 流式渲染订阅器: 把 {@link RenderEngine#render(Object, Map)} 包成可订阅的事件流。
 * <p>
 * 用法:
 * <pre>
 *   RenderStream s = engine.renderStream(template, data);
 *   s.subscribe(
 *       ev -> System.out.println(ev.format()),
 *      err -> err.printStackTrace(),
 *      () -> System.out.println("done"));
 *   // 或拉模式 (不阻塞):
 *   List&lt;RenderEngine.TraceEvent&gt; events = s.pollAll(500);
 * </pre>
 */
public class RenderStream {

    /** 终态事件类型。 */
    public static final String RESULT_TYPE = "RESULT";
    public static final String ERROR_TYPE = "ERROR";

    private final RenderEngine engine;
    private final Object template;
    private final Map<String, Object> data;
    private final List<RenderEngine.TraceEvent> events = new CopyOnWriteArrayList<>();
    private final AtomicReference<Object> result = new AtomicReference<>();
    private final AtomicReference<Throwable> error = new AtomicReference<>();
    private final AtomicBoolean done = new AtomicBoolean(false);
    private final Object lock = new Object();
    private volatile Thread worker;

    private Consumer<RenderEngine.TraceEvent> onNext = ev -> {};
    private Consumer<Throwable> onErrorHandler = err -> {};
    private Runnable onCompleteHandler = () -> {};

    public RenderStream(RenderEngine engine, Object template, Map<String, Object> data) {
        this.engine = engine;
        this.template = template;
        this.data = data;
    }

    /** 启动渲染线程(若未启动)。可重复调用,只在第一次启动。 */
    public synchronized RenderStream start() {
        if (worker != null) return this;
        worker = new Thread(this::runRender, "template4j-stream");
        worker.setDaemon(true);
        worker.start();
        return this;
    }

    /** 拉模式: 等待最多 timeoutMs, 返回这段时间内产生的事件 + 终态。结果已在 result() / error() 中。 */
    public List<RenderEngine.TraceEvent> pollAll(long timeoutMs) {
        start();
        // 处理 overflow:Long.MAX_VALUE + currentTime 会变成负数
        long deadline = (timeoutMs < Long.MAX_VALUE)
                ? System.currentTimeMillis() + timeoutMs : Long.MAX_VALUE;
        synchronized (lock) {
            while (!done.get() && System.currentTimeMillis() < deadline) {
                long waitMs = Math.max(1, Math.min(1000, deadline - System.currentTimeMillis()));
                try { lock.wait(waitMs); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
            }
        }
        return java.util.Collections.unmodifiableList(new java.util.ArrayList<>(events));
    }

    /** 拉模式: 阻塞直到渲染完成, 返回全部事件。 */
    public List<RenderEngine.TraceEvent> awaitAll() {
        return pollAll(Long.MAX_VALUE);
    }

    public Object result() { return result.get(); }
    public Throwable error() { return error.get(); }
    public boolean isDone() { return done.get(); }

    public List<RenderEngine.TraceEvent> events() {
        return java.util.Collections.unmodifiableList(new java.util.ArrayList<>(events));
    }

    /** 推模式: 注册回调并立即启动。 */
    public synchronized RenderStream subscribe(Consumer<RenderEngine.TraceEvent> onNext,
                                              Consumer<Throwable> onError,
                                              Runnable onComplete) {
        if (onNext != null) this.onNext = onNext;
        if (onError != null) this.onErrorHandler = onError;
        if (onComplete != null) this.onCompleteHandler = onComplete;
        return start();
    }

    private void runRender() {
        // 临时订阅: 渲染结束必须反注册, 否则消费者(连同本 stream 的事件列表)
        // 会永久泄漏在共享引擎上, 后续任意请求的 trace 都会写入这里。
        RenderEngine.TraceHandle handle = engine.addTraceConsumer(ev -> {
            events.add(ev);
            onNext.accept(ev);
        });
        try {
            Object r = engine.render(template, data);
            result.set(r);
            RenderEngine.TraceEvent done = RenderEngine.TraceEvent.of(
                    RESULT_TYPE, "", "render complete", 0, r);
            events.add(done);
            onNext.accept(done);
        } catch (Throwable t) {
            error.set(t);
            RenderEngine.TraceEvent errEv = RenderEngine.TraceEvent.of(
                    ERROR_TYPE, "", t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName(), 0, null);
            events.add(errEv);
            onNext.accept(errEv);
            onErrorHandler.accept(t);
        } finally {
            handle.remove();
            done.set(true);
            synchronized (lock) { lock.notifyAll(); }
            onCompleteHandler.run();
        }
    }
}