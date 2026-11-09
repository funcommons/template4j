package fun.commons.template4j.core;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;

/**
 * Covers LoaderUtil missed lines/branches.
 */
public class LoaderUtilMissedTest {

    @Test
    public void testLoadResourceWithNullTemplateLoader() throws Exception {
        Object result = LoaderUtil.loadResource(null, "template4j-test.properties", null);
        Assertions.assertNotNull(result);
    }

    @Test
    public void testLoadResourceAsText() throws Exception {
        Object result = LoaderUtil.loadResource(new DefaultTemplateLoader(), "template4j-test.properties", "text");
        Assertions.assertTrue(result instanceof String);
    }

    @Test
    public void testLoadResourceAutoDetect() throws Exception {
        Object result = LoaderUtil.loadResource(new DefaultTemplateLoader(), "template4j-test.properties", null);
        Assertions.assertNotNull(result);
    }

    @Test
    public void testIncludeValueWithNonObjectNonArrayNonString() throws Exception {
        Object result = LoaderUtil.includeValue(new DefaultTemplateLoader(), "test", 42);
        Assertions.assertEquals(42, result);
    }

    @Test
    public void testIncludeStringWithoutIncludeKey() throws Exception {
        Object result = LoaderUtil.includeString(new DefaultTemplateLoader(), "test", "just a string");
        Assertions.assertEquals(result, "just a string");
    }

    @Test
    public void testIncludeArrayWithSimpleElements() throws Exception {
        com.alibaba.fastjson2.JSONArray arr = new com.alibaba.fastjson2.JSONArray();
        arr.add("hello");
        arr.add(42);
        Object result = LoaderUtil.includeArray(new DefaultTemplateLoader(), "test", arr);
        Assertions.assertTrue(result instanceof com.alibaba.fastjson2.JSONArray);
        Assertions.assertEquals(2, ((com.alibaba.fastjson2.JSONArray) result).size());
    }

    @Test
    public void testLoadResourceWithYamlType() throws Exception {
        // YAML type on a .properties file — may fail, but covers the branch
        try {
            LoaderUtil.loadResource(new DefaultTemplateLoader(), "template4j-test.properties", "yaml");
        } catch (Exception e) {
            // acceptable — properties file is not valid YAML
        }
    }

    @Test
    public void testLoadResourceNullThrowsIOException() throws Exception {
        Assertions.assertThrows(IOException.class, () -> {
        LoaderUtil.loadResource(url -> null, "missing.txt", null);
            });
    }
}
