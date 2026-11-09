package fun.commons.template4j.core;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

/**
 * 通过反射调用 DefaultTemplateLoader 的私有 getDefaultClassLoader，覆盖各分支。
 */
public class DefaultTemplateLoaderCoverageTest {

    @Test
    public void testGetDefaultClassLoader() throws Exception {
        Method m = DefaultTemplateLoader.class.getDeclaredMethod("getDefaultClassLoader");
        m.setAccessible(true);
        ClassLoader loader = (ClassLoader) m.invoke(null);
        Assertions.assertNotNull(loader);
    }
}
