package fun.commons.template4j.core;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * 补 DefaultTemplateLoader 异常路径 + 各种 LoaderUtil.format 推断。
 */
public class LoaderMoreTest {

    private final RenderEngine engine = RenderEngine.build();

    @Test
    public void testLoaderUtilJsonExtension() throws Exception {
        // .json 后缀
        Object result = LoaderUtil.loadResource(
                (TemplateLoader) url -> "{\"k\":1}", "/x.json", null);
        Assertions.assertNotNull(result);
    }

    @Test
    public void testLoaderUtilJson5Extension() throws Exception {
        Object result = LoaderUtil.loadResource(
                (TemplateLoader) url -> "{\"k\":1}", "/x.json5", null);
        Assertions.assertNotNull(result);
    }

    @Test
    public void testLoaderUtilJson5ObjectExtension() throws Exception {
        Object result = LoaderUtil.loadResource(
                (TemplateLoader) url -> "{\"k\":1}", "/x.japi", null);
        Assertions.assertNotNull(result);
    }

    @Test
    public void testLoaderUtilYamlExtension() throws Exception {
        Object result = LoaderUtil.loadResource(
                (TemplateLoader) url -> "k: 1", "/x.yml", null);
        Assertions.assertNotNull(result);
    }

    @Test
    public void testLoaderUtilYamlOtherExtensions() throws Exception {
        Assertions.assertNotNull(LoaderUtil.loadResource(
                (TemplateLoader) url -> "k: 1", "/x.yaml", null));
        Assertions.assertNotNull(LoaderUtil.loadResource(
                (TemplateLoader) url -> "k: 1", "/x.yt", null));
        Assertions.assertNotNull(LoaderUtil.loadResource(
                (TemplateLoader) url -> "k: 1", "/x.yapi", null));
    }

    @Test
    public void testLoaderUtilResourceTypeOverrides() throws Exception {
        // 显式指定 type 覆盖后缀推断
        Object result = LoaderUtil.loadResource(
                (TemplateLoader) url -> "{\"k\":1}", "/x.unknown", "json");
        Assertions.assertNotNull(result);
    }

    @Test
    public void testLoaderUtilLoadTemplateDelegates() throws Exception {
        // loadTemplate 走 loadResource + parseTemplate
        Object result = LoaderUtil.loadTemplate(
                (TemplateLoader) url -> "{\"k\":1}", "/x.json", null);
        Assertions.assertNotNull(result);
    }

    @Test
    public void testDefaultLoaderClasspathPrefix() throws Exception {
        // classpath: 前缀
        String content = new DefaultTemplateLoader().load("classpath:/org/junit/jupiter/api/Assertions.class");
        Assertions.assertNotNull(content);
    }

    @Test
    public void testDefaultLoaderNullClasspath() {
        // null path 应抛 NPE 或 IOException
        try {
            new DefaultTemplateLoader().load(null);
        Assertions.fail("expected Exception");
    } catch (Exception ex) {
            // expected
        }
    }

    @Test
    public void testLoadJson() throws Exception {
        // TemplateLoader.loadJson(url) - 触发 default JSON.parse
        Object result = ((TemplateLoader) url -> "{\"k\":1}").loadJson("/x.json");
        Assertions.assertNotNull(result);
    }

    @Test
    public void testLoadYaml() throws Exception {
        // TemplateLoader.loadYaml(url) - 触发 default UJson.JSONFromYaml
        Object result = ((TemplateLoader) url -> "k: 1\nv: hello").loadYaml("/x.yml");
        Assertions.assertNotNull(result);
    }
}
