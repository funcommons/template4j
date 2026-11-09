package fun.commons.template4j.demo.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "template4j.demo")
public class DemoProperties {
    private int renderTimeoutMs = 5000;
    private int maxTemplateSize = 65536;
}
