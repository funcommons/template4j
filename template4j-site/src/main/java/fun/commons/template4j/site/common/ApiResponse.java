package fun.commons.template4j.site.common;

import fun.commons.template4j.site.dto.ApiError;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {
    private int code;
    private String message;
    private T data;
    private String traceId;
    private ApiError error;

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(ErrorCode.SUCCESS, "success", data, null, null);
    }

    public static <T> ApiResponse<T> success(T data, String traceId) {
        return new ApiResponse<>(ErrorCode.SUCCESS, "success", data, traceId, null);
    }

    public static <T> ApiResponse<T> error(int code, String message, ApiError error) {
        return new ApiResponse<>(code, message, null, null, error);
    }

    public static <T> ApiResponse<T> error(int code, String message, ApiError error, String traceId) {
        return new ApiResponse<>(code, message, null, traceId, error);
    }
}
