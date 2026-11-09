package fun.commons.template4j.site.config;

import jakarta.servlet.Filter;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.Map;

/**
 * SecurityHeadersFilter TDD 契约测试。纯 Filter 直测 (Spring mock-web + jakarta servlet), 不启动 Spring 上下文。
 *
 * <p>实现契约 (业务代码需满足):
 * <ul>
 *   <li>{@code @Component @Order(2)} (在 TraceIdFilter 之后), 提供无参构造器</li>
 *   <li>对所有请求 (不限 /api) 加响应头: X-Content-Type-Options: nosniff / X-Frame-Options: DENY /
 *       Referrer-Policy: no-referrer / Content-Security-Policy: (与下方 CSP 常量逐字符一致)</li>
 *   <li>仅 /api/* 路径额外加 Cache-Control: no-store</li>
 *   <li>响应中已存在的同名头不覆盖 (保留上游已设的值)</li>
 *   <li>请求属性 {@link SecurityHeadersFilter#SKIP} 值为 "true" 时跳过全部安全头</li>
 * </ul>
 */
class SecurityHeadersFilterTest {

    private static final String CSP = "default-src 'self'; script-src 'self' 'unsafe-inline'; "
            + "style-src 'self' 'unsafe-inline'; img-src 'self' data:; connect-src 'self'";

    private MockHttpServletResponse send(Filter filter, String path) throws ServletException, IOException {
        return send(filter, path, Map.of(), false);
    }

    /** preset: 过滤器执行前预置的响应头 (模拟上游已设); skip: 是否设置 SKIP 请求属性。 */
    private MockHttpServletResponse send(Filter filter, String path, Map<String, String> preset, boolean skip)
            throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("GET");
        request.setContextPath("");
        request.setServletPath(path);
        request.setRequestURI(path);
        if (skip) {
            request.setAttribute(SecurityHeadersFilter.SKIP, "true");
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        preset.forEach(response::setHeader);
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    @Test
    void contentTypeOptionsIsNosniffOnApiPath() throws Exception {
        MockHttpServletResponse resp = send(new SecurityHeadersFilter(), "/api/render");
        Assertions.assertEquals("nosniff", resp.getHeader("X-Content-Type-Options"));
    }

    @Test
    void frameOptionsIsDeny() throws Exception {
        MockHttpServletResponse resp = send(new SecurityHeadersFilter(), "/api/render");
        Assertions.assertEquals("DENY", resp.getHeader("X-Frame-Options"));
    }

    @Test
    void referrerPolicyIsNoReferrer() throws Exception {
        MockHttpServletResponse resp = send(new SecurityHeadersFilter(), "/api/render");
        Assertions.assertEquals("no-referrer", resp.getHeader("Referrer-Policy"));
    }

    @Test
    void contentSecurityPolicyMatchesContractExactly() throws Exception {
        MockHttpServletResponse resp = send(new SecurityHeadersFilter(), "/docs/page");
        Assertions.assertEquals(CSP, resp.getHeader("Content-Security-Policy"), "CSP 必须与契约逐字符一致");
    }

    @Test
    void apiPathGetsCacheControlNoStore() throws Exception {
        MockHttpServletResponse resp = send(new SecurityHeadersFilter(), "/api/render");
        Assertions.assertEquals("no-store", resp.getHeader("Cache-Control"));
    }

    @Test
    void nonApiPathHasNoCacheControlButStillHasSecurityHeaders() throws Exception {
        MockHttpServletResponse resp = send(new SecurityHeadersFilter(), "/index.html");
        Assertions.assertNull(resp.getHeader("Cache-Control"), "非 /api 路径不应设置 Cache-Control");
        Assertions.assertEquals("nosniff", resp.getHeader("X-Content-Type-Options"), "非 /api 路径仍需基本安全头");
        Assertions.assertEquals("DENY", resp.getHeader("X-Frame-Options"));
        Assertions.assertEquals(CSP, resp.getHeader("Content-Security-Policy"));
    }

    @Test
    void existingHeaderIsPreserved() throws Exception {
        MockHttpServletResponse resp = send(new SecurityHeadersFilter(), "/api/render",
                Map.of("X-Frame-Options", "SAMEORIGIN", "Cache-Control", "public, max-age=3600"), false);
        Assertions.assertEquals("SAMEORIGIN", resp.getHeader("X-Frame-Options"), "上游已设的头应保留原值");
        Assertions.assertEquals("public, max-age=3600", resp.getHeader("Cache-Control"));
    }

    @Test
    void skipAttributeSuppressesAllHeaders() throws Exception {
        Assertions.assertEquals("true", SecurityHeadersFilter.SKIP, "SKIP 常量契约值必须为 \"true\"");
        MockHttpServletResponse resp = send(new SecurityHeadersFilter(), "/api/render", Map.of(), true);
        Assertions.assertNull(resp.getHeader("X-Content-Type-Options"));
        Assertions.assertNull(resp.getHeader("X-Frame-Options"));
        Assertions.assertNull(resp.getHeader("Referrer-Policy"));
        Assertions.assertNull(resp.getHeader("Content-Security-Policy"));
        Assertions.assertNull(resp.getHeader("Cache-Control"), "SKIP 时 /api 路径也不应有 Cache-Control");
    }

    @Test
    void apiSubPathsAlsoGetCacheControl() throws Exception {
        Assertions.assertEquals("no-store",
                send(new SecurityHeadersFilter(), "/api/render/preview").getHeader("Cache-Control"));
        Assertions.assertEquals("no-store",
                send(new SecurityHeadersFilter(), "/api/learn").getHeader("Cache-Control"));
    }
}
