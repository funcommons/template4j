package fun.commons.template4j.core;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

/**
 * Force covers all branches of DefaultTemplateLoader.getDefaultClassLoader.
 */
public class DefaultTemplateLoaderClassLoaderTest {

    @Test
    public void testGetDefaultClassLoaderDirectly() throws Exception {
        Method m = DefaultTemplateLoader.class.getDeclaredMethod("getDefaultClassLoader");
        m.setAccessible(true);
        ClassLoader loader = (ClassLoader) m.invoke(null);
        Assertions.assertNotNull(loader);
    }

    @Test
    public void testLoadClasspathAndSlashPrefixes() throws Exception {
        DefaultTemplateLoader loader = new DefaultTemplateLoader();
        // Test classpath: prefix
        String s1 = loader.load("classpath:template4j-test.properties");
        Assertions.assertNotNull(s1);

        // Test with multiple slashes
        String s2 = loader.load("classpath:///template4j-test.properties");
        Assertions.assertNotNull(s2);

        // Test with leading slash but no classpath: prefix
        String s3 = loader.load("/template4j-test.properties");
        Assertions.assertNotNull(s3);

        // Test with no prefix at all
        String s4 = loader.load("template4j-test.properties");
        Assertions.assertNotNull(s4);
    }
}
