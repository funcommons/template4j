package fun.commons.template4j.core;

import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 补 90% LINE 覆盖率：覆盖 IfRenderer 全部分支、RenderContext renderField 重载、
 * DefaultTemplateLoader 异常路径、SelfObject putVarDefine / putVars 边界。
 */
public class ExtraCoverageTest {

    private final RenderEngine engine = RenderEngine.build();

    // ========== IfRenderer 全 11 个 is 分支 ==========

    @Test
    public void testIfIsTrue() throws Exception {
        // is="true" + select=true → 走 then
        Object r = engine.renderJson("""
                {"@class":"if","select":true,"is":"true","then":"T","else":"F"}
                """, Map.of());
        Assertions.assertEquals(r, "T");
    }

    @Test
    public void testIfIsNotTrue() throws Exception {
        // is="!true" + select=true → 走 else
        Object r = engine.renderJson("""
                {"@class":"if","select":true,"is":"!true","then":"T","else":"F"}
                """, Map.of());
        Assertions.assertEquals(r, "F");
    }

    @Test
    public void testIfIsFalse() throws Exception {
        // is="false" + select=false → then
        Object r = engine.renderJson("""
                {"@class":"if","select":false,"is":"false","then":"T","else":"F"}
                """, Map.of());
        Assertions.assertEquals(r, "T");
    }

    @Test
    public void testIfIsNotFalse() throws Exception {
        // is="!false" + select=false → else
        Object r = engine.renderJson("""
                {"@class":"if","select":false,"is":"!false","then":"T","else":"F"}
                """, Map.of());
        Assertions.assertEquals(r, "F");
    }

    @Test
    public void testIfIsNull() throws Exception {
        // is="null" + select={@x} 缺省 → null → then
        Object r = engine.renderJson("""
                {"@class":"if","select":"{@x}","is":"null","then":"N","else":"V"}
                """, Map.of());
        Assertions.assertEquals(r, "N");
    }

    @Test
    public void testIfIsNotNull() throws Exception {
        // is="!null" + select 有值 → then
        Object r = engine.renderJson("""
                {"@class":"if","select":"@x","is":"!null","then":"V","else":"N"}
                """, Map.of("x", "hello"));
        Assertions.assertEquals(r, "V");
    }

    @Test
    public void testIfIsBlank() throws Exception {
        // is="blank" + select 是空字符串 → then
        Object r = engine.renderJson("""
                {"@class":"if","select":"   ","is":"blank","then":"B","else":"V"}
                """, Map.of());
        Assertions.assertEquals(r, "B");
    }

    @Test
    public void testIfIsNotBlank() throws Exception {
        // is="!blank" + select 非空 → then
        Object r = engine.renderJson("""
                {"@class":"if","select":"hi","is":"!blank","then":"V","else":"B"}
                """, Map.of());
        Assertions.assertEquals(r, "V");
    }

    @Test
    public void testIfIsEmpty() throws Exception {
        // is="empty" + select 空 map → then
        Object r = engine.renderJson("""
                {"@class":"if","select":"{@m}","is":"empty","then":"E","else":"V"}
                """, Map.of("m", Map.of()));
        Assertions.assertEquals(r, "E");
    }

    @Test
    public void testIfIsNotEmpty() throws Exception {
        // is="!empty" + select 非空 map → then
        Object r = engine.renderJson("""
                {"@class":"if","select":"{@m}","is":"!empty","then":"V","else":"E"}
                """, Map.of("m", Map.of("a", 1)));
        Assertions.assertEquals(r, "V");
    }

    @Test
    public void testIfIsDefault() throws Exception {
        // is 未知值 → test 保持 false → 走 else
        Object r = engine.renderJson("""
                {"@class":"if","select":true,"is":"unknown","then":"T","else":"F"}
                """, Map.of());
        Assertions.assertEquals(r, "F");
    }

    @Test
    public void testIfThenOrElseAliases() throws Exception {
        // then/else 也可以用 true/false 别名
        Object r = engine.renderJson("""
                {"@class":"if","select":true,"true":"T","false":"F"}
                """, Map.of());
        Assertions.assertEquals(r, "T");
    }

    // ========== RenderContext.renderField 重载 (用自定义组件触发) ==========

    /** 自定义组件，调用各种 renderField 重载 */
    @Test
    public void testRenderContextRenderFieldOverloads() throws Exception {
        // 自定义组件，触发 3 个 renderField 重载
        RenderEngine e = RenderEngine.build().withComponent(new fun.commons.template4j.core.RenderComponent() {
            @Override public String name() { return "covertest"; }
            @Override public Object render(RenderContext ctx) throws Exception {
                // 触发 renderField(fieldName) — 无 var
                String r1 = ctx.renderField("a");
                // 触发 renderField(fieldName, varName, varObject) — 单 var
                String r2 = ctx.renderField("b", "extra", "V2");
                // 触发 renderField(fieldName, varName1, varObject1, varName2, varObject2) — 双 var
                String r3 = ctx.renderField("c", "v1", "FIRST", "v2", "SECOND");
                return r1 + "|" + r2 + "|" + r3;
            }
        });
        Object r = e.renderJson("""
                {
                    "@class": "covertest",
                    "a": "{@name}",
                    "b": "{@name}/{$extra}",
                    "c": "{$v1}-{$v2}"
                }
                """, Map.of("name", "alice"));
        Assertions.assertEquals(r, "alice|alice/V2|FIRST-SECOND");
    }

    // ========== DefaultTemplateLoader 异常路径 ==========

    @Test
    public void testDefaultLoaderClasspathNotFound() {
        // 验证抛 IOException 路径
        try {
            new DefaultTemplateLoader().load("classpath:/non-existent-resource-" + System.nanoTime() + ".json");
            Assertions.fail("不存在的 classpath 资源应抛 IOException");
        } catch (Exception ex) {
            // 期望
        }
    }

    @Test
    public void testDefaultLoaderStripsLeadingSlash() throws Exception {
        // 验证 classpath: 前缀和开头的 / 都被剥离
        // 找一个真实存在的资源：junit jar
        String content = new DefaultTemplateLoader().load("/org/junit/jupiter/api/Assertions.class");
        Assertions.assertNotNull(content);
        Assertions.assertTrue(content.length() > 100, "应能加载到内容: " + content.substring(0, Math.min(50, content.length())));
    }

    // ========== SelfObject.putVarDefine / putVars 边界 ==========

    @Test
    public void testPutVarDefineNestedMap() throws Exception {
        // putVarDefine 递归处理 Map
        Object r = engine.renderJson("""
                {
                    "$": {
                        "a": "1",
                        "nested": { "b": "2" }
                    },
                    "x": "{$a} {$nested.b}"
                }
                """, Map.of());
        Assertions.assertEquals(((JSONObject) r).getString("x"), "1 2");
    }

    @Test
    public void testPutVarDefineListOfMaps() throws Exception {
        // putVarDefine 处理 List<Map>
        Object r = engine.renderJson("""
                {
                    "$": [
                        { "a": "1" },
                        { "b": "2" }
                    ],
                    "x": "{$a}{$b}"
                }
                """, Map.of());
        Assertions.assertEquals(((JSONObject) r).getString("x"), "12");
    }

    @Test
    public void testPutVarDefineInvalidTypeThrows() {
        // putVarDefine 收到非 Map/List 应抛 RenderException
        try {
            engine.renderJson("""
                    {"$": "not a map", "x": "y"}
                    """, Map.of());
            Assertions.fail("$ 不是 Map/List 应抛 RenderException");
        } catch (Exception ex) {
            Assertions.assertTrue(ex.getMessage().contains("not support") || ex.getMessage().contains("script"), "异常应说明 type 不支持: " + ex.getMessage());
        }
    }

    @Test
    public void testPutVarDefineWithTemplateValue() throws Exception {
        // putVarDefine 的 value 是模板（会渲染后再 put）
        Object r = engine.renderJson("""
                {
                    "$": {
                        "greeting": "{@name}!"
                    },
                    "out": "{$greeting}"
                }
                """, Map.of("name", "Alice"));
        Assertions.assertEquals(((JSONObject) r).getString("out"), "Alice!");
    }

    @Test
    public void testPutVarsEmpty() throws Exception {
        // putVars(null) 静默返回
        Object r = engine.renderJson("""
                {"x": "y"}
                """, Map.of());
        Assertions.assertEquals(((JSONObject) r).getString("x"), "y");
    }

    @Test
    public void testPutVarsWithMap() throws Exception {
        // putVars 立即求值
        Object r = engine.renderJson("""
                {
                    "$$": {
                        "v": "{@x}"
                    },
                    "out": "{$v}"
                }
                """, Map.of("x", "shared"));
        Assertions.assertEquals(((JSONObject) r).getString("out"), "shared");
    }

    @Test
    public void testPutVarsWithList() throws Exception {
        // putVars 处理 List<Map>
        Object r = engine.renderJson("""
                {
                    "$$": [
                        { "a": "{@x}" },
                        { "b": "{@y}" }
                    ],
                    "out": "{$a}{$b}"
                }
                """, Map.of("x", "1", "y", "2"));
        Assertions.assertEquals(((JSONObject) r).getString("out"), "12");
    }

    @Test
    public void testPutVarsWithInvalidTypeThrows() {
        // putVars 收到非 Map/List 应抛
        try {
            engine.renderJson("""
                    {"$$": "not a map", "x": "y"}
                    """, Map.of());
            Assertions.fail("$$ 不是 Map/List 应抛 RenderException");
        } catch (Exception ex) {
            // 期望
        }
    }
}