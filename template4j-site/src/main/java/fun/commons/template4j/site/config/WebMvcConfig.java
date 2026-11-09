package fun.commons.template4j.site.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/static/**")
            .addResourceLocations("classpath:/static/");
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/").setViewName("forward:/index.html");
        registry.addViewController("/learn/{id:\\w[-\\w]*}").setViewName("forward:/index.html");
        registry.addViewController("/learn").setViewName("forward:/index.html");
        registry.addViewController("/try").setViewName("forward:/index.html");
        registry.addViewController("/docs").setViewName("forward:/index.html");
    }
}
