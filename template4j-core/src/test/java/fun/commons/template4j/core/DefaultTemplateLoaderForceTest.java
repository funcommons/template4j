package fun.commons.template4j.core;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Force-covers DefaultTemplateLoader.getDefaultClassLoader() all branches
 * by temporarily setting context classloader to null.
 */
public class DefaultTemplateLoaderForceTest {

    @Test
    public void testGetDefaultClassLoaderWithNullContextLoader() throws Exception {
        // Save and replace context classloader
        ClassLoader originalLoader = Thread.currentThread().getContextClassLoader();
        try {
            // Set context classloader to null → forces fallback to class.getClassLoader()
            Thread.currentThread().setContextClassLoader(null);

            Method m = DefaultTemplateLoader.class.getDeclaredMethod("getDefaultClassLoader");
            m.setAccessible(true);
            ClassLoader loader = (ClassLoader) m.invoke(null);
            Assertions.assertNotNull(loader);
        } finally {
            Thread.currentThread().setContextClassLoader(originalLoader);
        }
    }

    @Test
    public void testGetDefaultClassLoaderWithNormalContextLoader() throws Exception {
        Method m = DefaultTemplateLoader.class.getDeclaredMethod("getDefaultClassLoader");
        m.setAccessible(true);
        ClassLoader loader = (ClassLoader) m.invoke(null);
        Assertions.assertNotNull(loader);
        Assertions.assertSame(Thread.currentThread().getContextClassLoader(), loader);
    }
}
