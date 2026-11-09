package fun.commons.template4j.demo.common;

public class ErrorCode {
    private ErrorCode() {}

    public static final int SUCCESS = 0;
    public static final int TEMPLATE_RENDER_ERROR = 10300;
    public static final int TEMPLATE_PARSE_ERROR = 10301;
    public static final int TEMPLATE_TIMEOUT = 10302;
    public static final int TEMPLATE_SIZE_EXCEEDED = 10303;
    public static final int EXAMPLE_NOT_FOUND = 10404;
    public static final int INTERNAL_ERROR = 10500;
    public static final int VALIDATION_ERROR = 10400;
}
