package fun.commons.template4j.core.components;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TDD 契约测试: RestRenderer SSRF 防护 (纯单元, 不发网络请求, 不依赖 DNS 解析)。
 *
 * <p>业务侧待实现契约:
 * <ul>
 *   <li>{@code RestRenderer.isBlockedHost(String)} — package-private static。
 *       loopback ({@code localhost} / {@code 127.0.0.1} / {@code 0.0.0.0} / {@code ::1}),
 *       私网 ({@code 10/8}, {@code 172.16-31}, {@code 192.168/16}), link-local
 *       ({@code 169.254/16} 云元数据), IPv6 ULA {@code fd00::/8} 与 {@code fe80::} → true;
 *       null / 空串 → true (fail-closed); 大小写不敏感, 尾点 {@code 127.0.0.1.} → true;
 *       普通公网域名 / 公网 IP → false</li>
 *   <li>系统属性 {@code template4j.rest.allowed-hosts} (逗号分隔) 白名单中的主机总是放行</li>
 *   <li>{@code RestRenderer.maxResponseBytes()} — 读系统属性
 *       {@code template4j.rest.maxResponseBytes}, 默认 2097152 (2MB)</li>
 *   <li>{@code resolveUrl} 相对路径拼 baseUrl 行为保持不变</li>
 * </ul>
 */
public class RestSsrfGuardTest {

    private static final String ALLOWED_HOSTS = "template4j.rest.allowed-hosts";
    private static final String MAX_BYTES = "template4j.rest.maxResponseBytes";

    private String prevAllowedHosts;
    private String prevMaxBytes;

    @BeforeEach
    void snapshotAndClearGuardSystemProps() {
        prevAllowedHosts = System.getProperty(ALLOWED_HOSTS);
        prevMaxBytes = System.getProperty(MAX_BYTES);
        // 守卫规则的拦截断言必须在"无白名单"环境下进行 (surefire 注入的
        // 127.0.0.1,localhost,0.0.0.0 会使白名单压过拦截规则); 注入值在 @AfterEach 原样恢复。
        System.clearProperty(ALLOWED_HOSTS);
    }

    /** 恢复 surefire 注入的原值 (存在时), 仅原值不存在才 clear —— 不得无条件清掉注入的白名单。 */
    private static void restoreAllowedHosts(String prev) {
        if (prev == null) {
            System.clearProperty(ALLOWED_HOSTS);
        } else {
            System.setProperty(ALLOWED_HOSTS, prev);
        }
    }

    private static void restoreMaxBytes(String prev) {
        if (prev == null) {
            System.clearProperty(MAX_BYTES);
        } else {
            System.setProperty(MAX_BYTES, prev);
        }
    }

    @AfterEach
    void restoreGuardSystemProps() {
        restoreAllowedHosts(prevAllowedHosts);
        restoreMaxBytes(prevMaxBytes);
    }

    // ---- loopback / 0.0.0.0 / ::1 / 大小写 / 尾点 ----
    @ParameterizedTest
    @ValueSource(strings = {"localhost", "LOCALHOST", "LocalHost", "127.0.0.1",
            "127.0.0.1.", "0.0.0.0", "::1"})
    void loopbackHostsAreBlocked(String host) {
        assertTrue(RestRenderer.isBlockedHost(host), host + " should be blocked");
    }

    // ---- IPv4 私网 + link-local (169.254 = 云元数据端点) ----
    @ParameterizedTest
    @ValueSource(strings = {"10.0.0.1", "10.255.255.255", "172.16.0.1", "172.31.255.255",
            "192.168.0.0", "192.168.1.100", "169.254.169.254", "169.254.1.1"})
    void privateAndLinkLocalV4AreBlocked(String host) {
        assertTrue(RestRenderer.isBlockedHost(host), host + " should be blocked");
    }

    // ---- IPv6 ULA fd00::/8 与 link-local fe80:: (大小写不敏感) ----
    @ParameterizedTest
    @ValueSource(strings = {"fd00::1", "fdff::a", "FD00::ABCD", "fe80::1", "FE80::FF"})
    void ipv6UlaAndLinkLocalAreBlocked(String host) {
        assertTrue(RestRenderer.isBlockedHost(host), host + " should be blocked");
    }

    // ---- 172.16-31 边界: 172.15 / 172.32 不在私网段, 放行 ----
    @ParameterizedTest
    @ValueSource(strings = {"172.15.0.1", "172.32.0.1"})
    void outsidePrivateRangeIsAllowed(String host) {
        assertFalse(RestRenderer.isBlockedHost(host), host + " should not be blocked");
    }

    // ---- 公网域名 / 公网 IP 放行 ----
    @ParameterizedTest
    @ValueSource(strings = {"example.com", "api.example.com", "93.184.216.34", "8.8.8.8", "1.1.1.1"})
    void publicHostsAreAllowed(String host) {
        assertFalse(RestRenderer.isBlockedHost(host), host + " should not be blocked");
    }

    // ---- fail-closed: null / 空串一律拦截 ----
    @ParameterizedTest
    @NullAndEmptySource
    void nullOrEmptyIsBlockedFailClosed(String host) {
        assertTrue(RestRenderer.isBlockedHost(host), "null/empty host must fail closed");
    }

    // ---- 白名单: template4j.rest.allowed-hosts 中的主机总是放行 ----
    @Test
    void allowedHostsWhitelistOverridesRules() {
        String prev = System.getProperty(ALLOWED_HOSTS);
        System.setProperty(ALLOWED_HOSTS, "localhost,10.0.0.7,192.168.1.1");
        try {
            assertFalse(RestRenderer.isBlockedHost("localhost"), "whitelist localhost");
            assertFalse(RestRenderer.isBlockedHost("10.0.0.7"), "whitelist 10.0.0.7");
            assertFalse(RestRenderer.isBlockedHost("192.168.1.1"), "whitelist 192.168.1.1");
            // 不在白名单内的内网主机仍被拦截
            assertTrue(RestRenderer.isBlockedHost("192.168.1.2"));
            assertTrue(RestRenderer.isBlockedHost("10.0.0.8"));
        } finally {
            restoreAllowedHosts(prev);
        }
    }

    // ---- maxResponseBytes: 默认 2MB (2097152) ----
    @Test
    void maxResponseBytesDefaultsTo2MB() {
        String old = System.getProperty(MAX_BYTES);
        System.clearProperty(MAX_BYTES);
        try {
            assertEquals(2097152L, RestRenderer.maxResponseBytes());
        } finally {
            restoreMaxBytes(old);
        }
    }

    // ---- maxResponseBytes: 可通过系统属性覆盖 ----
    @Test
    void maxResponseBytesIsOverridable() {
        String old = System.getProperty(MAX_BYTES);
        System.setProperty(MAX_BYTES, "4096");
        try {
            assertEquals(4096L, RestRenderer.maxResponseBytes());
        } finally {
            restoreMaxBytes(old);
        }
    }

    // ---- resolveUrl: 相对路径拼 baseUrl 行为保持不变 ----
    @Test
    void resolveUrlKeepsBaseUrlBehaviour() {
        try {
            RestRenderer.setBaseUrl("http://inner.example.com:8080");
            assertEquals("http://inner.example.com:8080/api/x", RestRenderer.resolveUrl("/api/x"));
            assertEquals("https://other.example.com/y",
                    RestRenderer.resolveUrl("https://other.example.com/y"));
            RestRenderer.setBaseUrl("http://base/");
            assertEquals("http://base/z", RestRenderer.resolveUrl("/z"));
        } finally {
            RestRenderer.setBaseUrl("");
        }
    }
}
