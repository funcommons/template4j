package fun.commons.template4j.site.config;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.List;

/**
 * RateLimitFilter S1 安全修复契约测试 (trust-proxy + 有界桶表)。纯 Filter 直测 (Spring mock-web + jakarta
 * servlet), 不启动 Spring 上下文, 不 import bucket4j 类型, 风格与 {@link RateLimitFilterTest} 一致。
 *
 * <p>实现契约 (业务代码需满足, 见 docs/analysis/optimization-plan-round2.md §S1):
 * <ul>
 *   <li>新增全参构造器: {@code RateLimitFilter(boolean enabled, int capacity, int refillMinutes,
 *       boolean trustProxy, Collection&lt;String&gt; trustedProxies, int maxBuckets)};
 *       现有 3 参构造器保留为兼容委托, 默认 trustProxy=false / trustedProxies 空 / maxBuckets=100000</li>
 *   <li>trustProxy=false (默认): clientKey 一律取 remoteAddr, 即使带 X-Forwarded-For 头 → 攻击者轮换 XFF
 *       仍命中同一个桶, 限流不被绕过</li>
 *   <li>trustProxy=true 且 remoteAddr 命中 trustedProxies (支持精确 IP 与 IPv4 CIDR 如 10.0.0.0/8):
 *       取 XFF 首值 (trim) 作为 clientKey</li>
 *   <li>trustProxy=true 但 remoteAddr 不在 trustedProxies: 仍用 remoteAddr (XFF 被忽略)</li>
 *   <li>桶表有界: maxBuckets=N 时制造超过 N 个不同 clientKey 后, 桶数量不超过 N
 *       (经包级可见方法 {@code bucketCount()} 断言)</li>
 *   <li>回归保持: enabled=false 全放行; GET 不拦; 写方法耗尽返回 429 + Retry-After + JSON 体
 *       {"error":"rate_limited",...}; XFF 多值 (逗号分隔) 取首值</li>
 * </ul>
 */
class RateLimitFilterTrustProxyTest {

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

    private Attempt send(RateLimitFilter filter, String method, String path, String remoteAddr, String xff)
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

    // ---------------------------------------------------------------- 规格点 1: trustProxy=false 忽略 XFF

    @Test
    void trustProxyDisabledIgnoresXffEvenIfRemoteAddrInTrustedList() throws Exception {
        // trustProxy=false 时 trustedProxies 即使能命中 remoteAddr 也不生效: XFF 一律忽略, 按 remoteAddr 计数
        RateLimitFilter filter = new RateLimitFilter(true, 1, 1, false, List.of("10.0.0.0/8"), 1000);
        Assertions.assertTrue(send(filter, "POST", "/api/render", "10.0.0.5", "203.0.113.7").passedChain(),
                "第 1 次请求应放行");
        // 攻击者轮换 XFF: remoteAddr 相同 → 仍命中同一个桶
        Attempt rotated = send(filter, "POST", "/api/render", "10.0.0.5", "8.8.8.8");
        Assertions.assertEquals(429, rotated.status(), "trustProxy=false 时轮换 XFF 不能绕过限流");
        Assertions.assertFalse(rotated.passedChain());
        // 两个不同 XFF 值只产生 1 个桶 (按 remoteAddr)
        Assertions.assertEquals(1, filter.bucketCount(), "trustProxy=false 时只应有 remoteAddr 一个桶");
    }

    @Test
    void legacyConstructorDelegatesWithProxyTrustOff() throws Exception {
        // 3 参兼容构造器等价于 trustProxy=false: XFF 轮换不绕过, 桶按 remoteAddr 收敛
        RateLimitFilter filter = new RateLimitFilter(true, 1, 1);
        Assertions.assertTrue(send(filter, "POST", "/api/render", "10.1.0.1", "203.0.113.1").passedChain());
        Assertions.assertEquals(429,
                send(filter, "POST", "/api/render", "10.1.0.1", "203.0.113.2").status(),
                "兼容构造器默认不信任 XFF, 轮换 XFF 应命中同一桶");
        Assertions.assertEquals(1, filter.bucketCount(), "兼容构造器下不同 XFF 不应产生多个桶");
    }

    // ---------------------------------------------------- 规格点 2: trustProxy=true 且命中 trustedProxies → 取 XFF

    @Test
    void trustedExactIpProxyUsesXffFirstValue() throws Exception {
        // remoteAddr 精确命中 trustedProxies → XFF 首值作为 clientKey
        RateLimitFilter filter = new RateLimitFilter(true, 1, 1, true, List.of("192.168.1.10"), 1000);
        Assertions.assertTrue(send(filter, "POST", "/api/render", "192.168.1.10", "203.0.113.7").passedChain(),
                "可信代理后的第 1 个客户端应放行");
        // 同一 XFF 再来 → 命中同一桶
        Assertions.assertEquals(429, send(filter, "POST", "/api/render", "192.168.1.10", "203.0.113.7").status(),
                "同一 XFF 应命中同一桶");
        // 换 XFF → 独立桶
        Assertions.assertTrue(send(filter, "POST", "/api/render", "192.168.1.10", "203.0.113.8").passedChain(),
                "可信代理后不同 XFF 应独立计数");
        Assertions.assertEquals(2, filter.bucketCount(), "两个不同 XFF 客户端应恰好 2 个桶");
    }

    @Test
    void trustedCidrSlash8MatchesWholeBlock() throws Exception {
        // 10.0.0.0/8: 10.x.x.x 全部视为可信代理, clientKey 只取决于 XFF 而非具体代理 IP
        RateLimitFilter filter = new RateLimitFilter(true, 1, 1, true, List.of("10.0.0.0/8"), 1000);
        Assertions.assertTrue(send(filter, "POST", "/api/render", "10.201.33.9", "203.0.113.7").passedChain(),
                "10/8 内的 remoteAddr 应视为可信代理");
        // 换了一个同为 10/8 的代理 IP, XFF 不变 → 仍同桶
        Assertions.assertEquals(429, send(filter, "POST", "/api/render", "10.1.2.3", "203.0.113.7").status(),
                "clientKey 只取决于 XFF 首值, 与具体代理 IP 无关");
        // /8 边界: 10.255.255.255 仍命中 → 新 XFF 客户端独立放行
        Assertions.assertTrue(send(filter, "POST", "/api/render", "10.255.255.255", "203.0.113.9").passedChain(),
                "/8 边界地址 10.255.255.255 应命中信任列表");
    }

    @Test
    void trustedCidrSlash24BoundaryMatch() throws Exception {
        // 172.16.5.0/24: 仅 172.16.5.0 ~ 172.16.5.255 可信, /24 边界地址应命中
        RateLimitFilter filter = new RateLimitFilter(true, 1, 1, true, List.of("172.16.5.0/24"), 1000);
        Assertions.assertTrue(send(filter, "POST", "/api/render", "172.16.5.199", "203.0.113.7").passedChain(),
                "/24 网段内的 remoteAddr 应视为可信代理");
        Assertions.assertEquals(429, send(filter, "POST", "/api/render", "172.16.5.255", "203.0.113.7").status(),
                "/24 边界地址 172.16.5.255 应命中且与同 XFF 同桶");
    }

    // ------------------------------------------- 规格点 3: trustProxy=true 但 remoteAddr 不在 trustedProxies → 用 remoteAddr

    @Test
    void untrustedRemoteAddrIgnoresXffEvenWithTrustProxyOn() throws Exception {
        // trustProxy=true 但 remoteAddr (203.0.113.50) 不在 10/8 → XFF 被忽略, 按 remoteAddr 计数
        RateLimitFilter filter = new RateLimitFilter(true, 1, 1, true, List.of("10.0.0.0/8"), 1000);
        Assertions.assertTrue(send(filter, "POST", "/api/render", "203.0.113.50", "203.0.113.7").passedChain());
        Attempt rotated = send(filter, "POST", "/api/render", "203.0.113.50", "8.8.8.8");
        Assertions.assertEquals(429, rotated.status(), "不可信 remoteAddr 下轮换 XFF 应命中同一桶");
        Assertions.assertFalse(rotated.passedChain());
        Assertions.assertEquals(1, filter.bucketCount(), "不可信来源只应产生 remoteAddr 一个桶");
    }

    @Test
    void nonMatchingCidrNetworkFallsBackToRemoteAddr() throws Exception {
        // 配置 192.168.100.0/24, remoteAddr=192.168.101.5 不匹配网段 → XFF 被忽略
        RateLimitFilter filter = new RateLimitFilter(true, 1, 1, true, List.of("192.168.100.0/24"), 1000);
        Assertions.assertTrue(send(filter, "POST", "/api/render", "192.168.101.5", "203.0.113.7").passedChain());
        Assertions.assertEquals(429,
                send(filter, "POST", "/api/render", "192.168.101.5", "9.9.9.9").status(),
                "网段不匹配时轮换 XFF 应被忽略, 命中同一桶");
        Assertions.assertEquals(1, filter.bucketCount(), "网段不匹配时只应有 remoteAddr 一个桶");
    }

    @Test
    void trustProxyWithEmptyTrustedListFallsBackToRemoteAddr() throws Exception {
        // trustProxy=true 但信任列表为空 → 任何 remoteAddr 都不可信, XFF 一律忽略
        RateLimitFilter filter = new RateLimitFilter(true, 1, 1, true, List.of(), 1000);
        Assertions.assertTrue(send(filter, "POST", "/api/render", "10.0.0.9", "203.0.113.7").passedChain());
        Assertions.assertEquals(429,
                send(filter, "POST", "/api/render", "10.0.0.9", "8.8.8.8").status(),
                "空信任列表时 XFF 应被忽略");
        Assertions.assertEquals(1, filter.bucketCount(), "空信任列表时只应有 remoteAddr 一个桶");
    }

    // ------------------------------------------------------------------------ 规格点 4: 桶表有界

    @Test
    void bucketTableIsBoundedByMaxBuckets() throws Exception {
        // maxBuckets=5: 喷射 20 个不同 remoteAddr (clientKey) 后, 内部桶数量不得超过 5
        final int maxBuckets = 5;
        RateLimitFilter filter = new RateLimitFilter(true, 100, 1, false, List.of(), maxBuckets);
        for (int i = 1; i <= 20; i++) {
            Attempt a = send(filter, "POST", "/api/render", "10.9.0." + i, null);
            Assertions.assertTrue(a.passedChain(), "容量 100 足够, 第 " + i + " 个客户端应放行");
        }
        Assertions.assertTrue(filter.bucketCount() > 0, "确有计数发生, 桶数量应 > 0");
        Assertions.assertTrue(filter.bucketCount() <= maxBuckets,
                "制造 20 个 clientKey 后桶数量应 <= " + maxBuckets + ", 实际: " + filter.bucketCount());
    }

    @Test
    void xffSprayThroughTrustedProxyIsBounded() throws Exception {
        // S1 攻击场景: 经可信代理喷射大量不同 XFF, 桶表仍须有界
        final int maxBuckets = 5;
        RateLimitFilter filter = new RateLimitFilter(true, 100, 1, true, List.of("10.0.0.0/8"), maxBuckets);
        for (int i = 1; i <= 20; i++) {
            Attempt a = send(filter, "POST", "/api/render", "10.0.0.2", "203.0.113." + i);
            Assertions.assertTrue(a.passedChain(), "第 " + i + " 个不同 XFF 客户端应放行 (容量 100)");
        }
        Assertions.assertTrue(filter.bucketCount() <= maxBuckets,
                "XFF 喷射后桶数量应 <= " + maxBuckets + ", 实际: " + filter.bucketCount());
    }

    // ------------------------------------------------------------------------------ 规格点 5: 回归保持

    @Test
    void regressionDisabledGetExemptAnd429Shape() throws Exception {
        // 回归: enabled=false 全放行 (即使 trustProxy=true 且带 XFF)
        RateLimitFilter disabled = new RateLimitFilter(false, 1, 1, true, List.of("10.0.0.0/8"), 10);
        for (int i = 0; i < 3; i++) {
            Attempt a = send(disabled, "POST", "/api/render", "10.0.0.1", "203.0.113." + i);
            Assertions.assertTrue(a.passedChain(), "enabled=false 时第 " + i + " 次应放行");
        }
        // 回归: GET 不拦且不消耗令牌; 写方法耗尽 → 429 + Retry-After 头 + JSON 错误体
        RateLimitFilter filter = new RateLimitFilter(true, 2, 1, false, List.of(), 100);
        for (int i = 0; i < 5; i++) {
            Assertions.assertTrue(send(filter, "GET", "/api/render", "10.0.3.1", null).passedChain(),
                    "GET 第 " + i + " 次应放行且不消耗令牌");
        }
        Assertions.assertTrue(send(filter, "POST", "/api/render", "10.0.3.1", null).passedChain());
        Assertions.assertTrue(send(filter, "PUT", "/api/render", "10.0.3.1", null).passedChain());
        Attempt blocked = send(filter, "DELETE", "/api/render", "10.0.3.1", null);
        Assertions.assertFalse(blocked.passedChain(), "第 3 个写请求应被拦截");
        Assertions.assertEquals(429, blocked.status());
        String retryAfter = blocked.response().getHeader("Retry-After");
        Assertions.assertNotNull(retryAfter, "超限响应必须有 Retry-After 头");
        Assertions.assertTrue(Integer.parseInt(retryAfter) >= 1,
                "Retry-After 应 >= 1 秒, 实际: " + retryAfter);
        String body = blocked.response().getContentAsString();
        Assertions.assertTrue(body.contains("\"error\":\"rate_limited\""),
                "JSON 错误体应含 rate_limited, 实际: " + body);
    }

    @Test
    void multiValueXffTakesFirstTrimmedEntryWhenTrusted() throws Exception {
        // 可信代理下多值 XFF 取首值且 trim: "203.0.113.7, 10.0.0.1, 8.8.8.8" 与单值 "203.0.113.7" 同桶
        RateLimitFilter filter = new RateLimitFilter(true, 1, 1, true, List.of("10.0.0.0/8"), 1000);
        Assertions.assertTrue(
                send(filter, "POST", "/api/render", "10.0.0.2", "203.0.113.7, 10.0.0.1, 8.8.8.8").passedChain());
        Assertions.assertEquals(429,
                send(filter, "POST", "/api/render", "10.0.0.3", "203.0.113.7").status(),
                "多值 XFF 应与首值单值同桶");
        // 首值带前导空格: trim 后仍是同一 clientKey
        Assertions.assertEquals(429,
                send(filter, "POST", "/api/render", "10.0.0.4", "  203.0.113.7 , 9.9.9.9").status(),
                "首值 trim 后应命中同一桶");
        // 首值不同 → 独立桶
        Assertions.assertTrue(
                send(filter, "POST", "/api/render", "10.0.0.5", "8.8.8.8, 203.0.113.7").passedChain(),
                "首值不同应独立计数");
        Assertions.assertEquals(2, filter.bucketCount(), "首值 203.0.113.7 与 8.8.8.8 应恰好 2 个桶");
    }
}
