package fun.commons.template4j.site.dto;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * TryRequest + RenderRequest compact constructor 边界覆盖。
 */
class DtoRecordsCoverageTest {

    @Test
    void tryRequestNullTemplateTypeDefaultsToJson() {
        TryRequest req = new TryRequest(null, "{}", null, null);
        Assertions.assertEquals("json", req.templateType());
        Assertions.assertNotNull(req.options());
        Assertions.assertFalse(req.options().withTrace());
    }

    @Test
    void tryRequestEmptyTemplateTypeDefaultsToJson() {
        TryRequest req = new TryRequest("", "{}", null, null);
        Assertions.assertEquals("json", req.templateType());
    }

    @Test
    void tryRequestBlankTemplateTypePreservedAsIs() {
        // "   " 不是 isEmpty, 所以保留
        TryRequest req = new TryRequest("   ", "{}", null, null);
        Assertions.assertEquals("   ", req.templateType());
    }

    @Test
    void tryRequestNonNullOptionsPreserved() {
        TryRequest.Options opts = new TryRequest.Options(true);
        TryRequest req = new TryRequest("json", "{}", null, opts);
        Assertions.assertTrue(req.options().withTrace());
    }

    @Test
    void tryRequestOptionsWithTraceFalseByDefault() {
        TryRequest.Options opts = new TryRequest.Options(false);
        Assertions.assertFalse(opts.withTrace());
    }

    @Test
    void renderRequestNullTemplateTypeDefaultsToJson() {
        RenderRequest req = new RenderRequest(null, "{}", null);
        Assertions.assertEquals("json", req.templateType());
    }

    @Test
    void renderRequestEmptyTemplateTypeDefaultsToJson() {
        RenderRequest req = new RenderRequest("", "{}", null);
        Assertions.assertEquals("json", req.templateType());
    }

    @Test
    void renderRequestNormalizedDataNullReturnsEmptyMap() {
        RenderRequest req = new RenderRequest("json", "{}", null);
        Object normalized = req.normalizedData();
        Assertions.assertTrue(normalized instanceof java.util.Map);
        Assertions.assertTrue(((java.util.Map<?, ?>) normalized).isEmpty());
    }

    @Test
    void renderRequestNormalizedDataMapReturnsAsIs() {
        java.util.Map<String, Object> m = java.util.Map.of("k", "v");
        RenderRequest req = new RenderRequest("json", "{}", m);
        Assertions.assertSame(m, req.normalizedData());
    }

    @Test
    void renderRequestNormalizedDataIterableReturnsList() {
        Iterable<Integer> it = java.util.List.of(1, 2, 3);
        RenderRequest req = new RenderRequest("json", "{}", it);
        Object normalized = req.normalizedData();
        Assertions.assertTrue(normalized instanceof java.util.List);
        Assertions.assertEquals(3, ((java.util.List<?>) normalized).size());
    }

    @Test
    void renderRequestNormalizedDataScalarReturnsAsIs() {
        RenderRequest req = new RenderRequest("json", "{}", 42);
        Assertions.assertEquals(42, req.normalizedData());
    }
}
