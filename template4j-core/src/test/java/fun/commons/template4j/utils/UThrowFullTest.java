package fun.commons.template4j.utils;

import fun.commons.template4j.utils.UThrow;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;

/**
 * Covers UThrow including private constructor.
 */
public class UThrowFullTest {

    @Test
    public void testAssertArgumentTrue() throws Exception {
        UThrow.assertArgument(true, "should not throw");
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

    @Test
    public void testPrivateConstructor() throws Exception {
        // Cover the private constructor via reflection
        Constructor<UThrow> c = UThrow.class.getDeclaredConstructor();
        c.setAccessible(true);
        UThrow instance = c.newInstance();
        Assertions.assertNotNull(instance);
    }
}
