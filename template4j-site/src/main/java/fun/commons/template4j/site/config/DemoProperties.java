package fun.commons.template4j.site.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "template4j.demo")
public class DemoProperties {
    private int renderTimeoutMs = 5000;
    private int maxTemplateSize = 65536;
    /** rest 组件相对地址的 base url, 指向本站点 mock 端点。 */
    private String restBaseUrl = "http://localhost:8080";
}
