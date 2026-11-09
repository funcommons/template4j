package fun.commons.template4j.site.config;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.util.List;

/**
 * RateLimitFilter S1 防御性分支补充测试 (与 {@link RateLimitFilterTrustProxyTest} 互补, 不重复其 12 个规格点)。
 * 纯 Filter 直测 (Spring mock-web + jakarta servlet), 不启动 Spring 上下文, 不 import bucket4j 类型。
 *
 * <p>分支 → 测试方法映射 (每条对应源码中的一个此前未覆盖的条件分支):
 * <ul>
 *   <li>构造器 {@code trustedProxies == null} → {@link #nullTrustedProxiesCollectionBehavesAsEmptyList}</li>
 *   <li>构造器 {@code Math.max(1, maxBuckets)} 钳制 (0 / 负数) → {@link #maxBucketsBelowOneIsClampedToOne}</li>
 *   <li>clientKey: 可信代理但 XFF 头缺失 (null) → {@link #trustedProxyWithoutXffHeaderKeysByRemoteAddr}</li>
 *   <li>clientKey: XFF 全空白 / 首段 trim 后为空 → {@link #blankOrEmptyFirstXffSegmentFallsBackToRemoteAddr}</li>
 *   <li>isTrustedProxy: remoteAddr 空白 → {@link #blankRemoteAddrIsUntrusted}</li>
 *   <li>parseLiteralAddress: 字符白名单拒绝 / InetAddress 异常捕获 → {@link #unparseableRemoteAddrFailsClosed}</li>
 *   <li>isTrustedProxy+parseLiteralAddress: IPv6 remoteAddr 对 IPv4 网段 (地址族不一致) / 长度 &gt; 45 拒绝 →
 *       {@link #ipv6AndOverlongRemoteAddrFailClosedAgainstIpv4Cidr}</li>
 *   <li>matchesTrustedEntry: 前缀长 &lt; 0 / &gt; 位数 (含 /32 上边界对照) → {@link #outOfRangeCidrPrefixLengthFailsClosed}</li>
 *   <li>matchesTrustedEntry: 网络段不可解析 (白名单 / 空网络段) / 前缀长非数字 → {@link #malformedCidrEntriesFailClosed}</li>
 *   <li>matchesTrustedEntry: 精确 IP 条目不可解析 / 解析成功但不相等 → {@link #exactIpEntryUnparseableOrMismatchFailsClosed}</li>
 *   <li>prefixMatches: 非整字节前缀 (如 /12) 的高位掩码比较 → {@link #nonByteAlignedPrefixComparedThroughHighBitMask}</li>
 *   <li>守卫链剩余分支: remoteAddr == null / 条目 null / 条目空白 → {@link #privateGuardsRejectNullAndBlankEntries}</li>
 * </ul>
 *
 * <p><b>反射直测注明</b>: {@link #privateGuardsRejectNullAndBlankEntries} 通过反射调用 private 守卫方法
 * ({@code isTrustedProxy} / {@code matchesTrustedEntry}) —— 这三个分支无法通过请求流以可断言的正确行为触达:
 * null/空白条目被构造器清洗排除 (null 元素在 {@code String::trim} 处即 NPE); remoteAddr==null 虽可触达守卫,
 * 但随后 {@code buckets.get(null, …)} 会被 Caffeine 拒绝 (NPE), 公共行为上无法断言"正确回退"。
 *
 * <p><b>测试输入纪律</b>: 所有进入 {@code InetAddress.getByName} 的字面量必须是确定可解析的合法字面量 ——
 * 非法但全白名单字符的串 (如 {@code 10.0.0.999}) 会退化为 DNS 查询 (结果随环境变化且可能超时), 禁止使用;
 * 确定性异常仅用 IPv6 zone-index 越界 ({@code fe80::1%2147483648} → "no such interface", 纯本地接口查找, 不走 DNS)。
 */
class RateLimitFilterBranchCoverageTest {

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

    // ------------------------------------------------ 构造器: trustedProxies == null → List.of()

    @Test
    void nullTrustedProxiesCollectionBehavesAsEmptyList() throws Exception {
        // null 信任集合必须被归一为空列表 (不抛 NPE), 语义等价于"无可信代理": XFF 一律忽略, 按 remoteAddr 计数
        RateLimitFilter filter = new RateLimitFilter(true, 1, 1, true, null, 100);
        Assertions.assertTrue(send(filter, "POST", "/api/render", "10.0.0.5", "203.0.113.7").passedChain(),
                "第 1 次请求应放行");
        Attempt rotated = send(filter, "POST", "/api/render", "10.0.0.5", "8.8.8.8");
        Assertions.assertEquals(429, rotated.status(),
                "null 信任集合下轮换 XFF 不能绕过限流 (应命中同一 remoteAddr 桶)");
        Assertions.assertFalse(rotated.passedChain());
        Assertions.assertEquals(1, filter.bucketCount(), "null 信任集合只应产生 remoteAddr 一个桶");
    }

    // ------------------------------------------------ 构造器: maxBuckets < 1 → Math.max(1, …) 钳制

    @Test
    void maxBucketsBelowOneIsClampedToOne() throws Exception {
        // maxBuckets=0 / 负数必须钳到 1: 构造不抛异常, 桶表仍有效且不超过 1
        RateLimitFilter zero = new RateLimitFilter(true, 100, 1, false, List.of(), 0);
        RateLimitFilter negative = new RateLimitFilter(true, 100, 1, false, List.of(), -7);
        for (int i = 1; i <= 3; i++) {
            Attempt a = send(zero, "POST", "/api/render", "10.20.0." + i, null);
            Assertions.assertTrue(a.passedChain(), "容量 100 足够, maxBuckets=0 下第 " + i + " 个客户端应放行");
            Attempt b = send(negative, "POST", "/api/render", "10.21.0." + i, null);
            Assertions.assertTrue(b.passedChain(), "容量 100 足够, maxBuckets=-7 下第 " + i + " 个客户端应放行");
        }
        Assertions.assertTrue(zero.bucketCount() > 0, "确有计数发生, 桶数量应 > 0");
        Assertions.assertTrue(zero.bucketCount() <= 1, "maxBuckets=0 应钳到 1, 实际桶数: " + zero.bucketCount());
        Assertions.assertTrue(negative.bucketCount() <= 1, "maxBuckets=-7 应钳到 1, 实际桶数: " + negative.bucketCount());
    }

    // ------------------------------------------------ clientKey: 可信代理但 XFF 头缺失 (null)

    @Test
    void trustedProxyWithoutXffHeaderKeysByRemoteAddr() throws Exception {
        // remoteAddr 可信但请求不带 XFF 头 (getHeader 返回 null) → 回退 remoteAddr, 不抛异常
        RateLimitFilter filter = new RateLimitFilter(true, 1, 1, true, List.of("10.0.0.0/8"), 1000);
        Assertions.assertTrue(send(filter, "POST", "/api/render", "10.0.0.2", null).passedChain(),
                "无 XFF 头的第 1 次请求应按 remoteAddr 建桶放行");
        Assertions.assertEquals(429,
                send(filter, "POST", "/api/render", "10.0.0.2", null).status(),
                "无 XFF 头的第 2 次请求应命中同一 remoteAddr 桶");
        Assertions.assertTrue(send(filter, "POST", "/api/render", "10.0.0.2", "203.0.113.7").passedChain(),
                "带 XFF 的请求应独立建桶 (证明前两次按 remoteAddr 计数而非按 XFF)");
        Assertions.assertEquals(2, filter.bucketCount(), "应为 remoteAddr 桶 + XFF 桶共 2 个");
    }

    // ------------------------------------------ clientKey: XFF 全空白 / 首段 trim 后为空 → 回退 remoteAddr

    @Test
    void blankOrEmptyFirstXffSegmentFallsBackToRemoteAddr() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(true, 1, 1, true, List.of("10.0.0.0/8"), 1000);
        // XFF 全空白 → isBlank → 回退 remoteAddr
        Assertions.assertTrue(send(filter, "POST", "/api/render", "10.0.0.2", "   ").passedChain(),
                "全空白 XFF 应回退 remoteAddr 建桶放行");
        Assertions.assertEquals(429,
                send(filter, "POST", "/api/render", "10.0.0.2", null).status(),
                "空白 XFF 请求与无 XFF 请求应命中同一 remoteAddr 桶 (若空白被当成 clientKey 则此处会放行)");
        // XFF 首段 trim 后为空 (逗号开头) → 回退 remoteAddr
        Assertions.assertTrue(send(filter, "POST", "/api/render", "10.0.0.3", ", 203.0.113.9").passedChain(),
                "首段为空的 XFF 应回退 remoteAddr 建桶放行");
        Assertions.assertTrue(
                send(filter, "POST", "/api/render", "10.0.0.3", "203.0.113.9").passedChain(),
                "若空首段请求误以 203.0.113.9 建桶, 此处会 429; 正确行为是独立放行");
        Assertions.assertEquals(429,
                send(filter, "POST", "/api/render", "10.0.0.3", null).status(),
                "空首段请求应命中 10.0.0.3 的 remoteAddr 桶 (已被上一请求耗尽)");
        Assertions.assertEquals(3, filter.bucketCount(),
                "应为 10.0.0.2 / 10.0.0.3 / 203.0.113.9 恰好 3 个桶");
    }

    // ------------------------------------------------ isTrustedProxy: remoteAddr 空白 → false

    @Test
    void blankRemoteAddrIsUntrusted() throws Exception {
        // remoteAddr 为空白串: 不解析、不匹配信任列表, XFF 一律忽略, 按空白 remoteAddr 原样计数
        RateLimitFilter filter = new RateLimitFilter(true, 1, 1, true, List.of("10.0.0.0/8"), 1000);
        Assertions.assertTrue(send(filter, "POST", "/api/render", "   ", "203.0.113.7").passedChain(),
                "空白 remoteAddr 第 1 次应放行 (fail-closed, XFF 忽略)");
        Assertions.assertEquals(429,
                send(filter, "POST", "/api/render", "   ", "8.8.8.8").status(),
                "空白 remoteAddr 轮换 XFF 不能绕过限流");
        Assertions.assertTrue(send(filter, "POST", "/api/render", "", "203.0.113.7").passedChain(),
                "空串 remoteAddr 与空白串应各自独立计数");
        Assertions.assertEquals(429,
                send(filter, "POST", "/api/render", "", "9.9.9.9").status(),
                "空串 remoteAddr 轮换 XFF 不能绕过限流");
        Assertions.assertEquals(2, filter.bucketCount(), "空白串与空串应为恰好 2 个独立桶");
    }

    // ------------------------------ parseLiteralAddress: 字符白名单拒绝 / InetAddress 异常捕获 → null

    @Test
    void unparseableRemoteAddrFailsClosed() throws Exception {
        // 注意: 不能用 "10.0.0.999" 之类的非法 IPv4 —— getByName 会退化为 DNS 查询, 结果随环境变化;
        // 白名单拒绝用 "not-an-ip" (含非白名单字符, 不触达 getByName); 确定性异常用 zone-index 越界的 IPv6
        // ("no such interface", 纯本地接口查找, 不走 DNS)。
        RateLimitFilter filter = new RateLimitFilter(true, 1, 1, true, List.of("10.0.0.0/8"), 1000);
        Assertions.assertTrue(send(filter, "POST", "/api/render", "not-an-ip", "203.0.113.7").passedChain(),
                "不可解析 remoteAddr 第 1 次应放行 (fail-closed, XFF 忽略)");
        Assertions.assertEquals(429,
                send(filter, "POST", "/api/render", "not-an-ip", "8.8.8.8").status(),
                "不可解析 remoteAddr 轮换 XFF 不能绕过限流");
        Assertions.assertTrue(
                send(filter, "POST", "/api/render", "fe80::1%2147483648", "203.0.113.7").passedChain(),
                "解析抛异常的 remoteAddr 第 1 次应放行且不向外传播异常");
        Assertions.assertEquals(429,
                send(filter, "POST", "/api/render", "fe80::1%2147483648", "8.8.8.8").status(),
                "解析抛异常的 remoteAddr 轮换 XFF 不能绕过限流");
        Assertions.assertEquals(2, filter.bucketCount(), "两个不可解析来源应为恰好 2 个独立桶");
    }

    // ----------------- 地址族不一致 (IPv6 remoteAddr 对 IPv4 网段) / 字面量长度 > 45 → 不可信; IPv6 网段正向对照

    @Test
    void ipv6AndOverlongRemoteAddrFailClosedAgainstIpv4Cidr() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(true, 1, 1, true, List.of("10.0.0.0/8"), 1000);
        // IPv6 remoteAddr 对 IPv4 CIDR: 字节长度不一致 → 不匹配, XFF 忽略
        Assertions.assertTrue(send(filter, "POST", "/api/render", "2001:db8::1", "203.0.113.7").passedChain(),
                "IPv6 来源对 IPv4 网段应 fail-closed 放行");
        Assertions.assertEquals(429,
                send(filter, "POST", "/api/render", "2001:db8::1", "8.8.8.8").status(),
                "地址族不一致时轮换 XFF 不能绕过限流");
        // 超长字面量 (>45 字符, 全白名单字符): 在字符粗筛后被长度上限拒绝, 不触达 getByName
        final String overlong = "ffff:".repeat(9) + "ffff";
        Assertions.assertEquals(49, overlong.length(), "用例自校验: 必须超过 45 字符上限");
        Assertions.assertTrue(send(filter, "POST", "/api/render", overlong, "203.0.113.7").passedChain(),
                "超长字面量来源应 fail-closed 放行");
        Assertions.assertEquals(429,
                send(filter, "POST", "/api/render", overlong, "8.8.8.8").status(),
                "超长字面量来源轮换 XFF 不能绕过限流");
        Assertions.assertEquals(2, filter.bucketCount(), "两个不可信来源应为恰好 2 个独立桶");

        // 正向对照: IPv6 remoteAddr 对 IPv6 CIDR (2001:db8::/32) 正常命中 �� XFF 生效
        RateLimitFilter v6 = new RateLimitFilter(true, 1, 1, true, List.of("2001:db8::/32"), 1000);
        Assertions.assertTrue(send(v6, "POST", "/api/render", "2001:db8::1", "203.0.113.7").passedChain(),
                "IPv6 网段内的来源应视为可信代理");
        Assertions.assertEquals(429,
                send(v6, "POST", "/api/render", "2001:db8::7fff", "203.0.113.7").status(),
                "同一 /32 前缀内换代理 IP, 相同 XFF 应命中同一桶");
        Assertions.assertTrue(send(v6, "POST", "/api/render", "2001:db8::1", "9.9.9.9").passedChain(),
                "可信 IPv6 代理下不同 XFF 应独立计数");
        Assertions.assertEquals(2, v6.bucketCount(), "IPv6 可信场景应为恰好 2 个 XFF 桶");
    }

    // ------------------------------ matchesTrustedEntry: 前缀长越界 (<0 / >位数) → 不匹配; /32 上边界对照

    @Test
    void outOfRangeCidrPrefixLengthFailsClosed() throws Exception {
        // 两个越界前缀 (33 > 32 位, -1 < 0) 都必须不匹配且不抛异常
        RateLimitFilter bad = new RateLimitFilter(true, 1, 1, true, List.of("10.0.0.5/33", "10.0.0.5/-1"), 1000);
        Assertions.assertTrue(send(bad, "POST", "/api/render", "10.0.0.5", "203.0.113.7").passedChain(),
                "越界前缀应 fail-closed, remoteAddr 回退计数");
        Assertions.assertEquals(429,
                send(bad, "POST", "/api/render", "10.0.0.5", "8.8.8.8").status(),
                "越界前缀下轮换 XFF 不能绕过限流");
        Assertions.assertEquals(1, bad.bucketCount(), "越界前缀下只应有 remoteAddr 一个桶");

        // 上边界对照: /32 恰好等于地址位数, 仍合法 → 命中且仅限该 IP
        RateLimitFilter good = new RateLimitFilter(true, 1, 1, true, List.of("10.0.0.5/32"), 1000);
        Assertions.assertTrue(send(good, "POST", "/api/render", "10.0.0.5", "203.0.113.7").passedChain(),
                "/32 精确主机网段应视为可信代理");
        Assertions.assertTrue(send(good, "POST", "/api/render", "10.0.0.5", "8.8.8.8").passedChain(),
                "/32 可信下不同 XFF 应独立计数");
        Assertions.assertTrue(send(good, "POST", "/api/render", "10.0.0.6", "203.0.113.7").passedChain(),
                "/32 相邻 IP 不应命中, 回退 remoteAddr 独立计数");
        Assertions.assertEquals(429,
                send(good, "POST", "/api/render", "10.0.0.6", "9.9.9.9").status(),
                "回退桶被耗尽后应 429");
        Assertions.assertEquals(3, good.bucketCount(), "应为 2 个 XFF 桶 + 1 个回退桶");
    }

    // ---------------- matchesTrustedEntry: 网络段不可解析 (白名单拒绝 / 空网络段) / 前缀长非数字 → fail-closed

    @Test
    void malformedCidrEntriesFailClosed() throws Exception {
        // "not-an-ip/8": 网络段含非白名单字符 (不触达 getByName); "/8": 网络段为空串;
        // "10.0.0.0/ten": 前缀长 parseInt 抛 NumberFormatException —— 三者都必须不匹配且不抛异常
        RateLimitFilter filter = new RateLimitFilter(true, 1, 1, true,
                List.of("not-an-ip/8", "/8", "10.0.0.0/ten"), 1000);
        Assertions.assertTrue(send(filter, "POST", "/api/render", "10.0.0.7", "203.0.113.7").passedChain(),
                "畸形网段配置应 fail-closed, remoteAddr 回退计数");
        Attempt rotated = send(filter, "POST", "/api/render", "10.0.0.7", "8.8.8.8");
        Assertions.assertEquals(429, rotated.status(), "畸形网段配置下轮换 XFF 不能绕过限流");
        Assertions.assertFalse(rotated.passedChain());
        Assertions.assertEquals(1, filter.bucketCount(), "畸形网段配置下只应有 remoteAddr 一个桶");
    }

    // ---------------- matchesTrustedEntry: 精确 IP 条目不可解析 / 解析成功但不相等 → fail-closed; 精确命中对照

    @Test
    void exactIpEntryUnparseableOrMismatchFailsClosed() throws Exception {
        // "not-an-ip": 精确条目白名单拒绝 (entryBytes == null); "203.0.113.99": 合法解析但与 remoteAddr 不等
        RateLimitFilter bad = new RateLimitFilter(true, 1, 1, true, List.of("not-an-ip", "203.0.113.99"), 1000);
        Assertions.assertTrue(send(bad, "POST", "/api/render", "10.0.0.8", "203.0.113.7").passedChain(),
                "精确条目不可解析/不相等时应 fail-closed 回退");
        Attempt rotated = send(bad, "POST", "/api/render", "10.0.0.8", "8.8.8.8");
        Assertions.assertEquals(429, rotated.status(), "回退桶耗尽后应 429");
        Assertions.assertFalse(rotated.passedChain());
        Assertions.assertEquals(1, bad.bucketCount(), "精确条目失效时只应有 remoteAddr 一个桶");

        // 对照: 精确 IP 命中 → XFF 生效; 邻居 IP 不在精确列表 → 仍回退
        RateLimitFilter good = new RateLimitFilter(true, 1, 1, true, List.of("10.0.0.8"), 1000);
        Assertions.assertTrue(send(good, "POST", "/api/render", "10.0.0.8", "203.0.113.7").passedChain());
        Assertions.assertTrue(send(good, "POST", "/api/render", "10.0.0.8", "8.8.8.8").passedChain(),
                "精确命中时轮换 XFF 应独立计数 (与 bad 场景相反)");
        Assertions.assertTrue(send(good, "POST", "/api/render", "10.0.0.9", "203.0.113.7").passedChain(),
                "精确列表不覆盖邻居 IP, 应回退 remoteAddr 独立计数");
        Assertions.assertEquals(3, good.bucketCount(), "应为 2 个 XFF 桶 + 1 个回退桶");
    }

    // --------------------------------- prefixMatches: 非整字节前缀 (/12) 的高位掩码比较 (命中 / 不命中)

    @Test
    void nonByteAlignedPrefixComparedThroughHighBitMask() throws Exception {
        // 172.16.0.0/12: 首字节整字节比较, 第二字节只比高 4 位 → 172.16.0.0 ~ 172.31.255.255 全命中;
        // 172.32.0.1 第二字节高 4 位 (0x20) != 0x10 → 不命中。条目同时携带空白填充与空串, 覆盖构造器清洗行为。
        RateLimitFilter filter = new RateLimitFilter(true, 1, 1, true, List.of("  172.16.0.0/12  ", ""), 1000);
        Assertions.assertTrue(send(filter, "POST", "/api/render", "172.20.5.6", "203.0.113.7").passedChain(),
                "/12 网段内的来源应视为可信代理");
        Assertions.assertEquals(429,
                send(filter, "POST", "/api/render", "172.31.200.1", "203.0.113.7").status(),
                "第二字节高 4 位相同 (16 vs 31) 应命中同一 XFF 桶 (若掩码失效则此处会放行)");
        Assertions.assertTrue(send(filter, "POST", "/api/render", "172.32.0.1", "203.0.113.7").passedChain(),
                "第二字节高 4 位不同 (0x20 vs 0x10) 不应命中, 回退 remoteAddr");
        Assertions.assertEquals(429,
                send(filter, "POST", "/api/render", "172.32.0.1", "8.8.8.8").status(),
                "回退桶耗尽后轮换 XFF 应 429");
        Assertions.assertEquals(2, filter.bucketCount(), "应为 1 个 XFF 桶 + 1 个回退桶");
    }

    // ------------------------- 守卫链剩余分支: remoteAddr==null / 条目 null / 条目空白 —— 反射直测 (注明)

    /**
     * <b>反射直测注明</b>: {@code isTrustedProxy} 的 {@code remoteAddr == null} 守卫与
     * {@code matchesTrustedEntry} 的 {@code entry == null / entry.isBlank()} 守卫无法通过公共请求流以
     * "可断言的正确行为" 触达 —— 构造器清洗保证条目永不为 null/空白 (null 元素在 {@code String::trim}
     * 处即 NPE); remoteAddr==null 虽能进入守卫, 但其后 {@code buckets.get(null, …)} 会被 Caffeine 拒绝,
     * 公共行为上只能观察到 NPE 而非正确回退。因此此处直接反射调用 private 守卫, 断言其 fail-closed 返回 false。
     */
    @Test
    void privateGuardsRejectNullAndBlankEntries() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(true, 1, 1, true, List.of("10.0.0.0/8"), 100);
        Method isTrustedProxy = RateLimitFilter.class.getDeclaredMethod("isTrustedProxy", String.class);
        isTrustedProxy.setAccessible(true);
        Assertions.assertFalse((boolean) isTrustedProxy.invoke(filter, (String) null),
                "remoteAddr == null 必须守卫为不可信");
        Assertions.assertFalse((boolean) isTrustedProxy.invoke(filter, ""),
                "remoteAddr 为空串必须守卫为不可信 (守卫链完整性)");

        Method matchesTrustedEntry = RateLimitFilter.class
                .getDeclaredMethod("matchesTrustedEntry", byte[].class, String.class);
        matchesTrustedEntry.setAccessible(true);
        byte[] remote = InetAddress.getByName("10.0.0.5").getAddress();
        Assertions.assertTrue((boolean) matchesTrustedEntry.invoke(null, remote, "10.0.0.0/8"),
                "自校验: 合法 CIDR 条目应命中 (证明探针可用)");
        Assertions.assertFalse((boolean) matchesTrustedEntry.invoke(null, remote, (String) null),
                "条目 == null 必须守卫为不匹配");
        Assertions.assertFalse((boolean) matchesTrustedEntry.invoke(null, remote, "   "),
                "条目全空白必须守卫为不匹配");
    }
}
