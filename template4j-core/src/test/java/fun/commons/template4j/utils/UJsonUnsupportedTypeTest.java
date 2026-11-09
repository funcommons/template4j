package fun.commons.template4j.utils;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * 覆盖 UJson.smartCast 不支持的类型。
 */
public class UJsonUnsupportedTypeTest {

    @Test
    public void testSmartCastUnsupportedType() {
        try {
            fun.commons.template4j.utils.UJson.smartCast("x", "binary");
            Assertions.fail("binary should throw IllegalArgumentException");
        } catch (Exception ex) {
            // expected
            Assertions.assertTrue(ex.getMessage().contains("Unsupported"));
        }
    }
}
