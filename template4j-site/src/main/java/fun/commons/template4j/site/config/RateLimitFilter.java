package fun.commons.template4j.site.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.InetAddress;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 写操作限流过滤器: 仅拦截 {@code /api/**} 下的 POST/PUT/PATCH/DELETE 请求。
 *
 * <p>按客户端维度独立计数, 客户端标识 (clientKey) 的取舍由 trust-proxy 语义决定 (S1 安全修复):
 * <ul>
 *   <li>{@code trust-proxy=false} (默认): 一律取 remoteAddr, 即使带 {@code X-Forwarded-For} 头也忽略 →
 *       攻击者轮换 XFF 无法伪造出无限个独立桶, 限流不被绕过。</li>
 *   <li>{@code trust-proxy=true} 且 remoteAddr 命中 {@code trusted-proxies} (精确 IP 或 IPv4 CIDR, 如
 *       {@code 10.0.0.0/8}): 取 XFF 首值 (trim) 作为 clientKey, XFF 空/缺失时回退 remoteAddr。</li>
 *   <li>{@code trust-proxy=true} 但 remoteAddr 不在信任列表 (列表为空 / remoteAddr 不可解析 / 网段或地址
 *       配置非法): 仍用 remoteAddr (XFF 被忽略) —— 任何不明确情形都按 "不可信" 处理 (fail-closed)。</li>
 * </ul>
 *
 * <p>内部使用 bucket4j 令牌桶 (容量 capacity, 每 refillMinutes 分钟整体恢复) 实现; 桶表由 Caffeine
 * {@code maximumSize(max-buckets)} 有界化 (LRU 淘汰), 防止 clientKey 喷射 (伪造 XFF / 海量 IPv6) 造成
 * 内存 DoS。令牌不足时直接返回 429 + {@code Retry-After} + JSON 错误体, 不再调用后续 Filter 与业务链。
 * {@code template4j.demo.rate-limit.enabled=false} 时整体放行。
 */
@Slf4j
@Component
@Order(0)
public class RateLimitFilter implements Filter {

    private static final String X_FORWARDED_FOR = "X-Forwarded-For";
    private static final String API_PREFIX = "/api/";
    private static final Set<String> WRITE_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");
    /** 兼容 3 参构造器的默认桶表上限。 */
    private static final int DEFAULT_MAX_BUCKETS = 100_000;

    private final boolean enabled;
    private final int capacity;
    private final int refillMinutes;
    private final boolean trustProxy;
    private final List<String> trustedProxies;
    private final Cache<String, Bucket> buckets;

    /** 兼容构造器: 等价于 trustProxy=false / trustedProxies 空 / maxBuckets=100000 (XFF 一律忽略)。 */
    public RateLimitFilter(boolean enabled, int capacity, int refillMinutes) {
        this(enabled, capacity, refillMinutes, false, List.of(), DEFAULT_MAX_BUCKETS);
    }

    /**
     * 全参构造器: Spring 注入与测试共用的入口 (类有多个构造器, Spring 路径必须显式标注 {@code @Autowired})。
     * maxBuckets 下限钳到 1, 避免 Caffeine 因非法值抛异常。
     */
    @Autowired
    public RateLimitFilter(
            @Value("${template4j.demo.rate-limit.enabled:true}") boolean enabled,
            @Value("${template4j.demo.rate-limit.capacity:10}") int capacity,
            @Value("${template4j.demo.rate-limit.refill-minutes:1}") int refillMinutes,
            @Value("${template4j.demo.rate-limit.trust-proxy:false}") boolean trustProxy,
            @Value("${template4j.demo.rate-limit.trusted-proxies:}") Collection<String> trustedProxies,
            @Value("${template4j.demo.rate-limit.max-buckets:100000}") int maxBuckets) {
        this.enabled = enabled;
        this.capacity = capacity;
        this.refillMinutes = refillMinutes;
        this.trustProxy = trustProxy;
        this.trustedProxies = trustedProxies == null ? List.of() : trustedProxies.stream()
                .map(String::trim).filter(s -> !s.isEmpty()).toList();
        this.buckets = Caffeine.newBuilder().maximumSize(Math.max(1, maxBuckets)).build();
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpReq = (HttpServletRequest) request;
        HttpServletResponse httpResp = (HttpServletResponse) response;

        if (!enabled || !isLimited(httpReq)) {
            chain.doFilter(request, response);
            return;
        }

        Bucket bucket = buckets.get(clientKey(httpReq), key -> newBucket());
        if (bucket.tryConsume(1)) {
            chain.doFilter(request, response);
            return;
        }

        reject(httpResp);
    }

    /** 仅 /api/** 下的写方法参与限流, 其余请求一律放行。 */
    private boolean isLimited(HttpServletRequest request) {
        String method = request.getMethod().toUpperCase(Locale.ROOT);
        return WRITE_METHODS.contains(method) && request.getServletPath().startsWith(API_PREFIX);
    }

    /**
     * clientKey 取舍 (S1): 只有 {@code trustProxy=true} 且 remoteAddr 命中信任列表时才取 XFF 首值 (trim),
     * XFF 空/缺失回退 remoteAddr; 其余情形一律 remoteAddr。
     */
    private String clientKey(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        if (trustProxy && isTrustedProxy(remoteAddr)) {
            String xff = request.getHeader(X_FORWARDED_FOR);
            if (xff != null && !xff.isBlank()) {
                String first = xff.split(",", 2)[0].trim();
                if (!first.isEmpty()) {
                    return first;
                }
            }
        }
        return remoteAddr;
    }

    /**
     * remoteAddr 是否命中可信代理列表 (精确 IP 或 IPv4 CIDR)。
     * 任何不可解析 / 地址族不一致 / 配置非法的情形都返回 false (fail-closed)。
     */
    private boolean isTrustedProxy(String remoteAddr) {
        if (remoteAddr == null || remoteAddr.isBlank() || trustedProxies.isEmpty()) {
            return false;
        }
        byte[] remoteBytes = parseLiteralAddress(remoteAddr);
        if (remoteBytes == null) {
            return false;
        }
        for (String entry : trustedProxies) {
            if (matchesTrustedEntry(remoteBytes, entry)) {
                return true;
            }
        }
        return false;
    }

    /** 单条信任配置匹配: 无 {@code /} 按精确 IP (字节相等); 有 {@code /} 按前缀位比较。 */
    private static boolean matchesTrustedEntry(byte[] remoteBytes, String entry) {
        if (entry == null || entry.isBlank()) {
            return false;
        }
        String trimmed = entry.trim();
        int slash = trimmed.indexOf('/');
        if (slash < 0) {
            byte[] entryBytes = parseLiteralAddress(trimmed);
            return entryBytes != null && Arrays.equals(entryBytes, remoteBytes);
        }
        byte[] networkBytes = parseLiteralAddress(trimmed.substring(0, slash));
        // 地址族不一致 (如 IPv6 remoteAddr 对 IPv4 网段) 视为不匹配
        if (networkBytes == null || networkBytes.length != remoteBytes.length) {
            return false;
        }
        int prefixLen;
        try {
            prefixLen = Integer.parseInt(trimmed.substring(slash + 1).trim());
        } catch (NumberFormatException e) {
            return false;
        }
        if (prefixLen < 0 || prefixLen > networkBytes.length * 8) {
            return false;
        }
        return prefixMatches(remoteBytes, networkBytes, prefixLen);
    }

    /** IPv4/IPv6 通用前缀位比较: 先整字节比较, 尾部剩余位按高位掩码比较。 */
    private static boolean prefixMatches(byte[] a, byte[] b, int prefixLen) {
        int fullBytes = prefixLen / 8;
        for (int i = 0; i < fullBytes; i++) {
            if (a[i] != b[i]) {
                return false;
            }
        }
        int remainBits = prefixLen % 8;
        if (remainBits == 0) {
            return true;
        }
        int mask = (0xFF << (8 - remainBits)) & 0xFF;
        return (a[fullBytes] & mask) == (b[fullBytes] & mask);
    }

    /**
     * 解析字面量 IP 地址 (IPv4 / IPv6 / v4-mapped / 带 zone 的 IPv6), 返回网络字节序原始字节;
     * 非字面量 (主机名等) 或非法格式返回 null。先做字符白名单粗筛 (十六进制/点/冒号/zone 分隔符),
     * 保证不会因误传主机名而触发 {@link InetAddress#getByName} 的 DNS 解析。
     */
    private static byte[] parseLiteralAddress(String ip) {
        int len = ip == null ? 0 : ip.length();
        if (len == 0 || len > 45) {
            return null;
        }
        for (int i = 0; i < len; i++) {
            char c = ip.charAt(i);
            boolean allowed = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F')
                    || c == '.' || c == ':' || c == '%';
            if (!allowed) {
                return null;
            }
        }
        try {
            return InetAddress.getByName(ip).getAddress();
        } catch (Exception e) {
            return null;
        }
    }

    private Bucket newBucket() {
        return Bucket.builder()
                .addLimit(Bandwidth.classic(capacity, Refill.intervally(capacity, Duration.ofMinutes(refillMinutes))))
                .build();
    }

    /** 包级测试钩子: 触发一次 Caffeine 清理 (把待淘汰条目真正移除) 后取桶数量的估计值。 */
    int bucketCount() {
        buckets.cleanUp();
        return (int) buckets.estimatedSize();
    }

    /** 令牌不足: 429 + Retry-After (按 refill 周期换算, 至少 1 秒) + JSON 错误体。 */
    private void reject(HttpServletResponse response) throws IOException {
        long retryAfter = Math.max(1, Duration.ofMinutes(refillMinutes).getSeconds());
        response.setStatus(429);
        response.setHeader("Retry-After", String.valueOf(retryAfter));
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"error\":\"rate_limited\",\"message\":\"too many requests, please retry later\"}");
        log.warn("request rate limited, retry after {} seconds", retryAfter);
    }
}
