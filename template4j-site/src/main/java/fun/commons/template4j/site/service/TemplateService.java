package fun.commons.template4j.demo.service;

import com.alibaba.fastjson2.JSON;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import fun.commons.template4j.core.RenderEngine;
import fun.commons.template4j.demo.config.DemoProperties;
import fun.commons.template4j.demo.dto.RenderRequest;
import fun.commons.template4j.demo.dto.RenderResponse;
import fun.commons.template4j.demo.exception.BizException;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

@Slf4j
@Service
public class TemplateService {

    private final RenderEngine renderEngine;
    private final DemoProperties demoProperties;
    private final YAMLMapper yamlMapper;
    private final ExecutorService renderExecutor;
    private final ThreadLocal<List<String>> traceHolder = new ThreadLocal<>();

    public TemplateService(RenderEngine renderEngine, DemoProperties demoProperties) {
        this.renderEngine = renderEngine;
        this.demoProperties = demoProperties;
        this.yamlMapper = new YAMLMapper();
        this.renderExecutor = new ThreadPoolExecutor(
            2, 4, 60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(100),
            new ThreadPoolExecutor.CallerRunsPolicy());
    }

    @PreDestroy
    void shutdown() {
        renderExecutor.shutdownNow();
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
            Object result = renderWithTimeout(parsed, data);
            long timing = System.currentTimeMillis() - start;
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

    private Object renderWithTimeout(Object template, Map<String, Object> data) throws Exception {
        Future<Object> future = renderExecutor.submit(() -> renderEngine.render(template, data));
        try {
            return future.get(demoProperties.getRenderTimeoutMs(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw BizException.timeout("Render timed out after " + demoProperties.getRenderTimeoutMs() + "ms");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof BizException) throw (BizException) cause;
            if (cause instanceof Exception) throw (Exception) cause;
            throw new RuntimeException(cause);
        }
    }

    public List<String> getTrace() {
        List<String> trace = traceHolder.get();
        return trace != null ? new ArrayList<>(trace) : Collections.emptyList();
    }
}
