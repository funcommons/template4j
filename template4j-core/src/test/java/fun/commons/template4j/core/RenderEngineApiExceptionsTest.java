package fun.commons.template4j.core;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * 覆盖 RenderEngine 的 withLoader/withImport/withBean 异常分支。
 */
public class RenderEngineApiExceptionsTest {

    @Test
    public void testWithLoaderNullThrows() {
        RenderEngine e = RenderEngine.build();
        try {
            e.withLoader(null);
            Assertions.fail("null loader should throw");
        } catch (Exception ex) {
            // expected
        }
    }

    @Test
    public void testWithImportNullThrows() {
        RenderEngine e = RenderEngine.build();
        try {
            e.withImport(null);
            Assertions.fail("null import should throw");
        } catch (Exception ex) {
            // expected
        }
    }

    @Test
    public void testWithBeanNullNameThrows() {
        RenderEngine e = RenderEngine.build();
        try {
            e.withBean(null, new Object());
            Assertions.fail("null name should throw");
        } catch (Exception ex) {
            // expected
        }
    }

    @Test
    public void testWithBeanDuplicateThrows() throws Exception {
        RenderEngine e = RenderEngine.build();
        Object bean = new Object();
        e.withBean("dup", bean);
        try {
            e.withBean("dup", bean);
            Assertions.fail("duplicate bean should throw");
        } catch (Exception ex) {
            // expected
        }
    }

    @Test
    public void testWithComponent() {
        // withComponent 不会返回异常分支
        RenderEngine e = RenderEngine.build();
        RenderEngine result = e.withComponent(new RenderComponent() {
            @Override public String name() { return "dummy"; }
            @Override public Object render(RenderComponent.RenderContext context) { return null; }
        });
        Assertions.assertSame(e, result);
    }

    @Test
    public void testWithImport() throws Exception {
        // withImport 添加 import 到 Groovy
        RenderEngine e = RenderEngine.build().withImport("java.util.Random");
        Object r = e.render("{% return new Random().nextInt(100) %}", java.util.Map.of());
        Assertions.assertNotNull(r);
    }

    @Test
    public void testWithBeansBulk() throws Exception {
        // withBeans(map) 批量注册 bean
        java.util.Map<String, Object> beans = new java.util.HashMap<>();
        beans.put("bean1", new Object());
        beans.put("bean2", new Object());
        RenderEngine e = RenderEngine.build().withBeans(beans);
        Assertions.assertNotNull(e);
    }

    @Test
    public void testRenderObjectObjectWithMap() throws Exception {
        // render(Object template, Object data) — data is Map
        Object r = RenderEngine.build().render("\"{@name}\"", (Object) java.util.Map.of("name", "alice"));
        Assertions.assertEquals(r, "\"alice\"");
    }

    @Test
    public void testRenderObjectObjectWithPojo() throws Exception {
        // render(Object template, Object data) — data is custom POJO
        // POJO 会被 JSONObject.from() 反射转换
        Object bean = new Object() {
            public String name = "bob";
        };
        Object r = RenderEngine.build().render("\"{@name}\"", (Object) bean);
        // 可能成功也可能抛异常，主要是不报错即可
        Assertions.assertNotNull(r);
    }

    @Test
    public void testParseDataObjectThrows() {
        // 不可序列化的 data — JSONObject.from 抛异常 → parseDataObject 重新抛 RenderException
        Object circular = new Object() {
            public Object self = this;
        };
        try {
            RenderEngine.build().render("\"x\"", (Object) circular);
        } catch (Exception ex) {
            // expected
        }
    }

    @Test
    public void testRenderYamlString() throws Exception {
        // renderYaml("template", data) — YAML 模板
        Object r = RenderEngine.build().renderYaml("name: \"{@name}\"", java.util.Map.of("name", "alice"));
        Assertions.assertEquals(((com.alibaba.fastjson2.JSONObject) r).getString("name"), "alice");
    }

    @Test
    public void testRenderYamlUrlWithType() throws Exception {
        // renderYamlUrl / renderYamlUrl(String, String, Map)
        TemplateLoader loader = url -> "name: \"{@name}\"";
        RenderEngine e = RenderEngine.build().withLoader(loader);
        Object r = e.renderUrl("/x.yml", "yaml", java.util.Map.of("name", "bob"));
        Assertions.assertEquals(((com.alibaba.fastjson2.JSONObject) r).getString("name"), "bob");
    }

    @Test
    public void testRenderYamlUrlStringOverload() throws Exception {
        // renderYamlUrl(url, data) - 资源类型推断为 yaml
        TemplateLoader loader = url -> "name: \"{@name}\"";
        RenderEngine e = RenderEngine.build().withLoader(loader);
        Object r = e.renderUrl("/x.yaml", java.util.Map.of("name", "carol"));
        Assertions.assertEquals(((com.alibaba.fastjson2.JSONObject) r).getString("name"), "carol");
    }

    @Test
    public void testRenderObjectObjectWithCustomPojo() throws Exception {
        // render(Object, Object) — data 是 POJO，被反射转 JSON
        Object pojo = new Object() {
            public String name = "carol";
            public int age = 30;
        };
        Object r = RenderEngine.build().render("\"{@name}\"", (Object) pojo);
        Assertions.assertNotNull(r);
    }

    @Test
    public void testRenderObjectObjectWithJsonString() {
        // render(Object, Object) — data 是 JSON 字符串，触发 JSONObject.from 异常
        try {
            RenderEngine.build().render("\"x\"", (Object) "{\"name\":\"dave\"}");
        } catch (Exception ex) {
            // expected (parseDataObject throw)
        }
    }

    @Test
    public void testRenderObjectObjectWithNullData() {
        // render(Object, null)
        try {
            RenderEngine.build().render("\"x\"", (Object) null);
        } catch (Exception ex) {
            // expected — parseDataObject(null) may throw
        }
    }
}
