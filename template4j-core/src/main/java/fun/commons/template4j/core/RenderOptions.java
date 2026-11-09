package fun.commons.template4j.core;

import java.util.function.Consumer;

/**
 * 请求级渲染选项 (不可变): 通过 {@code engine.render(template, data, options)} 传入,
 * 只作用于本次渲染, 不改变引擎全局状态 (渲染结束后 trace 订阅自动移除)。
 *
 * <pre>
 *   RenderOptions options = RenderOptions.builder()
 *           .traceConsumer(ev -> System.out.println(ev.format()))
 *           .timeoutMs(3000)
 *           .build();
 *   Object result = engine.render(template, data, options);
 * </pre>
 */
public final class RenderOptions {

    /** 本次渲染的 trace 事件消费者。null = 不订阅。 */
    private final Consumer<RenderEngine.TraceEvent> traceConsumer;

    /** 渲染超时毫秒数。0 = 不限时; 超时抛 {@link RenderException} (message 含 "timed out after Nms")。 */
    private final long timeoutMs;

    private RenderOptions(Builder builder) {
        this.traceConsumer = builder.traceConsumer;
        this.timeoutMs = builder.timeoutMs;
    }

    public Consumer<RenderEngine.TraceEvent> traceConsumer() {
        return traceConsumer;
    }

    public long timeoutMs() {
        return timeoutMs;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private Consumer<RenderEngine.TraceEvent> traceConsumer;
        private long timeoutMs;

        /** 订阅本次渲染的 trace 事件 (请求级, 渲染结束自动反注册)。 */
        public Builder traceConsumer(Consumer<RenderEngine.TraceEvent> traceConsumer) {
            this.traceConsumer = traceConsumer;
            return this;
        }

        /** 设置渲染超时毫秒数 (0 = 不限时, 默认)。 */
        public Builder timeoutMs(long timeoutMs) {
            this.timeoutMs = timeoutMs;
            return this;
        }

        public RenderOptions build() {
            return new RenderOptions(this);
        }
    }
}
