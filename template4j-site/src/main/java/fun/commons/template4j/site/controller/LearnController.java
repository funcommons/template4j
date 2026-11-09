package fun.commons.template4j.demo.controller;

import fun.commons.template4j.demo.common.ApiResponse;
import fun.commons.template4j.demo.config.TraceIdFilter;
import fun.commons.template4j.demo.dto.ExampleDetail;
import fun.commons.template4j.demo.dto.ExampleMeta;
import fun.commons.template4j.demo.service.ExampleService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/api/learn")
@RequiredArgsConstructor
public class LearnController {

    private final ExampleService exampleService;

    @GetMapping
    public ApiResponse<Map<String, Object>> list(HttpServletRequest request) {
        String traceId = (String) request.getAttribute(TraceIdFilter.TRACE_ID_KEY);
        List<ExampleMeta> examples = exampleService.list();
        List<String> categories = examples.stream()
            .map(ExampleMeta::category)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("examples", examples);
        data.put("total", examples.size());
        data.put("categories", categories);
        return ApiResponse.success(data, traceId);
    }

    @GetMapping("/{id}")
    public ApiResponse<ExampleDetail> detail(@PathVariable("id") String id, HttpServletRequest request) {
        String traceId = (String) request.getAttribute(TraceIdFilter.TRACE_ID_KEY);
        ExampleDetail detail = exampleService.load(id);
        return ApiResponse.success(detail, traceId);
    }
}
