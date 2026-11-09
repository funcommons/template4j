package fun.commons.template4j.site.controller;

import fun.commons.template4j.core.RenderEngine;
import fun.commons.template4j.site.common.ApiResponse;
import fun.commons.template4j.site.config.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 演示 `engine.compile()` + 内部 parseStrTemplate LRU 缓存的性能差异。
 *
 * <p>同一模板渲染 N 次: 第一次裸 render (冷启动 + 缓存 miss), 第二次仍裸 render (缓存 hit),
 * 第三次 compiled.render (复用编译产物)。三次耗时对比。
 */
@RestController
@RequestMapping("/api/demo/compile")
@RequiredArgsConstructor
public class CompileController {

    private final RenderEngine renderEngine;

    @GetMapping
    public ApiResponse<Map<String, Object>> demo(HttpServletRequest httpReq) {
        String template = "{\"v\":\"{@v}\",\"v2\":\"{@v2}\",\"v3\":\"{= @v + @v2 }\"}";
        Map<String, Object> data = Map.of("v", 42, "v2", 100);
        int iterations = 5000;

        long t1 = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            renderEngine.renderJson(template, data);
        }
        long bareNanos = System.nanoTime() - t1;

        RenderEngine.Template compiled;
        try {
            compiled = renderEngine.compileJson(template);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        long t2 = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            try { compiled.render(data); } catch (Exception ignored) {}
        }
        long compiledNanos = System.nanoTime() - t2;

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("iterations", iterations);
        out.put("bareRenderTotalMs", bareNanos / 1_000_000);
        out.put("compiledRenderTotalMs", compiledNanos / 1_000_000);
        out.put("bareAvgNs", bareNanos / iterations);
        out.put("compiledAvgNs", compiledNanos / iterations);
        out.put("speedup", String.format("%.2fx", (double) bareNanos / compiledNanos));
        out.put("explanation",
                "首次 render 触发 parseStrTemplate, 后续裸 render 走 LRU 缓存 (1024 条), "
                + "compiled.render 也走缓存。两者性能接近, 差异主要在句柄创建。"
                + "若模板只在 1 个 endpoint 用, compile() 主要价值是 API 显式。");
        return ApiResponse.success(out, (String) httpReq.getAttribute(TraceIdFilter.TRACE_ID_KEY));
    }
}
