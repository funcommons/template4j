package fun.commons.template4j.core.components;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import fun.commons.template4j.utils.UJson;
import fun.commons.template4j.core.RenderComponent;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
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
 * <p>实现使用 JDK 11+ 内置的 {@link java.net.http.HttpClient}; circuitBreaker 基于 Resilience4j。
 */
public class RestRenderer implements RenderComponent {

    /** 共享 HttpClient 实例 — JDK 实现是 thread-safe 的,所有 RestRenderer 共用一个。 */
    static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    /**
     * 进程内有界 LRU 缓存 (Caffeine): value + 过期时间戳 (ms)。最多 {@link #MAX_CACHE_ENTRIES} 条,
     * 超出按访问顺序淘汰; 并发安全 (全局 lock-free, 替代原 synchronized LinkedHashMap)。
     */
    static final int MAX_CACHE_ENTRIES = Integer.getInteger("template4j.rest.cache.maxEntries", 1024);
    private static final Cache<String, CacheEntry> CACHE = Caffeine.newBuilder()
            .maximumSize(MAX_CACHE_ENTRIES)
            .build();

    /** Resilience4j 断路器注册表, keyed by "METHOD url". 静态, 跨 RestRenderer 实例共享。 */
    private static final CircuitBreakerRegistry BREAKERS = CircuitBreakerRegistry.ofDefaults();

    /** 可选 base url: 相对路径 (以 / 开头) 将拼接该前缀。系统属性 {@code template4j.rest.baseUrl} 为默认值。 */
    private static volatile String baseUrl = System.getProperty("template4j.rest.baseUrl", "");

    /** 编程式设置 base url (优先于系统属性), 供嵌入方 / 站点指向自身 mock 端点。传空串清除。 */
    public static void setBaseUrl(String url) {
        baseUrl = url == null ? "" : url.trim();
    }

    /** 相对路径拼接 base url; 绝对地址 (含 scheme) 原样返回。 */
    static String resolveUrl(String url) {
        if (url == null || url.isEmpty() || baseUrl.isEmpty()) return url;
        if (url.startsWith("/")) {
            String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
            return base + url;
        }
        return url;
    }

    /** 引擎级 rest options ({@code RenderEngine.withRestOptions} 注入)。null = 未设置, 各项回退静态默认。 */
    private volatile RestOptions engineOptions;

    /** 注入引擎级 rest 配置 (实例值优先, 未设置字段回退静态默认); null 清除 (全部回退静态默认)。 */
    public void setRestOptions(RestOptions options) {
        this.engineOptions = options;
    }

    /** 实例级 url 解析: 用引擎级 baseUrl ({@link #effectiveBaseUrl}, 回退静态) 拼相对路径; 逻辑同静态 {@link #resolveUrl}。 */
    private String resolveUrlFor(String url) {
        String base = effectiveBaseUrl(engineOptions);
        if (url == null || url.isEmpty() || base.isEmpty()) return url;
        if (url.startsWith("/")) {
            String trimmed = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
            return trimmed + url;
        }
        return url;
    }

    /** 重置所有 breaker (移除全部实例, 下次调用按模板配置重建; 主要供测试用)。 */
    public static void resetCircuitBreakers() {
        BREAKERS.getAllCircuitBreakers().forEach(cb -> BREAKERS.remove(cb.getName()));
    }

    // ===== SSRF 防护 (字面判定, 不做 DNS 解析) =====

    /** 响应体大小默认上限: 2MB (2097152)。 */
    static final long DEFAULT_MAX_RESPONSE_BYTES = 2L * 1024 * 1024;

    /** 响应体大小上限: 系统属性 {@code template4j.rest.maxResponseBytes}, 默认 2097152 (2MB); 非法值或 ≤0 回退默认。 */
    static long maxResponseBytes() {
        long v = Long.getLong("template4j.rest.maxResponseBytes", DEFAULT_MAX_RESPONSE_BYTES);
        return v > 0 ? v : DEFAULT_MAX_RESPONSE_BYTES;
    }

    // ===== 引擎级实例 options 合并 (纯函数; RenderEngine.withRestOptions → setRestOptions 注入 engineOptions) =====

    /** 实例级 baseUrl 非空即用 (trim 后), 否则回退静态 baseUrl; options == null 时全回退静态。 */
    static String effectiveBaseUrl(RestOptions options) {
        String own = options == null ? "" : options.baseUrl();
        String trimmed = own == null ? "" : own.trim();
        return trimmed.isEmpty() ? baseUrl : trimmed;
    }

    /** 静态白名单 (template4j.rest.allowed-hosts) ∪ options 白名单: 归一化同 {@link #isBlockedHost}, 去重、保序。 */
    static List<String> effectiveAllowedHosts(RestOptions options) {
        Set<String> hosts = new LinkedHashSet<>();
        for (String raw : System.getProperty("template4j.rest.allowed-hosts", "").split(",")) addHost(hosts, raw);
        if (options != null) {
            for (String raw : options.allowedHosts()) addHost(hosts, raw);
        }
        return new ArrayList<>(hosts);
    }

    /** trim + 小写 + 去尾部 {@code '.'} 后加入白名单; 空白项跳过 (空属性 / 空集合不产生空条目)。 */
    private static void addHost(Set<String> hosts, String raw) {
        String h = normalizeHost(raw);
        if (!h.isEmpty()) hosts.add(h);
    }

    /** 实例级响应体上限 (>0) 优先, 否则回退静态 {@link #maxResponseBytes()}; options == null 时回退静态。 */
    static long effectiveMaxResponseBytes(RestOptions options) {
        long own = options == null ? 0L : options.maxResponseBytes();
        return own > 0 ? own : maxResponseBytes();
    }

    /**
     * SSRF 防护: 字面判定 host 是否禁止访问 — 纯函数, 不做 DNS 解析; 大小写不敏感, 忽略尾部 {@code '.'}。
     * 拦截 loopback ({@code localhost} / {@code 127/8} / {@code 0.0.0.0} / {@code ::1} / {@code ::ffff:127.x}),
     * RFC1918 私网 ({@code 10/8} / {@code 172.16-31} / {@code 192.168/16}), link-local
     * ({@code 169.254/16} 云元数据 / {@code fe80::/10}) 与 IPv6 ULA {@code fd00::/8};
     * null / 空 / 空白 fail-closed。系统属性 {@code template4j.rest.allowed-hosts} (逗号分隔) 总是放行。
     */
    static boolean isBlockedHost(String host) {
        String h = normalizeHost(host);
        if (h.isEmpty()) return true; // fail-closed
        // 白名单总是放行 (每次调用读取, 支持运行期动态配置)
        for (String allowed : System.getProperty("template4j.rest.allowed-hosts", "").split(",")) {
            if (h.equals(normalizeHost(allowed))) return false;
        }
        if (h.equals("localhost") || h.equals("::1") || h.equals("::")) return true;
        // IPv4-mapped IPv6 (::ffff:127.0.0.1): 取 IPv4 部分判定
        if (h.startsWith("::ffff:")) return isPrivateIpv4(h.substring(7));
        if (h.indexOf(':') < 0) return isPrivateIpv4(h); // IPv4 字面量 / 域名
        // IPv6: 取首个 hextet 判定 ULA fd00::/8 与 link-local fe80::/10
        String first = h.split(":", 2)[0];
        if (first.startsWith("fd")) return true;
        try {
            int v = Integer.parseInt(first, 16);
            return v >= 0xfe80 && v <= 0xfebf;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /** host 归一化: trim + 小写 + 去尾部 {@code '.'}。 */
    private static String normalizeHost(String host) {
        String h = host == null ? "" : host.trim().toLowerCase();
        while (h.endsWith(".")) h = h.substring(0, h.length() - 1);
        return h;
    }

    /** 点分 IPv4 私网 / loopback / link-local 判定; 域名等非 IPv4 返回 false。 */
    private static boolean isPrivateIpv4(String h) {
        int[] o = ipv4Octets(h);
        if (o == null) return false;
        return o[0] == 0 || o[0] == 10 || o[0] == 127
                || (o[0] == 172 && o[1] >= 16 && o[1] <= 31)
                || (o[0] == 192 && o[1] == 168)
                || (o[0] == 169 && o[1] == 254);
    }

    /** 解析点分 IPv4 (4 段, 每段 0-255); 非法返回 null。 */
    private static int[] ipv4Octets(String h) {
        String[] parts = h.split("\\.", -1);
        if (parts.length != 4) return null;
        int[] out = new int[4];
        for (int i = 0; i < 4; i++) {
            String p = parts[i];
            if (!p.matches("\\d{1,3}")) return null;
            int v = Integer.parseInt(p);
            if (v > 255) return null;
            out[i] = v;
        }
        return out;
    }

    /**
     * 限制响应体大小的 {@link HttpResponse.BodyHandler}: 下载中累计字节数, 超过
     * 引擎级上限 ({@link #effectiveMaxResponseBytes}, 回退静态 {@link #maxResponseBytes()}) 即中断
     * 下载并通知下游失败 (IOException, 消息含 {@code "response too large"}),
     * 复用 {@code send()} 既有 IOException / retry / error 路径。
     */
    private HttpResponse.BodyHandler<String> limitingStringHandler() {
        long max = effectiveMaxResponseBytes(engineOptions);
        return responseInfo -> {
            HttpResponse.BodySubscriber<String> down =
                    HttpResponse.BodySubscribers.ofString(StandardCharsets.UTF_8);
            return new HttpResponse.BodySubscriber<>() {
                private Flow.Subscription upstream;
                private long received = 0;

                @Override public CompletionStage<String> getBody() { return down.getBody(); }

                @Override public void onSubscribe(Flow.Subscription subscription) {
                    this.upstream = subscription;
                    down.onSubscribe(subscription);
                }

                @Override public void onNext(List<ByteBuffer> buffers) {
                    for (ByteBuffer b : buffers) received += b.remaining();
                    if (received <= max) { down.onNext(buffers); return; }
                    // 超限: 中断下载并通知下游失败, 避免把整个响应读进内存
                    down.onError(new IOException("response too large: exceeds max "
                            + max + " bytes"));
                    upstream.cancel();
                }

                @Override public void onError(Throwable t) { down.onError(t); }
                @Override public void onComplete() { down.onComplete(); }
            };
        };
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
        String url = resolveUrlFor(context.renderField(urlKey));

        // ----- SSRF 防护: 内网 / loopback / link-local host 直接拒绝 (字面判定, 不做 DNS 解析) -----
        // 静态规则 (isBlockedHost, 含 template4j.rest.allowed-hosts 放行) 拦截的 host 可被
        // 引擎级白名单 (withRestOptions.allowedHosts) 救回; 空 / null host 两者都不含 → 仍 fail-closed。
        String host = URI.create(url).getHost();
        if (isBlockedHost(host) && !effectiveAllowedHosts(engineOptions).contains(normalizeHost(host))) {
            throw new HttpException("ssrf guard: blocked host '" + host + "' in url: " + url, null);
        }

        // ----- 增强解析 -----
        long timeoutMs = parseLongField(context, "timeout", 30000L);
        RetryPolicy retry = parseRetry(context);
        CachePolicy cache = parseCache(context);
        CircuitBreakerConfig breaker = parseCircuitBreaker(context);
        String breakerKey = methodUpper + " " + url;

        // ----- 缓存命中 -----
        if (cache != null) {
            CacheEntry hit = CACHE.getIfPresent(cache.key);
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
        CircuitBreaker cb = null;
        if (breaker != null) {
            cb = BREAKERS.circuitBreaker(breakerKey, breaker);
            try {
                cb.acquirePermission();
            } catch (CallNotPermittedException e) {
                // 熔断中: 直接走 error 路径
                String msg = "circuit breaker open";
                if (context.containsField("error")) {
                    return renderError(context, -1, msg);
                }
                throw new HttpException(msg, null);
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
                response = HTTP_CLIENT.send(reqBuilder.build(), limitingStringHandler());
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
                // 成功: 记录 success (HALF_OPEN 探测通过 → CLOSED, 失败计数窗口清零)
                if (cb != null) {
                    cb.onSuccess(System.nanoTime() - httpStartNs, TimeUnit.NANOSECONDS);
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
            if (cb != null) {
                if (status >= 500) {
                    // 5xx 计入 breaker failure
                    cb.onError(System.nanoTime() - httpStartNs, TimeUnit.NANOSECONDS,
                            new HttpException(status, responseBody));
                } else {
                    // 3xx/4xx: 不计失败也不重置计数; 归还 HALF_OPEN 探测槽
                    cb.releasePermission();
                }
            }
            if (context.containsField("error")) {
                return renderError(context, status, responseBody);
            }
            throw new HttpException(status, responseBody);
        }

        // 网络层失败 (无响应): 计入 breaker failure
        if (cb != null) {
            cb.onError(System.nanoTime() - httpStartNs, TimeUnit.NANOSECONDS,
                    lastError != null ? lastError : new HttpException("unknown error", null));
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
    private static CircuitBreakerConfig parseCircuitBreaker(RenderContext context) {
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
        // 连续 threshold 次失败 → OPEN; resetSec 后放行 1 次探测, 成功 → CLOSED
        return CircuitBreakerConfig.custom()
                .failureRateThreshold(100)
                .slidingWindowSize(threshold)
                .minimumNumberOfCalls(threshold)
                .waitDurationInOpenState(Duration.ofMillis(resetSec * 1000L))
                .permittedNumberOfCallsInHalfOpenState(1)
                .slowCallDurationThreshold(Duration.ofNanos(Long.MAX_VALUE))
                .build();
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
