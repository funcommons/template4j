package fun.commons.template4j.core;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;

/**
 * Covers private constructors and remaining branches via reflection.
 */
public class ReflectionCoverageTest {

    @Test
    public void testTemplateUtilPrivateConstructor() throws Exception {
        Constructor<TemplateUtil> c = TemplateUtil.class.getDeclaredConstructor();
        c.setAccessible(true);
        TemplateUtil instance = c.newInstance();
        Assertions.assertNotNull(instance);
    }

    @Test
    public void testLoaderUtilPrivateConstructor() throws Exception {
        Constructor<LoaderUtil> c = LoaderUtil.class.getDeclaredConstructor();
        c.setAccessible(true);
        LoaderUtil instance = c.newInstance();
        Assertions.assertNotNull(instance);
    }

    @Test
    public void testScriptUtilPrivateConstructor() throws Exception {
        Constructor<ScriptUtil> c = ScriptUtil.class.getDeclaredConstructor();
        c.setAccessible(true);
        ScriptUtil instance = c.newInstance();
        Assertions.assertNotNull(instance);
    }
}
