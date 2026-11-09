package fun.commons.template4j.site.controller;

import fun.commons.template4j.site.common.ApiResponse;
import fun.commons.template4j.site.config.TraceIdFilter;
import fun.commons.template4j.site.dto.RenderRequest;
import fun.commons.template4j.site.dto.TryRequest;
import fun.commons.template4j.site.dto.TryResponse;
import fun.commons.template4j.site.service.TemplateService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/api/try")
@RequiredArgsConstructor
public class TryController {

    private final TemplateService templateService;

    @PostMapping
    public ApiResponse<TryResponse> tryRender(@RequestBody TryRequest request, HttpServletRequest httpReq) {
        String traceId = (String) httpReq.getAttribute(TraceIdFilter.TRACE_ID_KEY);

        var renderRequest = new RenderRequest(request.templateType(), request.template(), request.data());
        var renderResponse = templateService.render(renderRequest);

        boolean withTrace = request.options() != null && request.options().withTrace();
        List<String> trace = withTrace ? templateService.getTrace() : Collections.emptyList();

        var response = new TryResponse(renderResponse.rendered(), renderResponse.timingMs(), trace);
        return ApiResponse.success(response, traceId);
    }
}
