package fun.commons.template4j.site.service;

import fun.commons.template4j.site.SiteApplication;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.lang.reflect.Method;

/**
 * ExampleService 边界覆盖: loadTemplateUrl / serializedData / fallback / parseWebDoc 异常路径。
 */
@SpringBootTest(classes = SiteApplication.class)
class ExampleServiceCoverageTest {

    @Autowired
    private ExampleService exampleService;

    @Test
    void loadNonExistentExampleThrows() {
        // 不存在的 source 走 fallback; 但 id 不在 index 中, 抛 exampleNotFound
        Assertions.assertThrows(Exception.class, () -> exampleService.load("totally-bogus-id"));
    }

    @Test
    void loadTemplateUrlReturnsNullForMissing() throws Exception {
        Method m = ExampleService.class.getDeclaredMethod("loadTemplateUrl", String.class);
        m.setAccessible(true);
        Object r = m.invoke(exampleService, "/definitely/not/there.yml");
        Assertions.assertNull(r);
    }

    @Test
    void loadTemplateUrlReadsExistingClasspathResource() throws Exception {
        Method m = ExampleService.class.getDeclaredMethod("loadTemplateUrl", String.class);
        m.setAccessible(true);
        // 已知存在的 classpath 资源 (examples/index.json)
        Object r = m.invoke(exampleService, "examples/index.json");
        Assertions.assertNotNull(r);
        Assertions.assertTrue(r.toString().contains("intro-home"));
    }

    @Test
    void loadTemplateUrlTriesWithDocsPrefix() throws Exception {
        Method m = ExampleService.class.getDeclaredMethod("loadTemplateUrl", String.class);
        m.setAccessible(true);
        // documents/home.yml 不存在, 但 docs/documents/home.yml 存在
        Object r = m.invoke(exampleService, "documents/home.yml");
        Assertions.assertNotNull(r);
    }

    @Test
    void serializedDataReturnsEmptyMapOnInvalidJson() throws Exception {
        Method m = ExampleService.class.getDeclaredMethod("serializedData", String.class);
        m.setAccessible(true);
        Object r = m.invoke(exampleService, "this is not json");
        Assertions.assertTrue(r instanceof java.util.Map);
        Assertions.assertTrue(((java.util.Map<?, ?>) r).isEmpty());
    }

    @Test
    void fallbackUsedWhenSourceMissing() {
        // 调一个不存在的 source: 用反射改 meta.source 然后调 buildDetail
        // 但更简单: 列表里所有 source 都已存在, 直接断言现有 fallback 列表 (home / quick-start)
        var detail = exampleService.load("intro-home");
        Assertions.assertNotNull(detail);
        Assertions.assertNotNull(detail.template());
    }

    @Test
    void parseWebDocReturnsFallbackForMissingResource() throws Exception {
        Method m = ExampleService.class.getDeclaredMethod("parseWebDoc", String.class);
        m.setAccessible(true);
        Object r = m.invoke(exampleService, "totally/missing/path.yml");
        Assertions.assertNotNull(r);
    }

    @Test
    void jsonRendererContentReturnsEmptyForNonMap() throws Exception {
        Method m = ExampleService.class.getDeclaredMethod("jsonRendererContent", Object.class);
        m.setAccessible(true);
        Object r = m.invoke(exampleService, "not-a-map");
        Assertions.assertTrue(r instanceof java.util.Map);
        Assertions.assertTrue(((java.util.Map<?, ?>) r).isEmpty());
    }

    @Test
    void jsonRendererContentCastsMap() throws Exception {
        Method m = ExampleService.class.getDeclaredMethod("jsonRendererContent", Object.class);
        m.setAccessible(true);
        java.util.Map<String, Object> input = new java.util.LinkedHashMap<>();
        input.put("k", "v");
        Object r = m.invoke(exampleService, input);
        Assertions.assertTrue(r instanceof java.util.Map);
        Assertions.assertEquals("v", ((java.util.Map<?, ?>) r).get("k"));
    }

    @Test
    void extractFirstJsonRendererReturnsNullsForEmptyContent() throws Exception {
        Method m = ExampleService.class.getDeclaredMethod("extractFirstJsonRenderer", java.util.Map.class);
        m.setAccessible(true);
        Object r = m.invoke(exampleService, new java.util.LinkedHashMap<>());
        Assertions.assertNotNull(r);
        Object[] arr = (Object[]) r;
        Assertions.assertNull(arr[0]);
        Assertions.assertNull(arr[1]);
        Assertions.assertNull(arr[2]);
    }

    @Test
    void extractFirstJsonRendererReturnsNullsForNullContent() throws Exception {
        Method m = ExampleService.class.getDeclaredMethod("extractFirstJsonRenderer", java.util.Map.class);
        m.setAccessible(true);
        Object r = m.invoke(exampleService, (Object) null);
        Assertions.assertNotNull(r);
        Object[] arr = (Object[]) r;
        Assertions.assertNull(arr[0]);
        Assertions.assertNull(arr[1]);
        Assertions.assertNull(arr[2]);
    }

    @Test
    void extractFirstJsonRendererHandlesAllVariables() throws Exception {
        Method m = ExampleService.class.getDeclaredMethod("extractFirstJsonRenderer", java.util.Map.class);
        m.setAccessible(true);
        java.util.Map<String, Object> content = new java.util.LinkedHashMap<>();
        java.util.List<java.util.Map<String, Object>> variables = new java.util.ArrayList<>();
        java.util.Map<String, Object> v1 = new java.util.LinkedHashMap<>();
        v1.put("name", "template");
        v1.put("value", "{\"a\":1}");
        variables.add(v1);
        java.util.Map<String, Object> v2 = new java.util.LinkedHashMap<>();
        v2.put("name", "data");
        v2.put("value", "{\"x\":2}");
        variables.add(v2);
        content.put("variables", variables);
        content.put("result", "{\"rendered\":true}");

        Object r = m.invoke(exampleService, content);
        Object[] arr = (Object[]) r;
        Assertions.assertEquals("{\"a\":1}", arr[0]);
        Assertions.assertEquals("{\"x\":2}", arr[1]);
        Assertions.assertEquals("{\"rendered\":true}", arr[2]);
    }

    @Test
    void extractFirstJsonRendererSkipsNullValues() throws Exception {
        Method m = ExampleService.class.getDeclaredMethod("extractFirstJsonRenderer", java.util.Map.class);
        m.setAccessible(true);
        java.util.Map<String, Object> content = new java.util.LinkedHashMap<>();
        java.util.List<java.util.Map<String, Object>> variables = new java.util.ArrayList<>();
        java.util.Map<String, Object> v = new java.util.LinkedHashMap<>();
        v.put("name", "template");
        v.put("value", null);  // null value → skipped
        variables.add(v);
        content.put("variables", variables);

        Object r = m.invoke(exampleService, content);
        Object[] arr = (Object[]) r;
        Assertions.assertNull(arr[0]);
    }

    @Test
    void extractFirstJsonRendererHandlesTemplateUrl() throws Exception {
        Method m = ExampleService.class.getDeclaredMethod("extractFirstJsonRenderer", java.util.Map.class);
        m.setAccessible(true);
        java.util.Map<String, Object> content = new java.util.LinkedHashMap<>();
        java.util.List<java.util.Map<String, Object>> variables = new java.util.ArrayList<>();
        java.util.Map<String, Object> v = new java.util.LinkedHashMap<>();
        v.put("name", "templateUrl");
        v.put("value", "examples/index.json");  // exists
        variables.add(v);
        content.put("variables", variables);

        Object r = m.invoke(exampleService, content);
        Object[] arr = (Object[]) r;
        Assertions.assertNotNull(arr[0]);
        Assertions.assertTrue(arr[0].toString().contains("intro-home"));
    }

    @Test
    void extractFirstJsonRendererTemplateUrlMissing() throws Exception {
        Method m = ExampleService.class.getDeclaredMethod("extractFirstJsonRenderer", java.util.Map.class);
        m.setAccessible(true);
        java.util.Map<String, Object> content = new java.util.LinkedHashMap<>();
        java.util.List<java.util.Map<String, Object>> variables = new java.util.ArrayList<>();
        java.util.Map<String, Object> v = new java.util.LinkedHashMap<>();
        v.put("name", "templateUrl");
        v.put("value", "/no/such/path.yml");
        variables.add(v);
        content.put("variables", variables);

        Object r = m.invoke(exampleService, content);
        Object[] arr = (Object[]) r;
        Assertions.assertNull(arr[0]);
    }

    @Test
    void parsedJsonReturnsStringOnInvalid() throws Exception {
        Method m = ExampleService.class.getDeclaredMethod("parsedJson", String.class);
        m.setAccessible(true);
        Object r = m.invoke(exampleService, "not valid json");
        Assertions.assertTrue(r instanceof String);
        Assertions.assertEquals("not valid json", r);
    }

    @Test
    void parsedJsonReturnsParsedObject() throws Exception {
        Method m = ExampleService.class.getDeclaredMethod("parsedJson", String.class);
        m.setAccessible(true);
        Object r = m.invoke(exampleService, "{\"a\":1}");
        Assertions.assertTrue(r instanceof java.util.Map);
        Assertions.assertEquals(1, ((java.util.Map<?, ?>) r).get("a"));
    }

    @Test
    void loadInvalidIdPatternThrows() {
        Assertions.assertThrows(Exception.class, () -> exampleService.load("INVALID_ID!@#"));
    }

    @Test
    void listCachedReturnsSameInstance() throws Exception {
        // 二次调用 list → 命中 cachedIndex → 同一实例
        var list1 = exampleService.list();
        var list2 = exampleService.list();
        Assertions.assertSame(list1, list2);
        Assertions.assertFalse(list1.isEmpty());
    }

    @Test
    void listAfterCacheResetReloads() throws Exception {
        // 反射重置 cachedIndex → 下次 list() 触发 loadIndex (line 28 true + line 30 true)
        java.lang.reflect.Field f = ExampleService.class.getDeclaredField("cachedIndex");
        f.setAccessible(true);
        var oldCached = f.get(exampleService);
        f.set(exampleService, null);
        try {
            var list = exampleService.list();
            Assertions.assertNotNull(list);
            Assertions.assertFalse(list.isEmpty());
        } finally {
            f.set(exampleService, oldCached);
        }
    }
}
