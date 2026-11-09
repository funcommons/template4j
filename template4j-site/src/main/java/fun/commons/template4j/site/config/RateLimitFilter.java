package fun.commons.template4j.site.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 写操作限流过滤器: 仅拦截 {@code /api/**} 下的 POST/PUT/PATCH/DELETE 请求。
 *
 * <p>按客户端维度独立计数, 客户端标识优先取 {@code X-Forwarded-For} 首值 (trim), 否则取 remoteAddr;
 * 内部使用 bucket4j 令牌桶 (容量 capacity, 每 refillMinutes 分钟整体恢复) 实现。
 * 令牌不足时直接返回 429 + {@code Retry-After} + JSON 错误体, 不再调用后续 Filter 与业务链。
 * {@code template4j.demo.rate-limit.enabled=false} 时整体放行。
 */
@Slf4j
@Component
@Order(0)
public class RateLimitFilter implements Filter {

    private static final String X_FORWARDED_FOR = "X-Forwarded-For";
    private static final String API_PREFIX = "/api/";
    private static final Set<String> WRITE_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    private final boolean enabled;
    private final int capacity;
    private final int refillMinutes;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public RateLimitFilter(
            @Value("${template4j.demo.rate-limit.enabled:true}") boolean enabled,
            @Value("${template4j.demo.rate-limit.capacity:10}") int capacity,
            @Value("${template4j.demo.rate-limit.refill-minutes:1}") int refillMinutes) {
        this.enabled = enabled;
        this.capacity = capacity;
        this.refillMinutes = refillMinutes;
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

        Bucket bucket = buckets.computeIfAbsent(clientKey(httpReq), key -> newBucket());
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

    /** X-Forwarded-For 首值 (trim) 优先, 为空时回退到 remoteAddr。 */
    private String clientKey(HttpServletRequest request) {
        String xff = request.getHeader(X_FORWARDED_FOR);
        if (xff != null && !xff.isBlank()) {
            String first = xff.split(",", 2)[0].trim();
            if (!first.isEmpty()) {
                return first;
            }
        }
        return request.getRemoteAddr();
    }

    private Bucket newBucket() {
        return Bucket.builder()
                .addLimit(Bandwidth.classic(capacity, Refill.intervally(capacity, Duration.ofMinutes(refillMinutes))))
                .build();
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
