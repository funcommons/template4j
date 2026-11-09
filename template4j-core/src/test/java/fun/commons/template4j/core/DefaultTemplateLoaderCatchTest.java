package fun.commons.template4j.core;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Covers DefaultTemplateLoader.getDefaultClassLoader() defensive paths:
 * - Line 32-33: catch(Throwable) when getContextClassLoader() throws
 * - Line 35: loader == null branch
 * - Line 37-41: getClassLoader() returns null → getSystemClassLoader() fallback
 *
 * Uses a custom Thread subclass that overrides getContextClassLoader() to throw.
 */
public class DefaultTemplateLoaderCatchTest {

    // Custom Thread that overrides getContextClassLoader() to throw
    private static class ThreadingContextClassLoaderThread extends Thread {
        private final boolean shouldThrow;
        private volatile Object result;
        private volatile Exception exception;

        ThreadingContextClassLoaderThread(boolean shouldThrow) {
            this.shouldThrow = shouldThrow;
        }

        @Override
        public ClassLoader getContextClassLoader() {
            if (shouldThrow) {
                throw new RuntimeException("simulated SecurityException");
            }
            return null; // Return null to trigger loader==null path
        }

        @Override
        public void run() {
            try {
                DefaultTemplateLoader loader = new DefaultTemplateLoader();
                result = loader.load("template4j-test.properties");
            } catch (Exception e) {
                exception = e;
            }
        }
    }

    // Line 32-33: catch(Throwable) — getContextClassLoader() throws
    @Test
    public void testGetDefaultClassLoaderCatchThrowable() throws Exception {
        ThreadingContextClassLoaderThread t = new ThreadingContextClassLoaderThread(true);
        t.start();
        t.join(5000);
        // The load should succeed because getDefaultClassLoader() catches the exception
        // and falls back to getClassLoader()
        if (t.exception != null) {
            // If it fails, it's because the fallback also couldn't find the resource
            // But normally DefaultTemplateLoader.class.getClassLoader() CAN find it
            throw t.exception;
        }
        Assertions.assertNotNull(t.result);
    }

    // Line 35: loader == null — getContextClassLoader() returns null
    @Test
    public void testGetDefaultClassLoaderNullResult() throws Exception {
        ThreadingContextClassLoaderThread t = new ThreadingContextClassLoaderThread(false);
        t.start();
        t.join(5000);
        if (t.exception != null) {
            throw t.exception;
        }
        Assertions.assertNotNull(t.result);
    }
}
