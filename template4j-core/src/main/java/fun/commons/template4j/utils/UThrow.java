package fun.commons.template4j.utils;

public class UThrow {

    // 断言
    public static void assertArgument(boolean test, String message) {
        if (!test) {
            throw new IllegalArgumentException(message);
        }
    }

}
