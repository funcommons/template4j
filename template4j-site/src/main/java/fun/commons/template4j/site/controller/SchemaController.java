package fun.commons.template4j.site.controller;

import fun.commons.template4j.core.RenderEngine;
import fun.commons.template4j.core.RenderException;
import fun.commons.template4j.site.common.ApiResponse;
import fun.commons.template4j.site.config.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 演示 `engine.withInputSchema()` / `withOutputSchema()` fail-fast 校验。
 *
 * <p>POST 不带 id → schema 立即报错。GET 用合法数据展示成功路径。
 */
@RestController
@RequestMapping("/api/demo/schema")
@RequiredArgsConstructor
public class SchemaController {

    private static final String INPUT_SCHEMA = """
            {
              "$schema": "http://json-schema.org/draft-07/schema#",
              "type": "object",
              "properties": {
                "id":   { "type": "integer", "minimum": 1 },
                "name": { "type": "string", "minLength": 1, "maxLength": 50 }
              },
              "required": ["id", "name"]
            }
            """;

    private static final String OUTPUT_SCHEMA = """
            {
              "$schema": "http://json-schema.org/draft-07/schema#",
              "type": "object",
              "properties": {
                "userId":   { "type": "integer" },
                "greeting": { "type": "string" }
              },
              "required": ["userId", "greeting"]
            }
            """;

    private final RenderEngine prototypeEngine = RenderEngine.build()
            .withInputSchema(INPUT_SCHEMA)
            .withOutputSchema(OUTPUT_SCHEMA);

    @GetMapping
    public ApiResponse<Object> demo(HttpServletRequest httpReq) {
        // 合法数据
        Object result = prototypeEngine.renderJson(
                "{\"userId\":\"{@id}\",\"greeting\":\"Hello, {@name}!\"}",
                Map.of("id", 42, "name", "alice"));
        return ApiResponse.success(result, (String) httpReq.getAttribute(TraceIdFilter.TRACE_ID_KEY));
    }

    @PostMapping
    public ApiResponse<Object> demoCustom(@RequestBody Map<String, Object> data,
                                          HttpServletRequest httpReq) {
        try {
            Object result = prototypeEngine.renderJson(
                    "{\"userId\":\"{@id}\",\"greeting\":\"Hello, {@name}!\"}",
                    data);
            return ApiResponse.success(result, (String) httpReq.getAttribute(TraceIdFilter.TRACE_ID_KEY));
        } catch (RenderException e) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("phase", "schema-validation");
            err.put("message", e.getMessage());
            // ApiResponse.success 而非 error, 让前端能拿到结构化错误
            return ApiResponse.success(err, (String) httpReq.getAttribute(TraceIdFilter.TRACE_ID_KEY));
        }
    }
}
