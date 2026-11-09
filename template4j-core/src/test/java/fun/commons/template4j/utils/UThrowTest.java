package fun.commons.template4j.utils;

import fun.commons.template4j.utils.UThrow;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Covers UThrow.assertArgument.
 */
public class UThrowTest {

    @Test
    public void testAssertArgumentTrue() throws Exception {
        UThrow.assertArgument(true, "should not throw");
        // no exception
    }

    @Test
    public void testAssertArgumentFalse() {
        try {
            UThrow.assertArgument(false, "expected error");
            Assertions.fail("Should have thrown");
        } catch (Exception e) {
            Assertions.assertEquals(e.getMessage(), "expected error");
        }
    }
}
