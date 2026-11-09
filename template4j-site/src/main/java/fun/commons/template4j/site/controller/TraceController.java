package fun.commons.template4j.site.controller;

import fun.commons.template4j.core.RenderEngine;
import fun.commons.template4j.site.common.ApiResponse;
import fun.commons.template4j.site.config.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 演示 `engine.withTraceConsumer()` 调试追踪。
 *
 * <p>GET /api/demo/trace?v=42 → 渲染模板并返回收集到的所有 trace 事件 (RENDER_STRING / SCRIPT / COMPONENT 等)。
 */
@RestController
@RequestMapping("/api/demo/trace")
@RequiredArgsConstructor
public class TraceController {

    @GetMapping
    public ApiResponse<Map<String, Object>> demo(@RequestParam(defaultValue = "42") int v,
                                                  HttpServletRequest httpReq) {
        // 每次请求建独立 engine + 独立 consumer, 避免事件串
        List<RenderEngine.TraceEvent> events = new CopyOnWriteArrayList<>();
        RenderEngine engine = RenderEngine.build().withTraceConsumer(events::add);

        String template = "{\"v\":\"{@v}\",\"doubled\":\"{= @v * 2 }\"}";
        Object result = engine.renderJson(template, Map.of("v", v));

        List<Map<String, Object>> serialized = events.stream()
                .map(ev -> {
                    Map<String, Object> m = new java.util.LinkedHashMap<>();
                    m.put("type", ev.type());
                    m.put("path", ev.path());
                    m.put("message", ev.message());
                    m.put("durationMs", ev.durationMs());
                    return m;
                })
                .toList();

        Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("result", result);
        out.put("events", serialized);
        out.put("explanation",
                "RENDER_STRING 是字符串模板的渲染事件, SCRIPT 是 {=...} / {%...%} Groovy 求值事件。"
                + "未注册 consumer 时零开销。生产用 ring buffer + 异步 consumer 摊销。");
        return ApiResponse.success(out, (String) httpReq.getAttribute(TraceIdFilter.TRACE_ID_KEY));
    }
}
