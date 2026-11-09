package fun.commons.template4j.demo.controller;

import fun.commons.template4j.core.RenderEngine;
import fun.commons.template4j.demo.common.ApiResponse;
import fun.commons.template4j.demo.config.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/engine")
@RequiredArgsConstructor
public class EngineController {

    private final RenderEngine renderEngine;

    @GetMapping
    public ApiResponse<Map<String, Object>> engineInfo(HttpServletRequest request) {
        String traceId = (String) request.getAttribute(TraceIdFilter.TRACE_ID_KEY);
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("version", "0.4.0");
        info.put("components", List.of("rest", "invoke", "if", "each", "switch", "const", "render"));
        info.put("status", "ok");
        return ApiResponse.success(info, traceId);
    }
}
