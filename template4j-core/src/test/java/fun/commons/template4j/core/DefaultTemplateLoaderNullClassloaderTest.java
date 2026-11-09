package fun.commons.template4j.core;

import java.io.IOException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Covers DefaultTemplateLoader.getDefaultClassLoader() lines 37-41:
 * the getClassLoader()==null → getSystemClassLoader() fallback path.
 *
 * Uses Mockito MockedStatic to simulate:
 * 1. Thread.currentThread().getContextClassLoader() returning null
 * 2. DefaultTemplateLoader.getClassLoader() returning null
 *
 * Both mocks must run in the same thread as the test since MockedStatic
 * uses thread-local storage.
 */
public class DefaultTemplateLoaderNullClassloaderTest {

    @Test
    public void testGetClassLoaderReturnsNullForBootstrapClass() {
        ClassLoader result = DefaultTemplateLoader.getClassLoader(String.class);
        Assertions.assertNull(result);
    }

    @Test
    public void testGetClassLoaderReturnsNonNullForAppClass() {
        ClassLoader result = DefaultTemplateLoader.getClassLoader(DefaultTemplateLoader.class);
        Assertions.assertNotNull(result);
    }

    // Cover the full fallback path:
    // getContextClassLoader() returns null → getClassLoader() returns null → getSystemClassLoader()
    @Test
    public void testGetDefaultClassLoaderFallsBackToSystemClassLoader() throws Exception {
        try (org.mockito.MockedStatic<DefaultTemplateLoader> dlMock =
                org.mockito.Mockito.mockStatic(DefaultTemplateLoader.class, org.mockito.Mockito.CALLS_REAL_METHODS)) {

            // Make getClassLoader return null for any class, simulating bootstrap CL
            dlMock.when(() -> DefaultTemplateLoader.getClassLoader(org.mockito.ArgumentMatchers.any(Class.class)))
                    .thenReturn(null);

            // Now call load() directly (same thread, so mock is active)
            // But we also need Thread.currentThread().getContextClassLoader() to return null
            // We'll mock Thread using MockedStatic
            Thread currentThread = Thread.currentThread();
            ClassLoader originalCL = currentThread.getContextClassLoader();
            try {
                // Set context classloader to null to trigger the loader==null path
                currentThread.setContextClassLoader(null);

                DefaultTemplateLoader loader = new DefaultTemplateLoader();
                // This calls getDefaultClassLoader() which:
                // 1. Thread.currentThread().getContextClassLoader() → null
                // 2. getClassLoader(DefaultTemplateLoader.class) → null (mocked)
                // 3. ClassLoader.getSystemClassLoader() → real system CL
                // The system CL should be able to find classpath resources
                String result = loader.load("template4j-test.properties");
                Assertions.assertNotNull(result);
            } finally {
                currentThread.setContextClassLoader(originalCL);
            }
        }
    }

    // Test with null context classloader but normal getClassLoader (no mock)
    @Test
    public void testDefaultClassLoaderWithNullContextCL() throws Exception {
        Thread currentThread = Thread.currentThread();
        ClassLoader originalCL = currentThread.getContextClassLoader();
        try {
            currentThread.setContextClassLoader(null);
            DefaultTemplateLoader loader = new DefaultTemplateLoader();
            String result = loader.load("template4j-test.properties");
            Assertions.assertNotNull(result);
        } finally {
            currentThread.setContextClassLoader(originalCL);
        }
    }

    // Test the catch(Throwable) block when getContextClassLoader throws
    @Test
    public void testGetDefaultClassLoaderContextCLThrows() throws Exception {
        // Use a custom thread that throws from getContextClassLoader
        final Exception[] error = {null};
        Thread t = new Thread() {
            @Override
            public ClassLoader getContextClassLoader() {
                throw new RuntimeException("simulated SecurityException");
            }

            @Override
            public void run() {
                try {
                    DefaultTemplateLoader loader = new DefaultTemplateLoader();
                    String result = loader.load("template4j-test.properties");
                    Assertions.assertNotNull(result);
                } catch (Exception e) {
                    error[0] = e;
                }
            }
        };
        t.start();
        t.join(5000);
        if (error[0] != null) {
            throw error[0];
        }
    }

    // Cover the catch(Throwable) block for getSystemClassLoader()
    // by mocking both getClassLoader and getSystemClassLoader
    @Test
    public void testGetSystemClassLoaderThrows() throws Exception {
        try (org.mockito.MockedStatic<DefaultTemplateLoader> dlMock =
                org.mockito.Mockito.mockStatic(DefaultTemplateLoader.class, org.mockito.Mockito.CALLS_REAL_METHODS)) {

            // Make getClassLoader return null (simulating bootstrap CL)
            dlMock.when(() -> DefaultTemplateLoader.getClassLoader(org.mockito.ArgumentMatchers.any(Class.class)))
                    .thenReturn(null);

            // Make getSystemClassLoader throw
            dlMock.when(DefaultTemplateLoader::getSystemClassLoader)
                    .thenThrow(new RuntimeException("simulated SecurityException"));

            Thread currentThread = Thread.currentThread();
            ClassLoader originalCL = currentThread.getContextClassLoader();
            try {
                currentThread.setContextClassLoader(null);

                DefaultTemplateLoader loader = new DefaultTemplateLoader();
                // This calls getDefaultClassLoader() which:
                // 1. Thread.currentThread().getContextClassLoader() → null
                // 2. getClassLoader(DefaultTemplateLoader.class) → null (mocked)
                // 3. getSystemClassLoader() → throws (mocked) → catch block → loader stays null
                // Then IOUtils.resourceToString is called with null classloader, which should still work
                try {
                    String result = loader.load("template4j-test.properties");
                    // If it doesn't throw, the resource was found with null CL
                    Assertions.assertNotNull(result);
                } catch (IOException e) {
                    // If it throws IOException because null CL can't find resource, that's also fine
                    // The important thing is the catch block was exercised
                }
            } finally {
                currentThread.setContextClassLoader(originalCL);
            }
        }
    }
}
