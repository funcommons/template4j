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

    /**
     * 引擎脚本缓存可观测视图 (round2 §A3): 供 {@code RenderMetrics} 构造注入。
     * JsonRenderer (build() 的实现) 实现 {@link RenderEngine.ScriptCacheStats}, 这里直接
     * 暴露同一个 engine bean 的该能力视图 —— 声明类型精确匹配, 保证注入可解析。
     */
    @Bean
    public RenderEngine.ScriptCacheStats scriptCacheStats(RenderEngine renderEngine) {
        // build() (JsonRenderer) 实现 ScriptCacheStats; cast 必需, 因为嵌套接口不继承
        // RenderEngine — 检查在运行期执行 (对内置引擎不可能失败)。
        return (RenderEngine.ScriptCacheStats) renderEngine;
    }
}
