package fun.commons.template4j.core;

import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 覆盖 {@link TemplateLoader} 接口实现与自定义 loader
 */
public class TemplateLoaderTest {

    @Test
    public void testCustomLoader() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        TemplateLoader loader = new TemplateLoader() {
            @Override
            public String load(String url) throws java.io.IOException {
                calls.incrementAndGet();
                if (url.startsWith("db://")) {
                    return "{\"from\":\"db\",\"name\":\"{@name}\"}";
                }
                return new DefaultTemplateLoader().load(url);
            }
        };
        RenderEngine e = RenderEngine.build().withLoader(loader);
        Object r = e.renderUrl("db://user/1", Map.of("name", "alice"));
        JSONObject jo = (JSONObject) r;
        Assertions.assertEquals(jo.getString("from"), "db");
        Assertions.assertEquals(jo.getString("name"), "alice");
        Assertions.assertEquals(1, calls.get());
    }

    @Test
    public void testCustomLoaderLoadJson() throws Exception {
        TemplateLoader loader = new TemplateLoader() {
            @Override public String load(String url) { return "raw"; }
            @Override public Object loadJson(String url) { return JSONObject.parseObject("{\"x\":1}"); }
        };
        Object r = loader.loadJson("anything");
        Assertions.assertTrue(r instanceof JSONObject);
        Assertions.assertEquals(1, ((JSONObject) r).getIntValue("x"));
    }

    @Test
    public void testCustomLoaderLoadYamlDefault() throws Exception {
        // 不重写 loadYaml，调用默认实现
        TemplateLoader loader = url -> "a: 1\nb: 2\n";
        Object r = loader.loadYaml("anything");
        Assertions.assertTrue(r instanceof JSONObject);
        Assertions.assertEquals(2, ((JSONObject) r).getIntValue("b"));
    }

    @Test
    public void testCustomLoaderErrorPath() throws Exception {
        TemplateLoader loader = url -> { throw new RuntimeException("not found: " + url); };
        RenderEngine e = RenderEngine.build().withLoader(loader);
        try {
            e.renderUrl("/x.json", Map.of());
            Assertions.fail("loader 抛错应向上冒泡");
        } catch (Exception ex) {
            Assertions.assertTrue(ex.getMessage().contains("not found"));
        }
    }

    @Test
    public void testDefaultLoaderClasspath() throws Exception {
        RenderEngine e = RenderEngine.build();
        // 加载存在的 classpath 资源（test/resources 暂未提供文件，加载一个 JUnit 类路径下的文件作为冒烟）
        // 仅验证方法可调用不抛错
        try {
            e.renderUrl("classpath:/non-existent.json", Map.of());
        Assertions.fail("expected Exception");
    } catch (Exception expected) {
            // 期望：资源找不到
        }
    }
}