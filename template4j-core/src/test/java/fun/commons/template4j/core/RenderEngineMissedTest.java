package fun.commons.template4j.core;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

/**
 * Covers RenderEngine missed branches.
 */
public class RenderEngineMissedTest {

    @Test
    public void testWithBeans() throws Exception {
        RenderEngine e = RenderEngine.build();
        e = e.withBeans(Map.of("mybean", "beanvalue"));
        Object r = e.renderJson("{\"out\": \"{= mybean }\"}", Map.of());
        Assertions.assertEquals(((com.alibaba.fastjson2.JSONObject) r).getString("out"), "beanvalue");
    }

    @Test
    public void testWithBeansDuplicateThrows() throws Exception {
        RenderEngine e = RenderEngine.build();
        e = e.withBean("dup", "v1");
        try {
            e.withBean("dup", "v2");
            Assertions.fail("Should throw for duplicate bean");
        } catch (Exception ex) {
            Assertions.assertTrue(ex.getMessage().contains("already exists"));
        }
    }

    @Test
    public void testRenderWithObjectData() throws Exception {
        RenderEngine e = RenderEngine.build();
        Object r = e.render(Map.of("out", "{@name}"), Map.of("name", "test"));
        Assertions.assertTrue(r instanceof com.alibaba.fastjson2.JSONObject);
        Assertions.assertEquals(((com.alibaba.fastjson2.JSONObject) r).getString("out"), "test");
    }

    @Test
    public void testRenderJsonWithObjectData() throws Exception {
        RenderEngine e = RenderEngine.build();
        Object r = e.renderJson("{\"out\": \"{@name}\"}", (Object) Map.of("name", "obj"));
        Assertions.assertEquals(((com.alibaba.fastjson2.JSONObject) r).getString("out"), "obj");
    }

    @Test
    public void testRenderJsonWithStringData() throws Exception {
        RenderEngine e = RenderEngine.build();
        Object r = e.renderJson("{\"out\": \"{@name}\"}", "{\"name\":\"strdata\"}");
        Assertions.assertEquals(((com.alibaba.fastjson2.JSONObject) r).getString("out"), "strdata");
    }

    @Test
    public void testRenderYamlWithMapData() throws Exception {
        RenderEngine e = RenderEngine.build();
        Object r = e.renderYaml("out: '{@name}'", Map.of("name", "yamlval"));
        Assertions.assertEquals(((com.alibaba.fastjson2.JSONObject) r).getString("out"), "yamlval");
    }

    @Test
    public void testRenderYamlWithObjectData() throws Exception {
        RenderEngine e = RenderEngine.build();
        Object r = e.renderYaml("out: '{@name}'", (Object) Map.of("name", "yobj"));
        Assertions.assertEquals(((com.alibaba.fastjson2.JSONObject) r).getString("out"), "yobj");
    }

    @Test
    public void testRenderYamlWithStringData() throws Exception {
        RenderEngine e = RenderEngine.build();
        Object r = e.renderYaml("out: '{@name}'", "name: ystr");
        Assertions.assertEquals(((com.alibaba.fastjson2.JSONObject) r).getString("out"), "ystr");
    }

    @Test
    public void testRenderWithInvalidJsonTemplate() throws Exception {
        try {
            RenderEngine.build().renderJson("not json", Map.of());
            Assertions.fail("Should throw for invalid JSON");
        } catch (RenderException e) {
            Assertions.assertTrue(e.getMessage().contains("template is not json"));
        }
    }

    @Test
    public void testRenderWithInvalidDataString() throws Exception {
        try {
            RenderEngine.build().render("{\"out\":1}", "not json or yaml object");
            Assertions.fail("Should throw for invalid data");
        } catch (RenderException e) {
            // expected
        }
    }

    @Test
    public void testWithImport() throws Exception {
        RenderEngine e = RenderEngine.build();
        e = e.withImport("java.time.LocalDateTime");
        Object r = e.renderJson("{\"out\": \"{= LocalDateTime.now().getClass().simpleName }\"}", Map.of());
        Assertions.assertEquals(((com.alibaba.fastjson2.JSONObject) r).getString("out"), "LocalDateTime");
    }

    @Test
    public void testRenderWithMapData() throws Exception {
        Object r = RenderEngine.build().render(Map.of("val", "{@x}"), Map.of("x", 10));
        Assertions.assertEquals(10, ((com.alibaba.fastjson2.JSONObject) r).getIntValue("val"));
    }

    @Test
    public void testRenderWithStringData() throws Exception {
        // render(Object template, String data) — uses parseDataString
        Object r = RenderEngine.build().render(Map.of("val", "{@x}"), "{\"x\": 10}");
        Assertions.assertEquals(10, ((com.alibaba.fastjson2.JSONObject) r).getIntValue("val"));
    }
}
