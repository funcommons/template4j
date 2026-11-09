package fun.commons.template4j.site.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Micrometer metrics for the render path.
 *
 * <ul>
 *   <li>{@code template4j.render.total{type}} — counter bumped per successful render</li>
 *   <li>{@code template4j.render.duration} — timer recording each render's duration</li>
 *   <li>{@code template4j.script.cache.size} — gauge, registered at startup</li>
 * </ul>
 */
@Component
public class RenderMetrics {

    private static final String TOTAL = "template4j.render.total";
    private static final String DURATION = "template4j.render.duration";
    private static final String SCRIPT_CACHE_SIZE = "template4j.script.cache.size";

    private final MeterRegistry registry;
    private final Timer durationTimer;
    private final Map<String, Counter> totalByType = new ConcurrentHashMap<>();

    public RenderMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.durationTimer = registry.timer(DURATION);
        // ScriptEnvironment (and its scriptCacheSize()) is package-private inside template4j-core,
        // so site code cannot read the live cache size. Register a constant gauge for now so the
        // series exists from startup; TODO(T2): wire the real scriptCacheSize() value once core
        // exposes it (e.g. via an injected Supplier<Number> assembled where core types are visible).
        Gauge.builder(SCRIPT_CACHE_SIZE, () -> 0).register(registry);
    }

    /** Records one successful render of the given template type taking {@code durationMs}. */
    public void recordSuccess(String type, long durationMs) {
        String tag = type == null || type.isBlank() ? "json" : type;
        totalByType.computeIfAbsent(tag, t -> registry.counter(TOTAL, "type", t)).increment();
        durationTimer.record(durationMs, TimeUnit.MILLISECONDS);
    }
}
