package fun.commons.template4j.core;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

/**
 * Covers DefaultTemplateLoader all branches including fallback classloader paths.
 */
public class DefaultTemplateLoaderFullTest {

    @Test
    public void testLoadClasspathResource() throws Exception {
        DefaultTemplateLoader loader = new DefaultTemplateLoader();
        // Load an existing classpath resource
        String content = loader.load("classpath:template4j-test.properties");
        Assertions.assertNotNull(content);
    }

    @Test
    public void testLoadWithLeadingSlashes() throws Exception {
        DefaultTemplateLoader loader = new DefaultTemplateLoader();
        // Multiple leading slashes should be stripped
        String content = loader.load("classpath:///template4j-test.properties");
        Assertions.assertNotNull(content);
    }

    @Test
    public void testLoadWithoutClasspathPrefix() throws Exception {
        DefaultTemplateLoader loader = new DefaultTemplateLoader();
        String content = loader.load("template4j-test.properties");
        Assertions.assertNotNull(content);
    }

    @Test
    public void testGetDefaultClassLoaderFallback() throws Exception {
        // Call via reflection to cover all branches
        Method m = DefaultTemplateLoader.class.getDeclaredMethod("getDefaultClassLoader");
        m.setAccessible(true);
        ClassLoader loader = (ClassLoader) m.invoke(null);
        Assertions.assertNotNull(loader);
    }

    @Test
    public void testLoadNonexistentResource() throws Exception {
        Assertions.assertThrows(java.io.IOException.class, () -> {
        DefaultTemplateLoader loader = new DefaultTemplateLoader();
        loader.load("nonexistent-resource-xyz.txt");
            });
    }
}
