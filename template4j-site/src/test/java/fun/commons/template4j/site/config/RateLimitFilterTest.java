package fun.commons.template4j.site.config;

import jakarta.servlet.Filter;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

/**
 * RateLimitFilter TDD 契约测试。纯 Filter 直测 (Spring mock-web + jakarta servlet), 不启动 Spring 上下文,
 * 不 import bucket4j 类型。
 *
 * <p>实现契约 (业务代码需满足):
 * <ul>
 *   <li>公开构造器: {@code RateLimitFilter(boolean enabled, int capacity, int refillMinutes)};
 *       生产路径经 @Value 绑定 template4j.demo.rate-limit.{enabled,capacity,refill-minutes}, 默认 true/10/1</li>
 *   <li>只对 POST/PUT/PATCH/DELETE 且 /api/* 路径限流, 其余放行</li>
 *   <li>按 X-Forwarded-For 首值 (无则 remoteAddr) 独立计数</li>
 *   <li>超限: 429 + Retry-After (秒 >= 1) + JSON 响应体, 且 chain 不再执行</li>
 * </ul>
 */
class RateLimitFilterTest {

    private static final String XFF = "X-Forwarded-For";

    /** 一次请求的结果: chain 是否被调用 + 响应状态。 */
    private record Attempt(MockHttpServletResponse response, MockFilterChain chain) {
        boolean passedChain() {
            return chain.getRequest() != null;
        }

        int status() {
            return response.getStatus();
        }
    }

    private Attempt send(Filter filter, String method, String path, String remoteAddr, String xff)
            throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod(method);
        request.setContextPath("");
        request.setServletPath(path);
        request.setRequestURI(path);
        request.setRemoteAddr(remoteAddr);
        if (xff != null) {
            request.addHeader(XFF, xff);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        return new Attempt(response, chain);
    }

    @Test
    void getIsAlwaysAllowed() throws Exception {
        Filter filter = new RateLimitFilter(true, 1, 1);
        for (int i = 0; i < 5; i++) {
            Attempt a = send(filter, "GET", "/api/render", "10.0.0.1", null);
            Assertions.assertTrue(a.passedChain(), "GET 第 " + i + " 次应放行");
            Assertions.assertEquals(200, a.status());
        }
    }

    @Test
    void nonApiPathIsNotLimited() throws Exception {
        Filter filter = new RateLimitFilter(true, 1, 1);
        for (int i = 0; i < 5; i++) {
            Attempt a = send(filter, "POST", "/web/render", "10.0.0.2", null);
            Assertions.assertTrue(a.passedChain(), "非 /api 路径第 " + i + " 次 POST 应放行");
        }
    }

    @Test
    void writeUnderCapacityPasses() throws Exception {
        Filter filter = new RateLimitFilter(true, 3, 1);
        for (int i = 0; i < 3; i++) {
            Attempt a = send(filter, "POST", "/api/render", "10.0.0.3", null);
            Assertions.assertTrue(a.passedChain(), "容量内第 " + i + " 次 POST 应放行");
            Assertions.assertEquals(200, a.status());
        }
    }

    @Test
    void overLimitReturns429WithRetryAfter() throws Exception {
        Filter filter = new RateLimitFilter(true, 2, 1);
        Assertions.assertTrue(send(filter, "POST", "/api/render", "10.0.0.4", null).passedChain());
        Assertions.assertTrue(send(filter, "POST", "/api/render", "10.0.0.4", null).passedChain());
        Attempt blocked = send(filter, "POST", "/api/render", "10.0.0.4", null);
        Assertions.assertFalse(blocked.passedChain(), "第 3 次 POST 应被拦截");
        Assertions.assertEquals(429, blocked.status());
        String retryAfter = blocked.response().getHeader("Retry-After");
        Assertions.assertNotNull(retryAfter, "超限响应必须有 Retry-After 头");
        Assertions.assertTrue(Integer.parseInt(retryAfter) >= 1, "Retry-After 应 >= 1 秒, 实际: " + retryAfter);
        Assertions.assertEquals(429, send(filter, "POST", "/api/render", "10.0.0.4", null).status(),
                "令牌未恢复前应持续 429");
    }

    @Test
    void clientsAreLimitedIndependentlyByIp() throws Exception {
        Filter filter = new RateLimitFilter(true, 1, 1);
        Assertions.assertTrue(send(filter, "POST", "/api/render", "10.0.1.1", null).passedChain());
        Assertions.assertEquals(429, send(filter, "POST", "/api/render", "10.0.1.1", null).status());
        Assertions.assertTrue(send(filter, "POST", "/api/render", "10.0.1.2", null).passedChain(),
                "IP B 不应受 IP A 超限影响");
    }

    @Test
    void disabledFilterLetsEverythingThrough() throws Exception {
        Filter filter = new RateLimitFilter(false, 1, 1);
        for (int i = 0; i < 5; i++) {
            Attempt a = send(filter, "POST", "/api/render", "10.0.2.1", null);
            Assertions.assertTrue(a.passedChain(), "enabled=false 时第 " + i + " 次应放行");
        }
    }

    @Test
    void xForwardedForFirstValueTakesPrecedence() throws Exception {
        Filter filter = new RateLimitFilter(true, 1, 1);
        Assertions.assertTrue(
                send(filter, "POST", "/api/render", "192.168.0.1", "203.0.113.7, 10.0.0.1").passedChain());
        // remoteAddr 换了, 但 XFF 首值相同 -> 仍按 203.0.113.7 计数, 应被拦截
        Attempt blocked = send(filter, "POST", "/api/render", "10.9.9.9", "203.0.113.7");
        Assertions.assertEquals(429, blocked.status(), "XFF 首值应优先于 remoteAddr");
        Assertions.assertFalse(blocked.passedChain());
        // XFF 不同 -> 独立计数
        Assertions.assertTrue(send(filter, "POST", "/api/render", "203.0.113.7", "8.8.8.8").passedChain());
    }

    @Test
    void allWriteMethodsAreCountedAndGetExempt() throws Exception {
        Filter filter = new RateLimitFilter(true, 3, 1);
        Assertions.assertTrue(send(filter, "POST", "/api/render", "10.0.3.1", null).passedChain());
        Assertions.assertTrue(send(filter, "PUT", "/api/render", "10.0.3.1", null).passedChain());
        Assertions.assertTrue(send(filter, "GET", "/api/render", "10.0.3.1", null).passedChain(),
                "GET 不应消耗令牌");
        Assertions.assertTrue(send(filter, "PATCH", "/api/render", "10.0.3.1", null).passedChain());
        Assertions.assertEquals(429, send(filter, "DELETE", "/api/render", "10.0.3.1", null).status(),
                "第 4 个写方法应超限");
    }
}
