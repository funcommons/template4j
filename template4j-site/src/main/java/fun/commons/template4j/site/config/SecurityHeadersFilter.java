package fun.commons.template4j.site.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 安全响应头过滤器: 为站点所有响应补齐基础安全头, 降低点击劫持 / MIME 嗅探 / referrer 泄漏与 XSS 风险。
 *
 * <p>统一追加 (响应中已存在的同名头不覆盖, 保留上游设置的值):
 * {@code X-Content-Type-Options: nosniff}、{@code X-Frame-Options: DENY}、
 * {@code Referrer-Policy: no-referrer} 以及默认收紧的 {@code Content-Security-Policy};
 * 仅 {@code /api/**} 路径额外追加 {@code Cache-Control: no-store}, 防止接口响应被中间层缓存。
 *
 * <p>请求属性 {@link #SKIP} 值为 {@code "true"} 时跳过全部安全头 (供个别需要自定义头或非 HTML
 * 输出的端点显式退出)。在过滤器链中排于 {@link TraceIdFilter} 之后 ({@code @Order(2)})。
 */
@Slf4j
@Component
@Order(2)
public class SecurityHeadersFilter implements Filter {

    /** 请求属性名 (常量值即为属性名字面值): 属性值为 "true" 时本过滤器不写入任何安全头。 */
    public static final String SKIP = "true";

    private static final String API_PREFIX = "/api/";
    private static final String CSP = "default-src 'self'; script-src 'self' 'unsafe-inline'; "
            + "style-src 'self' 'unsafe-inline'; img-src 'self' data:; connect-src 'self'";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpReq = (HttpServletRequest) request;
        HttpServletResponse httpResp = (HttpServletResponse) response;

        if (!SKIP.equals(httpReq.getAttribute(SKIP))) {
            addIfAbsent(httpResp, "X-Content-Type-Options", "nosniff");
            addIfAbsent(httpResp, "X-Frame-Options", "DENY");
            addIfAbsent(httpResp, "Referrer-Policy", "no-referrer");
            addIfAbsent(httpResp, "Content-Security-Policy", CSP);
            if (httpReq.getServletPath().startsWith(API_PREFIX)) {
                addIfAbsent(httpResp, "Cache-Control", "no-store");
            }
        }

        chain.doFilter(request, response);
    }

    /** 仅当响应中尚无同名头时写入, 避免覆盖上游 (业务或其它过滤器) 已设置的安全头。 */
    private void addIfAbsent(HttpServletResponse response, String name, String value) {
        if (!response.containsHeader(name)) {
            response.setHeader(name, value);
        }
    }
}
