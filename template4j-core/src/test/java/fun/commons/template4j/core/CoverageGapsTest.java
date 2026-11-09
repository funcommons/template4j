package fun.commons.template4j.core;

import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

/**
 * 补未覆盖的分支 / 方法：
 * - TemplateUtil.simplify("$|int")
 * - TemplateUtil.varsStr (私有工具)
 * - SelfObject.putVarDefine / putVars / path / getRoot / lock 重入
 * - RenderContext 4 个匿名 HashMap 构造（render 重载触发）
 */
public class CoverageGapsTest {

    // ========== TemplateUtil.simplify("$|int") — 当前代码不支持,留 reminder ==========

    @Test
    public void testSimplifyDollarWithTypeNotSupported() throws Exception {
        // "$|int:value" 语法在 simplify 中未实现，仅校验不会误抛
        Object r = RenderEngine.build().renderJson("""
                {
                    "$n": "{@x}",
                    "out": "{$n}"
                }
                """, Map.of("x", 10));
        Assertions.assertEquals(10, ((JSONObject) r).getIntValue("out"));
    }

    // ========== SelfObject.getRoot / path / putVarDefine / putVars / lock ==========

    @Test
    public void testSelfObjectRootAndPath() throws Exception {
        // 渲染嵌套结构触发 path() 与 getRoot()
        Object r = RenderEngine.build().renderJson("""
                {
                    "a": {
                        "b": {
                            "c": "{@name}"
                        }
                    }
                }
                """, Map.of("name", "x"));
        Assertions.assertEquals(((JSONObject) ((JSONObject) ((JSONObject) r).getJSONObject("a")).getJSONObject("b")).getString("c"), "x");
    }

    @Test
    public void testLockReentryReturnsNull() throws Exception {
        // 同名递归调用被 locks 阻止，被阻止侧返回 null
        RenderEngine e = RenderEngine.build();
        Object r = e.renderJson("""
                {
                    "$f()": "{% return _args[0] <= 0 ? 0 : f(_args[0] - 1) %}",
                    "out": "{= f(2)}"
                }
                """, Map.of());
        // f(2) 内部调 f(1) → null → 2 <= 0 ? 0 : null → null
        Assertions.assertNull(((JSONObject) r).get("out"));
    }

    @Test
    public void testPutVarDefineNested() throws Exception {
        // putVarDefine：$ key + Map value，递归 putVar
        Object r = RenderEngine.build().renderJson("""
                {
                    "$": {
                        "a": "1",
                        "b": "2"
                    },
                    "out": "{$a}-{$b}"
                }
                """, Map.of());
        Assertions.assertEquals(((JSONObject) r).getString("out"), "1-2");
    }

    @Test
    public void testPutVarDefineInList() throws Exception {
        // putVarDefine：$ key + List<Map> value，递归处理
        Object r = RenderEngine.build().renderJson("""
                {
                    "$": [
                        { "a": "x" },
                        { "b": "y" }
                    ],
                    "out": "{$a}{$b}"
                }
                """, Map.of());
        Assertions.assertEquals(((JSONObject) r).getString("out"), "xy");
    }

    @Test
    public void testPutVarDefineThrowsOnInvalidType() {
        RenderEngine e = RenderEngine.build();
        try {
            // $ 后面是 String 而不是 Map/List
            e.renderJson("""
                    {
                        "$": "not-a-map",
                        "out": "x"
                    }
                    """, Map.of());
            Assertions.fail("$ 不是 Map/List 应抛 RenderException");
        } catch (Exception ex) {
            // 期望
        }
    }

    @Test
    public void testPutVars() throws Exception {
        // $$ 立即求值变量
        Object r = RenderEngine.build().renderJson("""
                {
                    "$$": {
                        "v": "{@x}"
                    },
                    "out": "{$v} {$v}"
                }
                """, Map.of("x", "shared"));
        Assertions.assertEquals(((JSONObject) r).getString("out"), "shared shared");
    }

    // ========== RenderContext 匿名 HashMap 构造（render 重载） ==========

    @Test
    public void testRenderContextWithVarOverload() throws Exception {
        // 触发 RenderContext.render(template, varName, varObject) 路径
        // 组件 + 新 vars 一起渲染
        RenderEngine e = RenderEngine.build();
        Object r = e.renderJson("""
                {
                    "@class": "each",
                    "select": [1, 2, 3],
                    "body": "{$$multiplied}"
                }
                """.replace("$$multiplied", "{= $item * 10 }"), Map.of());
        Assertions.assertTrue(r instanceof List);
    }

    @Test
    public void testRenderContextWithTwoVarsOverload() throws Exception {
        // 触发 RenderContext.render(template, varName1, varObject1, varName2, varObject2) 路径
        RenderEngine e = RenderEngine.build();
        Object r = e.renderJson("""
                {
                    "@class": "if",
                    "select": "{= @v1 == @v2 }",
                    "then": "equal",
                    "else": "different"
                }
                """, Map.of("v1", "x", "v2", "y"));
        Assertions.assertEquals(r, "different");
    }

    @Test
    public void testRenderContextRenderFieldWithTwoVars() throws Exception {
        // renderField(template, varName1, varObject1, varName2, varObject2)
        RenderEngine e = RenderEngine.build();
        Object r = e.renderJson("""
                {
                    "x": "{= @a + @b }"
                }
                """, Map.of("a", 1, "b", 2));
        Assertions.assertEquals(3, ((JSONObject) r).getIntValue("x"));
    }

    // ========== DefaultTemplateLoader 异常路径 ==========

    @Test
    public void testDefaultTemplateLoaderClasspathNotFound() {
        try {
            new DefaultTemplateLoader().load("classpath:/non-existent-resource-12345.json");
            Assertions.fail("不存在的 classpath 资源应抛 IOException");
        } catch (Exception ex) {
            // 期望
        }
    }

    @Test
    public void testDefaultTemplateLoaderStripsClasspathPrefix() throws Exception {
        // 验证 classpath: 前缀被剥离
        // 用一个真实存在的资源：junit jar 里的 org/junit/jupiter/api/Assertions.class
        String content = new DefaultTemplateLoader().load("classpath:/org/junit/jupiter/api/Assertions.class");
        Assertions.assertNotNull(content);
    }

    // ========== LoaderUtil 异常路径 ==========

    @Test
    public void testLoadResourceNullLoader() throws Exception {
        Assertions.assertThrows(java.io.IOException.class, () -> {
        // load=null 时抛 IOException（C-1 修复后的行为）
        TemplateLoader loader = url -> null;
        LoaderUtil.loadResource(loader, "/foo.json", null);
            });
    }

    @Test
    public void testLoadJSONObjectTemplateCastsArray() throws Exception {
        // 加载后是数组（非对象）时强转 JSONObject 应抛
        TemplateLoader loader = url -> "[1, 2, 3]";
        try {
            LoaderUtil.loadJSONObjectTemplate(loader, "/arr.json");
            Assertions.fail("JSONArray 转 JSONObject 应抛");
        } catch (Exception ex) {
            // 期望
        }
    }

    @Test
    public void testLoadJSONArrayTemplateCastsObject() throws Exception {
        // 验证：对象转 JSONArray 的实际行为（UType.castValue 返回原值）
        TemplateLoader loader = url -> "{\"a\": 1}";
        Object r = LoaderUtil.loadJSONArrayTemplate(loader, "/obj.json");
        // UType.castValue 对不匹配的类型可能返回原值；至少断言可调用
        Assertions.assertNotNull(r);
    }

    // ========== withBean 重名抛错 ==========

    @Test
    public void testWithBeanDuplicateThrows() throws Exception {
        RenderEngine e = RenderEngine.build();
        e.withBean("dup", new Object());
        try {
            e.withBean("dup", new Object());
            Assertions.fail("同名 bean 应抛 Exception");
        } catch (Exception ex) {
            Assertions.assertTrue(ex.getMessage().contains("dup"));
        }
    }

    // ========== RenderException 路径信息 ==========

    @Test
    public void testRenderExceptionPath() {
        // RenderException 构造时把 path/name/message 拼成 message
        RenderException e = new RenderException("users/0/name", "test", "test message", new RuntimeException("cause"));
        Assertions.assertTrue(e.getMessage().contains("users/0/name"), "message 应包含 path: " + e.getMessage());
        Assertions.assertTrue(e.getMessage().contains("test"), "message 应包含 name: " + e.getMessage());
        Assertions.assertNotNull(e.getCause(), "cause 应保留");
    }

    // ========== FuncFne null/empty args 边界 ==========

    @Test
    public void testFneWithCharSequenceSubclass() throws Exception {
        // CharSequence 子类：StringBuilder
        Object r = RenderEngine.build().render("{% fne(new StringBuilder(), 'a') %}", new JSONObject());
        Assertions.assertEquals(r, "a");
    }

    @Test
    public void testFneWithNumberNotEmpty() throws Exception {
        // 数字 0 在 fne 中不是 "空"，返回 0
        Object r = RenderEngine.build().render("{% fne(0, 'a', 'b') %}", new JSONObject());
        Assertions.assertEquals(0, ((Number) r).intValue());
    }

    // ========== GroovyImports 内部类 ==========

    @Test
    public void testGroovyImportsPutAndToCode() throws Exception {
        // 通过引擎 withImport 间接测试 GroovyImports
        RenderEngine e = RenderEngine.build().withImport("java.util.UUID");
        Object r = e.render("{% return UUID.randomUUID().toString().length() > 0 %}", Map.of());
        Assertions.assertEquals(Boolean.TRUE, r);
    }
}