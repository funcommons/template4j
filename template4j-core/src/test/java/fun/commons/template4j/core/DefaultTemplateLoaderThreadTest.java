package fun.commons.template4j.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

/**
 * 通过 Thread.setContextClassLoader 强制触发 DefaultTemplateLoader.getDefaultClassLoader 的 fallback 分支。
 */
public class DefaultTemplateLoaderThreadTest {

    private ClassLoader originalCL;

    @BeforeEach
    public void saveContext() {
        originalCL = Thread.currentThread().getContextClassLoader();
    }

    @AfterEach
    public void restoreContext() {
        Thread.currentThread().setContextClassLoader(originalCL);
    }

    @Test
    public void testGetDefaultClassLoaderViaReflection() throws Exception {
        // 通过反射调用 getDefaultClassLoader
        Method m = DefaultTemplateLoader.class.getDeclaredMethod("getDefaultClassLoader");
        m.setAccessible(true);
        ClassLoader loader = (ClassLoader) m.invoke(null);
        Assertions.assertNotNull(loader);
    }

    @Test
    public void testGetDefaultClassLoaderWithExplicitNullContext() throws Exception {
        // 把当前线程 contextClassLoader 设为 null — 触发 class.getClassLoader() fallback
        Thread.currentThread().setContextClassLoader(null);
        Method m = DefaultTemplateLoader.class.getDeclaredMethod("getDefaultClassLoader");
        m.setAccessible(true);
        ClassLoader loader = (ClassLoader) m.invoke(null);
        Assertions.assertNotNull(loader);
    }

    @Test
    public void testGetDefaultClassLoaderWithSelfClassLoader() throws Exception {
        // 模拟 context = null, class.getClassLoader() = null (极端情况)
        Thread.currentThread().setContextClassLoader(null);
        // 此时会走 fallback chain 中的 class.getClassLoader() 调用
        Method m = DefaultTemplateLoader.class.getDeclaredMethod("getDefaultClassLoader");
        m.setAccessible(true);
        ClassLoader loader = (ClassLoader) m.invoke(null);
        // 在测试环境 class.getClassLoader() 不为 null，所以走这条路径
        Assertions.assertNotNull(loader);
    }
}
