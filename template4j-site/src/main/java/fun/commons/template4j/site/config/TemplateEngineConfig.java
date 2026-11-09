package fun.commons.template4j.demo.config;

import fun.commons.template4j.core.RenderEngine;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(DemoProperties.class)
public class TemplateEngineConfig {

    @Bean
    public RenderEngine renderEngine() {
        return RenderEngine.build();
    }
}
