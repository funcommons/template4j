package fun.commons.template4j.core.components;

import java.util.List;

/** rest 组件实例级配置 (引擎级注入, 见 {@code RenderEngine#withRestOptions}): 三项均可选、不可变; 未设置字段回退 RestRenderer 静态默认。 */
public final class RestOptions {

    private final String baseUrl;
    private final List<String> allowedHosts;
    private final long maxResponseBytes;

    private RestOptions(Builder b) {
        this.baseUrl = b.baseUrl;
        this.allowedHosts = b.allowedHosts == null ? List.of() : List.copyOf(b.allowedHosts);
        this.maxResponseBytes = b.maxResponseBytes;
    }

    public static Builder builder() { return new Builder(); }

    public String baseUrl() { return baseUrl; }
    public List<String> allowedHosts() { return allowedHosts; }
    public long maxResponseBytes() { return maxResponseBytes; }

    /** 链式 Builder: 未调用的字段保持未设置语义 (baseUrl 空 / allowedHosts 空集合 / maxResponseBytes ≤0)。 */
    public static final class Builder {
        private String baseUrl = "";
        private List<String> allowedHosts = List.of();
        private long maxResponseBytes = 0L;

        public Builder baseUrl(String v) { this.baseUrl = v; return this; }
        public Builder allowedHosts(List<String> v) { this.allowedHosts = v; return this; }
        public Builder maxResponseBytes(long v) { this.maxResponseBytes = v; return this; }
        public RestOptions build() { return new RestOptions(this); }
    }
}
