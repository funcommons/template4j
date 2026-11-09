package fun.commons.template4j.demo.exception;

import fun.commons.template4j.demo.dto.ApiError;
import fun.commons.template4j.demo.common.ApiResponse;
import fun.commons.template4j.demo.common.ErrorCode;
import fun.commons.template4j.demo.config.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<?> handleBizException(BizException e, HttpServletRequest request) {
        log.warn("BizException: code={}, message={}", e.getCode(), e.getMessage());
        ApiError error = new ApiError("business_error", e.getMessage(), request.getRequestURI());
        String traceId = (String) request.getAttribute(TraceIdFilter.TRACE_ID_KEY);
        return ApiResponse.error(e.getCode(), e.getMessage(), error, traceId);
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<?> handleException(Exception e, HttpServletRequest request) {
        log.error("Unhandled exception", e);
        String detail = e instanceof NullPointerException ? "Internal error" : e.getMessage();
        ApiError error = new ApiError("internal_error", detail, request.getRequestURI());
        String traceId = (String) request.getAttribute(TraceIdFilter.TRACE_ID_KEY);
        return ApiResponse.error(ErrorCode.INTERNAL_ERROR, "Internal server error", error, traceId);
    }
}
