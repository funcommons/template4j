package fun.commons.template4j.core;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import fun.commons.template4j.core.beans.FuncFne;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.*;

/**
 * Covers the remaining missed branches in LoaderUtil and FuncFne.
 *
 * LoaderUtil missed branches:
 *   1. loadResource: fallback path where content is a JSON array string but URL has no
 *      recognized extension (UJson.isJsonObject returns false for arrays, so it falls
 *      through to the YAML-safe path, then to plain string).
 *   2. includeString: blank/empty string passed to includeString (isNotBlank guard).
 *   3. includeObject: &include value is neither String nor List (e.g. a Number),
 *      so the includes list stays empty and the for-loop is skipped.
 *
 * FuncFne missed branch:
 *   1. call(null): the values != null check on line 14 — calling with null varargs
 *      directly (Groovy fne() gives empty array, not null).
 */
public class LoaderUtilFuncFneBranchTest {

    // ========== LoaderUtil: loadResource fallback for JSON array with no extension ==========

    @Test
    public void testLoadResourceFallbackJsonArrayNoExtension() throws Exception {
        // Content is a JSON array string, URL has no recognized extension.
        // UJson.isJsonObject returns false for "[1,2,3]", so the fallback path
        // tries YAML-safe parse, which returns null for a JSON array, then
        // falls back to returning the raw string.
        TemplateLoader loader = url -> "[1, 2, 3]";
        Object result = LoaderUtil.loadResource(loader, "/noext", null);
        // The fallback: isJsonObject("[1,2,3]") is false, so it goes to
        // UJson.JSONFromYamlSafe which may return null for a bare JSON array,
        // then result == string.
        Assertions.assertNotNull(result);
    }

    // ========== LoaderUtil: includeString with blank string ==========

    @Test
    public void testIncludeStringBlank() throws Exception {
        // Blank string: isNotBlank returns false, so the method returns the blank string as-is
        Object result = LoaderUtil.includeString(new DefaultTemplateLoader(), "test", "");
        Assertions.assertEquals(result, "");
    }

    @Test
    public void testIncludeStringWhitespaceOnly() throws Exception {
        // Whitespace-only string: isNotBlank returns false
        Object result = LoaderUtil.includeString(new DefaultTemplateLoader(), "test", "   ");
        Assertions.assertEquals(result, "   ");
    }

    // ========== LoaderUtil: includeObject with &include value that is neither String nor List ==========

    @Test
    public void testIncludeObjectWithNumericIncludeValue() throws Exception {
        // &include value is a Number (neither String nor List), so includes stays empty
        JSONObject template = new JSONObject();
        template.put("&include", 42);
        template.put("other", "value");

        JSONObject result = LoaderUtil.includeObject(new DefaultTemplateLoader(), "/root.json", template);
        // The &include key is processed but no URLs are added to includes list,
        // so nothing is merged. The "other" key is processed normally.
        Assertions.assertEquals(result.getString("other"), "value");
        Assertions.assertFalse(result.containsKey("&include"));
    }

    @Test
    public void testIncludeObjectWithBooleanIncludeValue() throws Exception {
        // &include value is a Boolean (neither String nor List)
        JSONObject template = new JSONObject();
        template.put("&include", true);
        template.put("key", "val");

        JSONObject result = LoaderUtil.includeObject(new DefaultTemplateLoader(), "/root.json", template);
        Assertions.assertEquals(result.getString("key"), "val");
        Assertions.assertFalse(result.containsKey("&include"));
    }

    @Test
    public void testIncludeObjectWithNullIncludeValue() throws Exception {
        // &include value is null (neither String nor List)
        JSONObject template = new JSONObject();
        template.put("&include", null);
        template.put("key", "val");

        JSONObject result = LoaderUtil.includeObject(new DefaultTemplateLoader(), "/root.json", template);
        Assertions.assertEquals(result.getString("key"), "val");
        Assertions.assertFalse(result.containsKey("&include"));
    }

    // ========== LoaderUtil: .jpage and .ypage extension detection ==========

    @Test
    public void testLoadResourceJpageExtension() throws Exception {
        TemplateLoader loader = url -> "{\"k\":1}";
        Object result = LoaderUtil.loadResource(loader, "/x.jpage", null);
        Assertions.assertTrue(result instanceof JSONObject);
        Assertions.assertEquals(1, ((JSONObject) result).getIntValue("k"));
    }

    @Test
    public void testLoadResourceYpageExtension() throws Exception {
        TemplateLoader loader = url -> "k: 1";
        Object result = LoaderUtil.loadResource(loader, "/x.ypage", null);
        Assertions.assertTrue(result instanceof JSONObject);
        Assertions.assertEquals(1, ((JSONObject) result).getIntValue("k"));
    }

    // ========== LoaderUtil: includeString with &include: prefix on blank-ish string ==========

    @Test
    public void testIncludeStringWithIncludePrefixButBlank() throws Exception {
        // A string that is blank but starts with &include: — isNotBlank guard prevents processing
        // This is technically impossible (a blank string can't start with "&include:"),
        // but we test the isNotBlank false branch directly.
        Object result = LoaderUtil.includeString(new DefaultTemplateLoader(), "test", "");
        Assertions.assertEquals(result, "");
    }

    // ========== LoaderUtil: includeString with valid &include: prefix ==========

    @Test
    public void testIncludeStringWithIncludePrefix() throws Exception {
        // String value "&include:./partial.json" should trigger the include logic
        TemplateLoader loader = url -> {
            if (url.endsWith("partial.json")) return "{\"included\": true}";
            return "should not be called for root";
        };
        Object result = LoaderUtil.includeString(loader, "/root.json", "&include:./partial.json");
        Assertions.assertTrue(result instanceof JSONObject);
        Assertions.assertTrue(((JSONObject) result).getBooleanValue("included"));
    }

    // ========== LoaderUtil: loadResource with null templateLoader creates DefaultTemplateLoader ==========

    @Test
    public void testLoadResourceNullLoaderCreatesDefault() throws Exception {
        // When templateLoader is null, a DefaultTemplateLoader is created.
        // Using a classpath resource that exists.
        Object result = LoaderUtil.loadResource(null, "template4j-test.properties", "text");
        Assertions.assertNotNull(result);
        Assertions.assertTrue(result instanceof String);
    }

    // ========== LoaderUtil: loadResource throws IOException when content is null ==========

    @Test
    public void testLoadResourceThrowsIOExceptionWhenNull() throws Exception {
        Assertions.assertThrows(IOException.class, () -> {
        TemplateLoader loader = url -> null;
        LoaderUtil.loadResource(loader, "/missing.txt", null);
            });
    }

    // ========== FuncFne: call(null) — the values != null branch ==========

    @Test
    public void testFuncFneCallWithNullVarargs() {
        FuncFne fne = new FuncFne();
        // Calling with null varargs (not empty array) — tests the values != null check
        Object result = fne.call((Object[]) null);
        Assertions.assertNull(result);
    }

    // ========== FuncFne: call with empty array ==========

    @Test
    public void testFuncFneCallWithEmptyArray() {
        FuncFne fne = new FuncFne();
        Object result = fne.call();
        Assertions.assertNull(result);
    }

    // ========== FuncFne: else branch — non-CharSequence, non-Collection, non-Map, non-null ==========

    @Test
    public void testFuncFneCallWithIntegerValue() {
        FuncFne fne = new FuncFne();
        // Integer is not CharSequence, Collection, or Map — hits the else branch
        Object result = fne.call(null, 42);
        Assertions.assertEquals(42, result);
    }

    @Test
    public void testFuncFneCallWithDoubleValue() {
        FuncFne fne = new FuncFne();
        Object result = fne.call(null, 3.14);
        Assertions.assertEquals(3.14, result);
    }

    @Test
    public void testFuncFneCallWithBooleanValue() {
        FuncFne fne = new FuncFne();
        // Boolean is not CharSequence, Collection, or Map
        Object result = fne.call(null, false);
        Assertions.assertEquals(false, result);
    }

    // ========== FuncFne: all null values returns null ==========

    @Test
    public void testFuncFneCallAllNulls() {
        FuncFne fne = new FuncFne();
        Object result = fne.call(null, null, null);
        Assertions.assertNull(result);
    }

    // ========== FuncFne: CharSequence branch — blank CharSequence skipped ==========

    @Test
    public void testFuncFneCallBlankCharSequenceSkipped() {
        FuncFne fne = new FuncFne();
        // StringBuilder is a CharSequence; empty StringBuilder is blank
        Object result = fne.call(new StringBuilder(), "found");
        Assertions.assertEquals(result, "found");
    }

    // ========== FuncFne: Collection branch — non-empty collection returned ==========

    @Test
    public void testFuncFneCallNonEmptyCollection() {
        FuncFne fne = new FuncFne();
        List<String> list = Arrays.asList("a", "b");
        Object result = fne.call(null, list);
        Assertions.assertSame(list, result);
    }

    @Test
    public void testFuncFneCallEmptyCollectionSkipped() {
        FuncFne fne = new FuncFne();
        Object result = fne.call(Collections.emptyList(), "fallback");
        Assertions.assertEquals(result, "fallback");
    }

    // ========== FuncFne: Map branch — non-empty map returned ==========

    @Test
    public void testFuncFneCallNonEmptyMap() {
        FuncFne fne = new FuncFne();
        Map<String, Object> map = new HashMap<>();
        map.put("key", "value");
        Object result = fne.call(null, map);
        Assertions.assertSame(map, result);
    }

    @Test
    public void testFuncFneCallEmptyMapSkipped() {
        FuncFne fne = new FuncFne();
        Object result = fne.call(Collections.emptyMap(), "fallback");
        Assertions.assertEquals(result, "fallback");
    }

    // ========== FuncFne: first non-empty wins ==========

    @Test
    public void testFuncFneCallFirstNonEmptyWins() {
        FuncFne fne = new FuncFne();
        Object result = fne.call(null, "", Collections.emptyList(), "first");
        Assertions.assertEquals(result, "first");
    }

    // ========== LoaderUtil: JSON array detection with unrecognized extension ==========
    // Covers the isJsonArray branch after the bugfix: isJsonObject() || isJsonArray()
    // When content is [1,2,3] and URL has .data extension, isJsonObject returns false
    // but isJsonArray returns true, so it goes through JSON.parse

    @Test
    public void testLoadResourceJsonArrayWithUnrecognizedExtension() throws Exception {
        Object result = RenderEngine.build().renderUrl("template4j-array-test.data", null, Map.of());
        Assertions.assertTrue(result instanceof JSONArray);
        JSONArray arr = (JSONArray) result;
        Assertions.assertEquals(3, arr.size());
    }
}
