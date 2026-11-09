package fun.commons.template4j.site.controller;

import fun.commons.template4j.site.common.ApiResponse;
import fun.commons.template4j.site.config.TraceIdFilter;
import fun.commons.template4j.site.dto.RenderRequest;
import fun.commons.template4j.site.dto.RenderResponse;
import fun.commons.template4j.site.service.TemplateService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/render")
@RequiredArgsConstructor
public class RenderController {

    private final TemplateService templateService;

    @PostMapping
    public ApiResponse<RenderResponse> render(@RequestBody RenderRequest request, HttpServletRequest httpReq) {
        String traceId = (String) httpReq.getAttribute(TraceIdFilter.TRACE_ID_KEY);
        RenderResponse result = templateService.render(request);
        return ApiResponse.success(result, traceId);
    }
}
