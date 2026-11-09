package fun.commons.template4j.core.components;

import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TDD 契约测试: {@link RestOptions} (rest 组件实例级配置) 与 RestRenderer 的合并语义 (纯单元, 不发网络请求)。业务侧待实现契约:
 * <ul>
 *   <li>{@code RestOptions.builder().baseUrl(String).allowedHosts(List<String>).maxResponseBytes(long).build()}
 *       — 不可变, 三项均可选; 访问器 {@code baseUrl()} / {@code allowedHosts()} / {@code maxResponseBytes()};
 *       未设置时: baseUrl null/空, allowedHosts 空集合 (非 null), maxResponseBytes ≤ 0</li>
 *   <li>{@code RestRenderer.effectiveBaseUrl(RestOptions)} (package-private static):
 *       options.baseUrl 非空 ? options.baseUrl : 静态 baseUrl (setBaseUrl 控制)</li>
 *   <li>{@code RestRenderer.effectiveAllowedHosts(RestOptions)} (package-private static):
 *       静态白名单 (template4j.rest.allowed-hosts) ∪ options.allowedHosts</li>
 *   <li>{@code RestRenderer.effectiveMaxResponseBytes(RestOptions)} (package-private static):
 *       options 值 > 0 ? options 值 : maxResponseBytes()</li>
 *   <li>options == null → 三者全部回退静态默认 (引擎未设 options 时静态 API 仍生效, 与 RestSsrfGuardTest 兼容)</li>
 * </ul>
 * RenderEngine 侧契约 (engine.withRestOptions 可链式 / null=清除回退静态 / 引擎间隔离) 不在本文件调用验证:
 * 业务代码实现前该调用无法编译, @Disabled 也规避不了编译期错误, 由业务实现与其集成测试覆盖。
 */
public class RestOptionsTest {

    private static final String ALLOWED_HOSTS = "template4j.rest.allowed-hosts";
    private static final String MAX_BYTES = "template4j.rest.maxResponseBytes";

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

    @Test
    void builderHoldsAllThreeFields() {
        RestOptions options = RestOptions.builder().baseUrl("http://a.example/").allowedHosts(
                List.of("10.0.0.7", "192.168.1.1")).maxResponseBytes(4096L).build();
        assertEquals("http://a.example/", options.baseUrl());
        assertEquals(List.of("10.0.0.7", "192.168.1.1"), options.allowedHosts());
        assertEquals(4096L, options.maxResponseBytes());
    }

    @Test
    void builderDefaultsAreUnsetWhenFieldsOmitted() {
        RestOptions options = RestOptions.builder().build();
        assertTrue(options.baseUrl() == null || options.baseUrl().isEmpty(), "baseUrl 未设置 → null/空");
        assertTrue(options.allowedHosts().isEmpty(), "allowedHosts 未设置 → 空集合 (非 null)");
        assertTrue(options.maxResponseBytes() <= 0L, "maxResponseBytes 未设置 → ≤0 视为未设置");
    }

    @Test
    void engineLevelBaseUrlIsolatesFromStatic() {
        RestRenderer.setBaseUrl("http://static.example/");
        try {
            RestOptions options = RestOptions.builder().baseUrl("http://a.example/").build();
            assertEquals("http://a.example/", RestRenderer.effectiveBaseUrl(options));
            assertEquals("http://static.example/api/x", RestRenderer.resolveUrl("/api/x"),
                    "静态 baseUrl 不被引擎级 options 污染, 其它默认引擎仍走 static.example");
            assertEquals("http://static.example/",
                    RestRenderer.effectiveBaseUrl(RestOptions.builder().baseUrl("").build()));
        } finally {
            RestRenderer.setBaseUrl("");
        }
    }

    @Test
    void effectiveAllowedHostsMergesStaticAndOptions() {
        String prev = System.getProperty(ALLOWED_HOSTS);
        System.setProperty(ALLOWED_HOSTS, "localhost,10.0.0.7");
        try {
            Collection<String> hosts = RestRenderer.effectiveAllowedHosts(
                    RestOptions.builder().allowedHosts(List.of("192.168.1.1", "10.0.0.7")).build());
            assertTrue(hosts.contains("localhost") && hosts.contains("10.0.0.7"), "静态白名单保留 (含交集)");
            assertTrue(hosts.contains("192.168.1.1"), "options 白名单并入");
            assertFalse(hosts.contains("10.0.0.8"), "未列出的内网主机不得混入");
        } finally {
            restoreAllowedHosts(prev);
        }
    }

    @Test
    void effectiveAllowedHostsWithNullOptionsUsesStaticOnly() {
        String prev = System.getProperty(ALLOWED_HOSTS);
        System.setProperty(ALLOWED_HOSTS, "localhost");
        try {
            Collection<String> hosts = RestRenderer.effectiveAllowedHosts(null);
            assertTrue(hosts.contains("localhost"));
            assertFalse(hosts.contains("192.168.1.1"), "无 options 时不得新增白名单");
        } finally {
            restoreAllowedHosts(prev);
        }
    }

    @Test
    void effectiveAllowedHostsAllowsOptionsOnlyWhenNoStaticProperty() {
        String prev = System.getProperty(ALLOWED_HOSTS);
        System.clearProperty(ALLOWED_HOSTS);
        try {
            assertTrue(RestRenderer.effectiveAllowedHosts(
                    RestOptions.builder().allowedHosts(List.of("192.168.1.2")).build()).contains("192.168.1.2"));
            assertTrue(RestRenderer.effectiveAllowedHosts(null).isEmpty(), "无属性且无 options → 空白名单");
        } finally {
            restoreAllowedHosts(prev);
        }
    }

    @Test
    void effectiveMaxResponseBytesPrefersPositiveOptionValue() {
        String prev = System.getProperty(MAX_BYTES);
        System.setProperty(MAX_BYTES, "8192");
        try {
            assertEquals(4096L, RestRenderer.effectiveMaxResponseBytes(
                    RestOptions.builder().maxResponseBytes(4096L).build()));
            assertEquals(8192L, RestRenderer.effectiveMaxResponseBytes(RestOptions.builder().maxResponseBytes(0L).build()));
            assertEquals(8192L, RestRenderer.effectiveMaxResponseBytes(RestOptions.builder().maxResponseBytes(-1L).build()));
            assertEquals(8192L, RestRenderer.effectiveMaxResponseBytes(null));
        } finally {
            restoreMaxBytes(prev);
        }
    }

    @Test
    void nullOptionsFallBackToStaticDefaultsEverywhere() {
        String prev = System.getProperty(ALLOWED_HOSTS);
        RestRenderer.setBaseUrl("http://static.example/");
        System.setProperty(ALLOWED_HOSTS, "10.0.0.7");
        try {
            assertEquals("http://static.example/", RestRenderer.effectiveBaseUrl(null));
            assertTrue(RestRenderer.effectiveAllowedHosts(null).contains("10.0.0.7"));
        } finally {
            RestRenderer.setBaseUrl("");
            restoreAllowedHosts(prev);
        }
    }
}
