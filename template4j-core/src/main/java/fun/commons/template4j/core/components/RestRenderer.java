package fun.commons.template4j.core.components;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import fun.commons.template4j.utils.UJson;
import fun.commons.template4j.core.RenderComponent;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * HTTP 调用组件:支持 GET / POST / PUT / DELETE,以及 success / error 模板分支。
 *
 * <p>增强子属性 (生产编排):
 * <ul>
 *   <li>{@code timeout} - 单次请求超时 (ms)</li>
 *   <li>{@code retry}   - 重试策略 {count, backoff, baseMs, retryOn}</li>
 *   <li>{@code cache}   - 响应缓存 {key, ttl}</li>
 *   <li>{@code auth}    - 鉴权注入 {type, token | user, pass}</li>
 * </ul>
 *
 * <p>实现使用 JDK 11+ 内置的 {@link java.net.http.HttpClient},无第三方依赖。
 */
public class RestRenderer implements RenderComponent {

    /** 共享 HttpClient 实例 — JDK 实现是 thread-safe 的,所有 RestRenderer 共用一个。 */
    static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    /**
     * 进程内有界 LRU 缓存: value + 过期时间戳 (ms)。最多 {@link #MAX_CACHE_ENTRIES} 条,
     * 超出按访问顺序淘汰。所有操作走 {@link #CACHE_GATE} 同步,保证一致性。
     * 缓存对象小且访问 I/O-bound (网络请求), 同步开销可忽略。
     */
    static final int MAX_CACHE_ENTRIES = Integer.getInteger("template4j.rest.cache.maxEntries", 1024);
    private static final Map<String, CacheEntry> CACHE = Collections.synchronizedMap(
            new LinkedHashMap<String, CacheEntry>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, CacheEntry> eldest) {
                    return size() > MAX_CACHE_ENTRIES;
                }
            });

    /** 进程内 circuit breaker state, keyed by url+method. 静态, 跨 RestRenderer 实例共享。 */
    private static final ConcurrentHashMap<String, BreakerState> BREAKERS = new ConcurrentHashMap<>();

    /** 重置所有 breaker state (主要供测试用)。 */
    public static void resetCircuitBreakers() {
        BREAKERS.clear();
    }

    /** Breaker 状态机: CLOSED → OPEN (连续 failure 达 threshold) → HALF_OPEN (resetSec 后) → CLOSED/OPEN。 */
    private static final class BreakerState {
        static final int CLOSED = 0;
        static final int OPEN = 1;
        static final int HALF_OPEN = 2;
        volatile int state = CLOSED;
        volatile int failureCount = 0;
        volatile long openedAt = 0;
    }

    /** Breaker 配置。 */
    private static final class BreakerPolicy {
        final int threshold;
        final long resetMs;
        BreakerPolicy(int threshold, long resetMs) {
            this.threshold = threshold;
            this.resetMs = resetMs;
        }
    }

    public static class HttpException extends RuntimeException {
        private final int status;
        private final String body;

        public HttpException(int status, String body) {
            super("HTTP " + status + ": " + body);
            this.status = status;
            this.body = body;
        }

        public HttpException(String message, Throwable cause) {
            super(message, cause);
            this.status = -1;
            this.body = message;
        }

        public int getStatus() { return status; }
        public String getResponseBody() { return body; }
    }

    private static final class CacheEntry {
        final Object value;
        final long expiresAt;
        CacheEntry(Object value, long ttlMs) {
            this.value = value;
            this.expiresAt = System.currentTimeMillis() + ttlMs;
        }
        boolean expired() {
            return System.currentTimeMillis() >= expiresAt;
        }
    }

    @Override
    public String name() {
        return "rest";
    }

    @Override
    public Object render(RenderContext context) throws Exception {
        context.assertFieldOne("url", "select");

        String method = context.containsField("method") ? context.renderField("method") : "GET";
        String methodUpper = method.toUpperCase();

        String urlKey = context.firstNonField("url", "select");
        String url = context.renderField(urlKey);

        // ----- 增强解析 -----
        long timeoutMs = parseLongField(context, "timeout", 30000L);
        RetryPolicy retry = parseRetry(context);
        CachePolicy cache = parseCache(context);
        BreakerPolicy breaker = parseCircuitBreaker(context);
        String breakerKey = methodUpper + " " + url;

        // ----- 缓存命中 -----
        if (cache != null) {
            CacheEntry hit = CACHE.get(cache.key);
            if (hit != null && !hit.expired()) {
                // 防御性 deep copy: 缓存对象可能被 success 模板或后续代码 mutate,
                // 直接返回引用会导致并发请求间互相污染。String/Number/null 不需要 copy。
                Object safe = defensiveCopy(hit.value);
                return context.containsField("success")
                        ? context.renderField("success", "responseData", safe)
                        : safe;
            }
        }

        // ----- headers (含 auth 注入) -----
        Map<String, List<String>> headers = new LinkedHashMap<>();
        if (context.containsField("headers")) {
            Object headersObj = context.renderField("headers");
            if (headersObj instanceof Map) {
                ((Map<?, ?>) headersObj).forEach((k, v) -> {
                    if (k != null && v != null) {
                        headers.computeIfAbsent(k.toString(), kk -> new ArrayList<>()).add(v.toString());
                    }
                });
            }
        }
        applyAuth(context, headers);

        // ----- 构造 request builder -----
        HttpRequest.Builder reqBuilder = HttpRequest.newBuilder().uri(URI.create(url))
                .timeout(Duration.ofMillis(timeoutMs));
        headers.forEach((name, values) -> values.forEach(v -> reqBuilder.header(name, v)));

        String contentType = headers.entrySet().stream()
                .filter(e -> e.getKey().equalsIgnoreCase("Content-Type"))
                .map(Map.Entry::getValue)
                .map(values -> values.isEmpty() ? null : values.get(0))
                .findFirst()
                .orElse(null);

        if ("GET".equals(methodUpper)) {
            if (context.containsField("data")) {
                Object dataObj = context.renderField("data");
                String qs = buildQueryString(dataObj);
                if (!qs.isEmpty()) {
                    String sep = url.contains("?") ? "&" : "?";
                    url = url + sep + qs;
                    reqBuilder.uri(URI.create(url));
                }
            }
            reqBuilder.GET();
        } else {
            String bodyStr = null;
            if (context.containsField("data")) {
                Object dataObj = context.renderField("data");
                if (dataObj instanceof String s) {
                    bodyStr = s;
                } else if (dataObj != null) {
                    bodyStr = JSON.toJSONString(dataObj);
                }
            }
            String ct = contentType != null ? contentType : "application/json";
            if (ct.contains("application/x-www-form-urlencoded") || ct.contains("multipart/form-data")) {
                if (context.containsField("data")) {
                    Object dataObj = context.renderField("data");
                    bodyStr = buildQueryString(dataObj);
                }
            }
            if (bodyStr == null) bodyStr = "";
            HttpRequest.BodyPublisher publisher = HttpRequest.BodyPublishers.ofString(bodyStr, StandardCharsets.UTF_8);
            switch (methodUpper) {
                case "POST"   -> reqBuilder.POST(publisher);
                case "PUT"    -> reqBuilder.PUT(publisher);
                case "DELETE" -> reqBuilder.method("DELETE", publisher);
                default       -> reqBuilder.method(methodUpper, publisher);
            }
            if (contentType == null) {
                reqBuilder.header("Content-Type", ct);
            }
        }

        // ----- retry loop -----
        // circuit breaker: OPEN 且未到 reset 时间 → 直接走 error 分支不调 HTTP
        if (breaker != null) {
            BreakerState st = BREAKERS.computeIfAbsent(breakerKey, k -> new BreakerState());
            synchronized (st) {
                if (st.state == BreakerState.OPEN) {
                    if (System.currentTimeMillis() - st.openedAt >= breaker.resetMs) {
                        st.state = BreakerState.HALF_OPEN;
                    } else {
                        // 熔断中: 直接走 error 路径
                        String msg = "circuit breaker open";
                        if (context.containsField("error")) {
                            return renderError(context, -1, msg);
                        }
                        throw new HttpException(msg, null);
                    }
                }
            }
        }

        HttpResponse<String> response = null;
        Throwable lastError = null;
        int attempts = retry != null ? retry.count + 1 : 1;
        long httpStartNs = System.nanoTime();
        try {
        for (int attempt = 1; attempt <= attempts; attempt++) {
            if (attempt > 1) {
                try {
                    Thread.sleep(retry.backoffMs(attempt));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            try {
                response = HTTP_CLIENT.send(reqBuilder.build(),
                        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                int status = response.statusCode();
                if (status < 500 && status >= 200) {
                    // 成功或非 5xx 错误, 都不重试 (5xx 才视为可重试)
                    break;
                }
                if (retry != null && retry.retryOn5xx && status >= 500 && attempt < attempts) {
                    lastError = new HttpException(status, response.body());
                    continue;
                }
                break;
            } catch (java.io.IOException e) {
                // 网络层失败 (超时 / 连接拒绝 / DNS / socket reset 等都属 IOException)
                lastError = e;
                if (retry != null && retry.retryOnNetwork && attempt < attempts) {
                    continue;
                }
                break;
            } catch (Exception e) {
                if (e instanceof InterruptedException) throw e;
                lastError = e;
                if (retry != null && retry.retryOnNetwork && attempt < attempts) {
                    continue;
                }
                break;
            }
        }
        } finally {
            long durMs = (System.nanoTime() - httpStartNs) / 1_000_000;
            int traceStatus = response != null ? response.statusCode() : -1;
            context.renderer.trace(fun.commons.template4j.core.RenderEngine.TraceEvent.of(
                    "REST", context.self.path(),
                    methodUpper + " " + url + "  " + traceStatus,
                    durMs, null));
        }

        // ----- 处理结果 -----
        if (response != null) {
            int status = response.statusCode();
            String responseBody = response.body();
            if (status >= 200 && status < 300) {
                // 成功: 重置 breaker
                if (breaker != null) {
                    BreakerState st = BREAKERS.get(breakerKey);
                    if (st != null) {
                        synchronized (st) {
                            st.state = BreakerState.CLOSED;
                            st.failureCount = 0;
                        }
                    }
                }
                String dataType = context.getValue("dataType", "auto");
                Object result = UJson.smartCast(responseBody, dataType);
                if (cache != null) {
                    CACHE.put(cache.key, new CacheEntry(result, cache.ttl));
                }                if (context.containsField("success")) {
                    result = context.renderField("success", "responseData", result);
                }
                return result;
            }
            // 4xx / 5xx / 3xx
            // 5xx 计入 breaker failure
            if (breaker != null && status >= 500) {
                recordBreakerFailure(breakerKey, breaker);
            }
            if (context.containsField("error")) {
                return renderError(context, status, responseBody);
            }
            throw new HttpException(status, responseBody);
        }

        // 网络层失败 (无响应): 计入 breaker failure
        if (breaker != null) {
            recordBreakerFailure(breakerKey, breaker);
        }
        String msg = lastError != null && lastError.getMessage() != null
                ? lastError.getMessage()
                : (lastError != null ? lastError.getClass().getSimpleName() : "unknown error");
        if (context.containsField("error")) {
            return renderError(context, -1, msg);
        }
        if (lastError instanceof HttpException he) throw he;
        throw new HttpException(msg, lastError);
    }

    /** 记录一次失败, 若达 threshold 则 OPEN。 */
    private static void recordBreakerFailure(String key, BreakerPolicy policy) {
        BreakerState st = BREAKERS.get(key);
        if (st == null) return;
        synchronized (st) {
            st.failureCount++;
            if (st.failureCount >= policy.threshold) {
                st.state = BreakerState.OPEN;
                st.openedAt = System.currentTimeMillis();
            }
        }
    }

    /** 防御性 deep copy, 用于缓存命中后返回。immutable 类型直接返回原对象。 */
    private static Object defensiveCopy(Object value) {
        if (value == null) return null;
        if (value instanceof CharSequence) return value;
        if (value instanceof Number) return value;
        if (value instanceof Boolean) return value;
        // JSONObject / JSONArray / Map / List: 通过 JSON 序列化-反序列化 deep copy
        try {
            return JSON.parse(JSON.toJSONString(value));
        } catch (Exception e) {
            // 序列化失败: 返回原对象, 由调用方自行注意
            return value;
        }
    }

    private Object renderError(RenderContext context, int status, String message) throws Exception {
        JSONObject responseData = new JSONObject();
        responseData.put("status", status);
        responseData.put("message", message);
        return context.renderField("error", "responseData", responseData);
    }

    private static String buildQueryString(Object dataObj) {
        if (dataObj == null) return "";
        if (dataObj instanceof Map) {
            StringBuilder sb = new StringBuilder();
            boolean first = true;
            for (Map.Entry<?, ?> e : ((Map<?, ?>) dataObj).entrySet()) {
                if (!first) sb.append('&');
                first = false;
                sb.append(URLEncoder.encode(e.getKey().toString(), StandardCharsets.UTF_8));
                sb.append('=');
                sb.append(URLEncoder.encode(e.getValue() == null ? "" : e.getValue().toString(), StandardCharsets.UTF_8));
            }
            return sb.toString();
        }
        return URLEncoder.encode(dataObj.toString(), StandardCharsets.UTF_8);
    }

    // ===== 增强解析 helpers =====

    private static long parseLongField(RenderContext context, String field, long fallback) {
        if (!context.containsField(field)) return fallback;
        Object v;
        try {
            v = context.renderField(field);
        } catch (Exception e) {
            return fallback;
        }
        if (v instanceof Number) return ((Number) v).longValue();
        try { return Long.parseLong(String.valueOf(v).trim()); }
        catch (NumberFormatException e) { return fallback; }
    }

    private static RetryPolicy parseRetry(RenderContext context) {
        if (!context.containsField("retry")) return null;
        Object v;
        try {
            v = context.renderField("retry");
        } catch (Exception e) {
            return null;
        }
        if (!(v instanceof Map)) return null;
        @SuppressWarnings("unchecked")
        Map<String, Object> r = (Map<String, Object>) v;
        int count = toInt(r.get("count"), 0);
        if (count <= 0) return null;
        String backoff = String.valueOf(r.getOrDefault("backoff", "fixed"));
        long baseMs = toLong(r.get("baseMs"), 100L);
        String retryOn = String.valueOf(r.getOrDefault("retryOn", "5xx,network"));
        return new RetryPolicy(count, backoff, baseMs, retryOn);
    }

    private static CachePolicy parseCache(RenderContext context) {
        if (!context.containsField("cache")) return null;
        Object v;
        try {
            v = context.renderField("cache");
        } catch (Exception e) {
            return null;
        }
        if (!(v instanceof Map)) return null;
        @SuppressWarnings("unchecked")
        Map<String, Object> c = (Map<String, Object>) v;
        Object keyRaw = c.get("key");
        if (keyRaw == null) return null;
        String key = keyRaw.toString();
        long ttl = toLong(c.get("ttl"), 0L);
        if (ttl <= 0) return null;
        return new CachePolicy(key, ttl);
    }

    /** circuitBreaker: { threshold, resetSec }. threshold<=0 或 resetSec<=0 视为禁用。 */
    private static BreakerPolicy parseCircuitBreaker(RenderContext context) {
        if (!context.containsField("circuitBreaker")) return null;
        Object v;
        try {
            v = context.renderField("circuitBreaker");
        } catch (Exception e) {
            return null;
        }
        if (!(v instanceof Map)) return null;
        @SuppressWarnings("unchecked")
        Map<String, Object> b = (Map<String, Object>) v;
        int threshold = toInt(b.get("threshold"), 0);
        long resetSec = toLong(b.get("resetSec"), 0L);
        if (threshold <= 0 || resetSec <= 0) return null;
        return new BreakerPolicy(threshold, resetSec * 1000L);
    }

    private static void applyAuth(RenderContext context, Map<String, List<String>> headers) {
        if (!context.containsField("auth")) return;
        Object v;
        try {
            v = context.renderField("auth");
        } catch (Exception e) {
            return;
        }
        if (!(v instanceof Map)) return;
        @SuppressWarnings("unchecked")
        Map<String, Object> a = (Map<String, Object>) v;
        String type = String.valueOf(a.getOrDefault("type", "")).toLowerCase();
        switch (type) {
            case "bearer" -> {
                Object token = a.get("token");
                if (token != null) {
                    headers.computeIfAbsent("Authorization", k -> new ArrayList<>())
                            .add("Bearer " + token);
                }
            }
            case "basic" -> {
                Object user = a.get("user");
                Object pass = a.get("pass");
                if (user != null && pass != null) {
                    String raw = user + ":" + pass;
                    String encoded = Base64.getEncoder().encodeToString(
                            raw.getBytes(StandardCharsets.UTF_8));
                    headers.computeIfAbsent("Authorization", k -> new ArrayList<>())
                            .add("Basic " + encoded);
                }
            }
            default -> { /* unknown auth type — ignored */ }
        }
    }

    private static int toInt(Object v, int fallback) {
        if (v instanceof Number) return ((Number) v).intValue();
        try { return Integer.parseInt(String.valueOf(v).trim()); }
        catch (Exception e) { return fallback; }
    }

    private static long toLong(Object v, long fallback) {
        if (v instanceof Number) return ((Number) v).longValue();
        try { return Long.parseLong(String.valueOf(v).trim()); }
        catch (Exception e) { return fallback; }
    }

    private static final class RetryPolicy {
        final int count;
        final String backoff;
        final long baseMs;
        final boolean retryOn5xx;
        final boolean retryOnNetwork;

        RetryPolicy(int count, String backoff, long baseMs, String retryOn) {
            this.count = count;
            this.backoff = backoff;
            this.baseMs = baseMs;
            this.retryOn5xx = retryOn.contains("5xx") || retryOn.contains("all");
            this.retryOnNetwork = retryOn.contains("network") || retryOn.contains("all");
        }

        long backoffMs(int attempt) {
            // attempt 1 is the initial call, attempt 2 is the first retry
            int retryIndex = attempt - 1;
            return switch (backoff) {
                case "exponential" -> baseMs * (1L << Math.min(retryIndex - 1, 10));
                case "linear"      -> baseMs * retryIndex;
                default            -> baseMs;
            };
        }
    }

    private static final class CachePolicy {
        final String key;
        final long ttl;
        CachePolicy(String key, long ttl) {
            this.key = key;
            this.ttl = ttl;
        }
    }
}
