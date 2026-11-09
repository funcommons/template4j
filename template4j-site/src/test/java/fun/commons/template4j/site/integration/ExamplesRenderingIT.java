package fun.commons.template4j.demo.integration;

import com.alibaba.fastjson2.JSON;
import fun.commons.template4j.core.RenderEngine;
import fun.commons.template4j.demo.DemoApplication;
import fun.commons.template4j.demo.service.ExampleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = DemoApplication.class)
class ExamplesRenderingIT {

    @Autowired
    private ExampleService exampleService;

    @Autowired
    private RenderEngine renderEngine;

    @BeforeEach
    void setup() {
        assertNotNull(exampleService);
        assertNotNull(renderEngine);
    }

    // ---- existing specific tests ----

    @Test
    void textData() {
        var ex = exampleService.load("text-data");
        assertNotNull(ex.template());
        assertNotNull(ex.data());
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) ex.data();
        Object result = renderEngine.render(ex.template(), data);
        assertNotNull(result);
        assertTrue(result.toString().contains("小红"));
    }

    @Test
    void textVars() {
        var ex = exampleService.load("text-vars");
        assertNotNull(ex.template());
        assertNotNull(ex.data());
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) ex.data();
        Object result = renderEngine.renderJson(ex.template(), data);
        String json = JSON.toJSONString(result);
        assertTrue(json.contains("小明喜欢小红"));
    }

    @Test
    void textExpression() {
        var ex = exampleService.load("text-expression");
        assertNotNull(ex.template());
        assertNotNull(ex.data());
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) ex.data();
        Object result = renderEngine.renderJson(ex.template(), data);
        String json = JSON.toJSONString(result);
        assertTrue(json.contains("已成年"));
    }

    @Test
    void componentIf() {
        var ex = exampleService.load("component-if");
        assertNotNull(ex.template());
        assertNotNull(ex.data());
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) ex.data();
        Object result = renderEngine.renderJson(ex.template(), data);
        String json = JSON.toJSONString(result);
        assertTrue(json.contains("失败"));
    }

    @Test
    void componentEach() {
        var ex = exampleService.load("component-each");
        assertNotNull(ex.template());
        assertNotNull(ex.data());
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) ex.data();
        Object result = renderEngine.renderJson(ex.template(), data);
        String json = JSON.toJSONString(result);
        assertTrue(json.contains("电影"));
    }

    @Test
    void componentRest() {
        var ex = exampleService.load("component-rest");
        assertNotNull(ex.template());
        assertNotNull(ex.data());
    }

    @Test
    void templateInclude() {
        var ex = exampleService.load("template-include");
        assertNotNull(ex.template());
        assertNotNull(ex.data());
    }

    @Test
    void introHome() {
        var ex = exampleService.load("intro-home");
        assertNotNull(ex.template());
        assertNotNull(ex.data());
    }

    // ---- parameterized: all examples are loadable ----

    static Stream<String> allExampleIds() {
        return Stream.of(
            "intro-home", "quick-start",
            "text-data", "text-vars", "text-expression", "text-escape",
            "script-started", "script-data", "script-vars",
            "template-script", "template-const", "template-vars",
            "template-func", "template-enter", "template-return",
            "template-data", "template-star", "template-comment",
            "template-escape", "template-class", "template-include",
            "component-if", "component-each", "component-switch",
            "component-render", "component-const", "component-invoke",
            "component-rest",
            "builtin-fne", "builtin-cast", "builtin-render",
            "feature-json-key-order", "perf-security",
            "ref-jsonpath", "ref-code-tips"
        );
    }

    @ParameterizedTest
    @MethodSource("allExampleIds")
    void allExamplesLoadable(String id) {
        var detail = exampleService.load(id);
        assertNotNull(detail, "detail should not be null for " + id);
        assertNotNull(detail.title(), "title should not be null for " + id);
    }

    @Test
    void allExamplesFromList() {
        var list = exampleService.list();
        assertTrue(list.size() >= 35, "Should have at least 35 examples, got " + list.size());
        for (var meta : list) {
            var detail = exampleService.load(meta.id());
            assertNotNull(detail, "detail should not be null for " + meta.id());
            assertNotNull(detail.title(), "title should not be null for " + meta.id());
            // markdown-only docs have null template/data
            if (!"markdown".equals(meta.templateType())) {
                assertNotNull(detail.template(), "template should not be null for " + meta.id());
                assertNotNull(detail.data(), "data should not be null for " + meta.id());
            }
        }
    }
}
