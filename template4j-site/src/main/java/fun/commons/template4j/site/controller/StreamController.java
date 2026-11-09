package fun.commons.template4j.site.controller;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import fun.commons.template4j.core.RenderEngine;
import fun.commons.template4j.core.RenderStream;
import fun.commons.template4j.site.config.DemoProperties;
import fun.commons.template4j.site.config.TraceIdFilter;
import fun.commons.template4j.site.dto.TryRequest;
import fun.commons.template4j.site.dto.TryResponse;
import fun.commons.template4j.site.exception.BizException;
import fun.commons.template4j.site.service.TemplateService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.Collections;
import java.util.List;

/**
 * 流式渲染端点: SSE 推送 trace 事件 + 最终结果,适合大模板/慢组件(/api/try/stream)。
 */
@RestController
@RequestMapping("/api/try")
@RequiredArgsConstructor
public class StreamController {

    private final RenderEngine renderEngine;
    private final TemplateService templateService;
    private final DemoProperties demoProperties;

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public StreamingResponseBody stream(@RequestBody TryRequest request, HttpServletRequest httpReq) {
        String traceId = (String) httpReq.getAttribute(TraceIdFilter.TRACE_ID_KEY);

        // 与 /api/try 同口径的模板大小限制 (同步抛出 → GlobalExceptionHandler → 400)
        if (request.template() != null && request.template().length() > demoProperties.getMaxTemplateSize()) {
            throw BizException.sizeExceeded(
                "template size " + request.template().length() + " exceeds limit " + demoProperties.getMaxTemplateSize());
        }

        // 异步启动 RenderStream(转 JSON 模板 → JSONObject → RenderEngine 渲染)
        java.util.Map<String, Object> dataMap = request.data() instanceof java.util.Map
                ? (java.util.Map<String, Object>) request.data() : Collections.emptyMap();

        return (out) -> {
            out.write(("event: hello\ndata: {\"traceId\":\"" + traceId + "\"}\n\n").getBytes());
            out.flush();

            // 兼容 string 类型模板(非 JSON)
            Object parsedTpl;
            try {
                parsedTpl = JSON.parse(request.template());
            } catch (Exception je) {
                // 非 JSON → 当 string 模板处理
                parsedTpl = request.template();
            }

            // 推模式: 边渲染边写 SSE,避免 awaitAll 阻塞
            RenderStream s = renderEngine.renderStream(parsedTpl, dataMap);
            java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(1);
            s.subscribe(
                    ev -> {
                        if (RenderStream.RESULT_TYPE.equals(ev.type())) {
                            JSONObject r = new JSONObject();
                            r.put("rendered", ev.value());
                            try {
                                out.write(("event: done\ndata: " + r.toJSONString() + "\n\n").getBytes());
                                out.flush();
                            } catch (Exception ignored) {}
                        } else if (RenderStream.ERROR_TYPE.equals(ev.type())) {
                            try {
                                JSONObject obj = new JSONObject();
                                obj.put("type", ev.type());
                                obj.put("message", ev.message());
                                obj.put("path", ev.path());
                                out.write(("event: error\ndata: " + obj.toJSONString() + "\n\n").getBytes());
                                out.flush();
                            } catch (Exception ignored) {}
                        } else if (!"RENDER_STRING".equals(ev.type()) || ev.value() != null) {
                            // 普通 trace 事件 (跳过空 RENDER_STRING 噪声)
                            try {
                                JSONObject obj = new JSONObject();
                                obj.put("type", ev.type());
                                obj.put("path", ev.path());
                                obj.put("message", ev.message());
                                obj.put("durationMs", ev.durationMs());
                                out.write(("data: " + obj.toJSONString() + "\n\n").getBytes());
                                out.flush();
                            } catch (Exception ignored) {}
                        }
                    },
                    err -> {
                        try {
                            JSONObject obj = new JSONObject();
                            obj.put("message", err.getMessage());
                            obj.put("type", err.getClass().getSimpleName());
                            out.write(("event: error\ndata: " + obj.toJSONString() + "\n\n").getBytes());
                            out.flush();
                        } catch (Exception ignored) {}
                    },
                    done::countDown);
            try { done.await(30, java.util.concurrent.TimeUnit.SECONDS); }
            catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
        };
    }
}