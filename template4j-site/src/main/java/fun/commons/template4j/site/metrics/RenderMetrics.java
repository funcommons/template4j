package fun.commons.template4j.site.metrics;

import fun.commons.template4j.core.RenderEngine;
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
 *   <li>{@code template4j.script.cache.size} — gauge reading the engine's live script-cache size
 *       via {@link RenderEngine.ScriptCacheStats#scriptCacheSize()} (reports -1 when unsupported)</li>
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

    public RenderMetrics(MeterRegistry registry, RenderEngine.ScriptCacheStats scriptCacheStats) {
        this.registry = registry;
        this.durationTimer = registry.timer(DURATION);
        // Truth source (round2 §A3): the gauge reads the engine's scriptCacheSize() on every
        // query, so the series moves as scripts are compiled / evicted / cleared. The builder
        // keeps a strong reference to scriptCacheStats — fine here, the engine is a singleton
        // that lives for the life of the app. Unsupported engines report -1 (interface default).
        Gauge.builder(SCRIPT_CACHE_SIZE, scriptCacheStats, RenderEngine.ScriptCacheStats::scriptCacheSize)
                .register(registry);
    }

    /** Records one successful render of the given template type taking {@code durationMs}. */
    public void recordSuccess(String type, long durationMs) {
        String tag = type == null || type.isBlank() ? "json" : type;
        totalByType.computeIfAbsent(tag, t -> registry.counter(TOTAL, "type", t)).increment();
        durationTimer.record(durationMs, TimeUnit.MILLISECONDS);
    }
}
