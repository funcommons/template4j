package fun.commons.template4j.site.config;

import fun.commons.template4j.core.components.RestRenderer;
import fun.commons.template4j.core.RenderEngine;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(DemoProperties.class)
public class TemplateEngineConfig {

    @Bean
    public RenderEngine renderEngine(DemoProperties demoProperties) {
        // rest 组件的相对地址 (以 / 开头) 指向本站点, 让演示模板可以直接写 /users/{@id}
        RestRenderer.setBaseUrl(demoProperties.getRestBaseUrl());
        return RenderEngine.build();
    }
}
