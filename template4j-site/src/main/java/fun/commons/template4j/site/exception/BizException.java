package fun.commons.template4j.site.exception;

import fun.commons.template4j.site.common.ErrorCode;
import lombok.Getter;

@Getter
public class BizException extends RuntimeException {
    private final int code;
    private final String error;

    public BizException(int code, String message) {
        super(message);
        this.code = code;
        this.error = null;
    }

    public BizException(int code, String message, String error) {
        super(message);
        this.code = code;
        this.error = error;
    }

    public static BizException renderError(String message) {
        return new BizException(ErrorCode.TEMPLATE_RENDER_ERROR, message);
    }

    public static BizException parseError(String message) {
        return new BizException(ErrorCode.TEMPLATE_PARSE_ERROR, message);
    }

    public static BizException timeout(String message) {
        return new BizException(ErrorCode.TEMPLATE_TIMEOUT, message);
    }

    public static BizException sizeExceeded(String message) {
        return new BizException(ErrorCode.TEMPLATE_SIZE_EXCEEDED, message);
    }

    public static BizException exampleNotFound(String id) {
        return new BizException(ErrorCode.EXAMPLE_NOT_FOUND, "Example not found: " + id);
    }

    public static BizException badRequest(String message) {
        return new BizException(ErrorCode.VALIDATION_ERROR, message);
    }
}
