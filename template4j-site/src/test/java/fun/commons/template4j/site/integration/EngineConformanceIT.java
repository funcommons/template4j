package fun.commons.template4j.demo.integration;

import fun.commons.template4j.core.RenderEngine;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import fun.commons.template4j.demo.DemoApplication;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = DemoApplication.class)
class EngineConformanceIT {

    @Autowired
    private RenderEngine renderEngine;

    @Test
    void engineIsConfigured() {
        assertNotNull(renderEngine);
    }

    @Test
    void renderJsonWorks() {
        Object result = renderEngine.renderJson("{\"msg\":\"hello\"}", Map.of());
        assertNotNull(result);
        String json = com.alibaba.fastjson2.JSON.toJSONString(result);
        assertTrue(json.contains("hello"));
    }

    @Test
    void renderYamlWorks() {
        Object result = renderEngine.renderYaml("msg: hello yaml", Map.of());
        assertNotNull(result);
        String json = com.alibaba.fastjson2.JSON.toJSONString(result);
        assertTrue(json.contains("hello yaml"));
    }

    @Test
    void renderWithDataPathWorks() {
        Object result = renderEngine.renderJson(
            "{\"name\":\"{@user.name}\"}",
            Map.of("user", Map.of("name", "test"))
        );
        String json = com.alibaba.fastjson2.JSON.toJSONString(result);
        assertTrue(json.contains("test"));
    }

    @Test
    void renderWithGroovyExpressionWorks() {
        Object result = renderEngine.renderJson(
            "{\"sum\":\"{= 1 + 2 }\"}",
            Map.of()
        );
        String json = com.alibaba.fastjson2.JSON.toJSONString(result);
        assertTrue(json.contains("3"));
    }

    @Test
    void renderWithEachComponentWorks() {
        String template = "{\"items\":{\"@class\":\"each\",\"select\":\"{@users}\",\"itemName\":\"u\",\"body\":{\"n\":\"{$u.name}\"}}}";
        Object result = renderEngine.renderJson(template,
            Map.of("users", java.util.List.of(Map.of("name", "A"), Map.of("name", "B"))));
        String json = com.alibaba.fastjson2.JSON.toJSONString(result);
        assertTrue(json.contains("\"n\":\"A\""));
        assertTrue(json.contains("\"n\":\"B\""));
    }

    @Test
    void renderWithIfComponentWorks() {
        String template = "{\"check\":{\"@class\":\"if\",\"select\":\"{@flag}\",\"is\":\"true\",\"then\":\"yes\",\"else\":\"no\"}}";
        Object result = renderEngine.renderJson(template, Map.of("flag", true));
        String json = com.alibaba.fastjson2.JSON.toJSONString(result);
        assertTrue(json.contains("yes"));
    }
}
