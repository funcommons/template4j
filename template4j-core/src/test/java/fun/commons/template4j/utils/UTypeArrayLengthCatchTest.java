package fun.commons.template4j.utils;

import fun.commons.template4j.utils.UType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Covers UType.getArrayLength() and the catch block in testBoolean().
 */
public class UTypeArrayLengthCatchTest {

    @Test
    public void testGetArrayLengthNormalArray() throws Exception {
        int length = UType.getArrayLength(new int[]{1, 2, 3});
        Assertions.assertEquals(3, length);
    }

    @Test
    public void testGetArrayLengthEmptyArray() throws Exception {
        int length = UType.getArrayLength(new int[]{});
        Assertions.assertEquals(0, length);
    }

    @Test
    public void testTestBooleanWithNormalArray() {
        Assertions.assertTrue(UType.testBoolean(new int[]{1}));
        Assertions.assertFalse(UType.testBoolean(new int[]{}));
    }

    // Cover the catch block by mocking UType.getArrayLength to throw
    @Test
    public void testTestBooleanCatchBlockViaMockedGetArrayLength() {
        try (org.mockito.MockedStatic<UType> utypeMock =
                org.mockito.Mockito.mockStatic(UType.class, org.mockito.Mockito.CALLS_REAL_METHODS)) {
            // Make getArrayLength throw for any call
            utypeMock.when(() -> UType.getArrayLength(org.mockito.ArgumentMatchers.any()))
                    .thenThrow(new IllegalArgumentException("simulated"));

            boolean result = UType.testBoolean(new int[]{1, 2, 3});
            // The catch block sets test = false
            Assertions.assertFalse(result);
        }
    }

    @Test
    public void testTestBooleanWithNullArray() {
        Assertions.assertFalse(UType.testBoolean(null));
    }
}
