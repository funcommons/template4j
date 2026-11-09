package fun.commons.template4j.site.service;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

/**
 * 覆盖 ExampleService.parseWebDoc 各分支:
 * - json-renderer with doc=false
 * - non-typed / other-type sections
 * - json-renderer with template-only / data-only
 * - empty json-renderer variables list
 * - rawSections empty (sections.isEmpty branch)
 */
class ExampleServiceParseWebDocTest {

    private final ExampleService service = new ExampleService();

    private Object invokeParseWebDoc(String path) throws Exception {
        Method m = ExampleService.class.getDeclaredMethod("parseWebDoc", String.class);
        m.setAccessible(true);
        return m.invoke(service, path);
    }

    @Test
    void parseWebDocWithDocFalse() throws Exception {
        // doc=false 时, 不提取 template/data/expected, 但仍构造 sections
        Object r = invokeParseWebDoc("docs/documents/coverage-doc-false.yml");
        Assertions.assertNotNull(r);
    }

    @Test
    void parseWebDocWithOtherType() throws Exception {
        // 非 markdown/json-renderer 类型 → else 分支保留 raw content
        Object r = invokeParseWebDoc("docs/documents/coverage-other-type.yml");
        Assertions.assertNotNull(r);
    }

    @Test
    void parseWebDocWithTemplateOnlyVariable() throws Exception {
        // 只有 template 变量, 没 data → data=null → wrapped fallback
        Object r = invokeParseWebDoc("docs/documents/coverage-template-only.yml");
        Assertions.assertNotNull(r);
    }

    @Test
    void parseWebDocWithDataOnlyVariable() throws Exception {
        // 只有 data 变量, 没 template → template=null → wrapped fallback
        Object r = invokeParseWebDoc("docs/documents/coverage-data-only.yml");
        Assertions.assertNotNull(r);
    }

    @Test
    void parseWebDocWithEmptyVariables() throws Exception {
        // variables 为空 → template/data 都不提取 → fallback (template==null && data==null)
        Object r = invokeParseWebDoc("docs/documents/coverage-no-vars.yml");
        Assertions.assertNotNull(r);
    }

    @Test
    void parseWebDocWithEmptySections() throws Exception {
        // sections 为空 → fallback
        Object r = invokeParseWebDoc("docs/documents/coverage-empty.yml");
        Assertions.assertNotNull(r);
    }
}