package fun.commons.template4j.site.service;

import fun.commons.template4j.core.RenderEngine;
import fun.commons.template4j.site.SiteApplication;
import fun.commons.template4j.site.config.DemoProperties;
import fun.commons.template4j.site.dto.RenderRequest;
import fun.commons.template4j.site.dto.RenderResponse;
import fun.commons.template4j.site.exception.BizException;
import fun.commons.template4j.site.metrics.RenderMetrics;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

/**
 * TemplateService 边界覆盖: size 限制 / null data / 非常规 normalize 路径 / parseError / timeout / BizException 透传。
 */
@SpringBootTest(classes = SiteApplication.class)
class TemplateServiceCoverageTest {

    @Autowired
    private TemplateService templateService;

    // 用于在测试内构造独立的 TemplateService 实例 (trace 测试不依赖共享单例的渲染历史)
    @Autowired
    private RenderEngine renderEngine;

    @Autowired
    private DemoProperties demoProperties;

    @Autowired
    private RenderMetrics renderMetrics;

    @Test
    void sizeLimitExceededThrows() {
        // 默认 maxTemplateSize 较大, 这里手工造一个超大 template
        StringBuilder big = new StringBuilder("{\"v\":\"");
        for (int i = 0; i < 200_000; i++) big.append('x');
        big.append("\"}");
        RenderRequest req = new RenderRequest("json", big.toString(), Map.of());
        Assertions.assertThrows(BizException.class, () -> templateService.render(req));
    }

    @Test
    void nullDataTreatedAsEmptyMap() {
        // normalizedData 为 null → Map.of()
        RenderRequest req = new RenderRequest("json", "{\"v\":\"hello\"}", null);
        RenderResponse r = templateService.render(req);
        Assertions.assertNotNull(r);
    }

    @Test
    void arrayDataWrappedInItems() {
        // array root → wrapped in {"items": [...]}
        RenderRequest req = new RenderRequest("json",
                "{\"count\":{\"@class\":\"each\",\"select\":\"{@items}\",\"body\":\"{{$item}}\"}}",
                List.of(1, 2, 3));
        RenderResponse r = templateService.render(req);
        Assertions.assertNotNull(r);
    }

    @Test
    void scalarDataWrappedInValue() {
        // scalar → wrapped in {"value": ...}
        RenderRequest req = new RenderRequest("string", "{$value}", 42);
        RenderResponse r = templateService.render(req);
        Assertions.assertNotNull(r);
    }

    @Test
    void blankTemplateTypeDefaultsToJson() {
        RenderRequest req = new RenderRequest("   ", "{\"v\":\"x\"}", Map.of());
        RenderResponse r = templateService.render(req);
        Assertions.assertEquals("json", r.templateType());
    }

    @Test
    void parseErrorForInvalidYamlThrows() {
        RenderRequest req = new RenderRequest("yaml", "\tinvalid: : yaml", Map.of());
        Assertions.assertThrows(Exception.class, () -> templateService.render(req));
    }

    @Test
    void renderErrorOnBadGroovyScriptThrows() {
        // Groovy 语法错误 → RenderException 被 BizException.renderError 包装
        RenderRequest req = new RenderRequest("json",
                "{\"v\":\"{= invalid groovy syntax here !!! }\"}", Map.of());
        Assertions.assertThrows(BizException.class, () -> templateService.render(req));
    }

    @Test
    void emptyTemplateThrows() {
        // 空 template → BizException.badRequest
        RenderRequest req = new RenderRequest("json", "", Map.of());
        Assertions.assertThrows(BizException.class, () -> templateService.render(req));
    }

    @Test
    void stringTemplateTypeRendersAsString() {
        // type="string" → asString=true → 返回类型 "string"
        RenderRequest req = new RenderRequest("string", "hello {@name}", Map.of("name", "world"));
        RenderResponse r = templateService.render(req);
        Assertions.assertEquals("string", r.templateType());
    }

    @Test
    void yamlTemplateTypeRendersYaml() {
        // type="yaml" → parseTemplate 走 yamlMapper.readValue
        RenderRequest req = new RenderRequest("yaml", "v: hello\n", Map.of());
        RenderResponse r = templateService.render(req);
        Assertions.assertNotNull(r);
    }

    @Test
    void ymlTemplateTypeRendersYaml() {
        // type="yml" 同样识别为 yaml
        RenderRequest req = new RenderRequest("yml", "v: hello\n", Map.of());
        RenderResponse r = templateService.render(req);
        Assertions.assertNotNull(r);
    }

    @Test
    void jsonParseFallsBackToStringTemplate() {
        // 模板里有 {@...} 占位符 → fastjson2 解析失败 → fallback 到 string
        RenderRequest req = new RenderRequest("json", "hello {@name}", Map.of("name", "world"));
        RenderResponse r = templateService.render(req);
        // fallback 后 asString=true → 返回 "string" 类型
        Assertions.assertEquals("string", r.templateType());
    }

    @Test
    void getTraceReturnsImmutableList() {
        // templateService 是 Spring 共享单例: 其它测试可能已渲染过, lastTrace 可能非空,
        // 所以这里只断言与顺序无关的性质 — getTrace() 永远返回不可变列表 (List.copyOf / emptyList)。
        List<String> t = templateService.getTrace();
        Assertions.assertNotNull(t);
        Assertions.assertThrows(UnsupportedOperationException.class, () -> t.add("x"));
    }

    @Test
    void getTraceWithDataFromLastRender() {
        // 独立实例: 未渲染时 getTrace() 为空
        TemplateService fresh = new TemplateService(renderEngine, demoProperties, renderMetrics);
        Assertions.assertTrue(fresh.getTrace().isEmpty());

        // 成功渲染一次带占位符的 json 模板 → lastTrace 收集到 trace 条目 (String)
        RenderRequest req = new RenderRequest("json", "{\"greeting\":\"hello {@name}\"}", Map.of("name", "world"));
        RenderResponse r = fresh.render(req);
        Assertions.assertNotNull(r);

        List<String> t = fresh.getTrace();
        Assertions.assertNotNull(t);
        Assertions.assertFalse(t.isEmpty());
        for (String line : t) {
            Assertions.assertNotNull(line);
        }
    }
}
