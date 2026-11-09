package fun.commons.template4j.core;


import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class DefaultTemplateLoader implements TemplateLoader
{

    @Override
    public String load(String url) throws IOException {

        if (url.startsWith("classpath:")) {
            url = url.substring("classpath:".length());
        }


        while (url.startsWith("/")) {
            url = url.substring(1);
        }

        return IOUtils.resourceToString(url, StandardCharsets.UTF_8,getDefaultClassLoader());

    }

    private static ClassLoader getDefaultClassLoader() {
        ClassLoader loader = null;
        try {
            loader = Thread.currentThread().getContextClassLoader();
        } catch (Throwable e) {
        }

        if (loader == null) {
            loader = getClassLoader(DefaultTemplateLoader.class);
            if (loader == null) {
                try {
                    loader = getSystemClassLoader();
                } catch (Throwable e) {
                }
            }
        }
        return loader;
    }

    public static ClassLoader getClassLoader(Class<?> clazz) {
        return clazz.getClassLoader();
    }

    static ClassLoader getSystemClassLoader() {
        return ClassLoader.getSystemClassLoader();
    }
}
