package fun.commons.template4j.utils;

import fun.commons.template4j.utils.UString;
import fun.commons.template4j.utils.UJson;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;

/**
 * Covers private constructors for utility classes.
 */
public class UtilPrivateConstructorTest {

    @Test
    public void testUStringPrivateConstructor() throws Exception {
        Constructor<UString> c = UString.class.getDeclaredConstructor();
        c.setAccessible(true);
        UString instance = c.newInstance();
        Assertions.assertNotNull(instance);
    }

    @Test
    public void testUJsonPrivateConstructor() throws Exception {
        Constructor<UJson> c = UJson.class.getDeclaredConstructor();
        c.setAccessible(true);
        UJson instance = c.newInstance();
        Assertions.assertNotNull(instance);
    }
}
