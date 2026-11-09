package fun.commons.template4j.site.service;

import com.alibaba.fastjson2.JSON;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import fun.commons.template4j.core.RenderEngine;
import fun.commons.template4j.core.RenderException;
import fun.commons.template4j.core.RenderOptions;
import fun.commons.template4j.site.config.DemoProperties;
import fun.commons.template4j.site.dto.RenderRequest;
import fun.commons.template4j.site.dto.RenderResponse;
import fun.commons.template4j.site.exception.BizException;
import fun.commons.template4j.site.metrics.RenderMetrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class TemplateService {

    private final RenderEngine renderEngine;
    private final DemoProperties demoProperties;
    private final YAMLMapper yamlMapper;
    private final RenderMetrics renderMetrics;
    /** 最近一次成功渲染的 trace 行 (TraceEvent.format()), 供 TryController 的 with_trace 读取。 */
    private volatile List<String> lastTrace;

    public TemplateService(RenderEngine renderEngine, DemoProperties demoProperties, RenderMetrics renderMetrics) {
        this.renderEngine = renderEngine;
        this.demoProperties = demoProperties;
        this.renderMetrics = renderMetrics;
        this.yamlMapper = new YAMLMapper();
    }

    public RenderResponse render(RenderRequest request) {
        if (request.template() == null || request.template().isEmpty()) {
            throw BizException.badRequest("template is required");
        }
        if (request.template().length() > demoProperties.getMaxTemplateSize()) {
            throw BizException.sizeExceeded(
                "template size " + request.template().length() + " exceeds limit " + demoProperties.getMaxTemplateSize());
        }

        Object normalized = request.normalizedData();
        Map<String, Object> data;
        if (normalized == null) {
            data = Map.of();
        } else if (normalized instanceof Map) {
            data = (Map<String, Object>) normalized;
        } else if (normalized instanceof Iterable) {
            // JSON array root. Engine API only takes a Map, so wrap in a singleton.
            data = Map.of("items", normalized);
        } else {
            // Scalars pass through unchanged — the engine doesn't address them
            // as JSON paths, so consumers should pass an object for full coverage.
            data = Map.of("value", normalized);
        }

        long start = System.currentTimeMillis();
        String type = request.templateType();
        if (type == null || type.isBlank()) type = "json";

        try {
            Object parsed = parseTemplate(type, request.template());
            // if the parser fell back to a raw string template (json with {@...} placeholders),
            // surface that fact to the client so it can render the output as a string.
            boolean asString = "string".equalsIgnoreCase(type) || (parsed instanceof String);
            List<String> trace = new ArrayList<>();
            Object result = renderWithTimeout(parsed, data, trace);
            lastTrace = List.copyOf(trace);
            long timing = System.currentTimeMillis() - start;
            renderMetrics.recordSuccess(type, timing);
            return new RenderResponse(result, asString ? "string" : type, timing);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("Render error", e);
            throw BizException.renderError(e.getMessage());
        }
    }

    private Object parseTemplate(String type, String template) {
        try {
            if ("string".equalsIgnoreCase(type)) {
                return template;
            }
            if ("yaml".equalsIgnoreCase(type) || "yml".equalsIgnoreCase(type)) {
                return yamlMapper.readValue(template, Map.class);
            }
            try {
                return JSON.parse(template);
            } catch (Exception je) {
                // fastjson2 chokes on {@...} / {$...} etc. inside JSON string values.
                // fall back to a raw string template — the engine handles placeholders either way.
                log.debug("JSON parse failed, falling back to string template: {}", je.getMessage());
                return template;
            }
        } catch (Exception e) {
            throw BizException.parseError("Failed to parse template: " + e.getMessage());
        }
    }

    private Object renderWithTimeout(Object template, Map<String, Object> data, List<String> trace) {
        RenderOptions options = RenderOptions.builder()
            .timeoutMs(demoProperties.getRenderTimeoutMs())
            .traceConsumer(event -> trace.add(event.format()))
            .build();
        try {
            return renderEngine.render(template, data, options);
        } catch (RenderException e) {
            if (e.getMessage() != null && e.getMessage().contains("timed out")) {
                throw BizException.timeout("Render timed out after " + demoProperties.getRenderTimeoutMs() + "ms");
            }
            throw e;
        }
    }

    public List<String> getTrace() {
        List<String> trace = lastTrace;
        return trace != null ? trace : Collections.emptyList();
    }
}
