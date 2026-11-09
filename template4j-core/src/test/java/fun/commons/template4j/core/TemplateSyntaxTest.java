package fun.commons.template4j.core;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

/**
 * 模板语法测试类
 * 测试所有模板语法特性: JSONPath, 局部变量, Groovy 表达式, 特殊字段等
 * 对应 FUNCTIONAL_TEST.md 中的模板语法测试部分 (TC-SYNTAX)
 */
public class TemplateSyntaxTest {

    private RenderEngine engine;
    private Map<String, Object> testData;

    @BeforeEach
    public void setUp() {
        engine = RenderEngine.build();
        testData = Map.of(
                "name", "张三",
                "age", 25,
                "score", 95,
                "user", Map.of(
                        "name", "张三",
                        "age", 25,
                        "address", Map.of(
                                "city", "北京",
                                "street", "中关村"
                        )
                ),
                "items", List.of("A", "B", "C"),
                "users", List.of(
                        Map.of("name", "张三", "age", 25, "score", 95),
                        Map.of("name", "李四", "age", 15, "score", 65),
                        Map.of("name", "王五", "age", 30, "score", 55)
                ),
                "price", 100,
                "quantity", 2,
                "discount", 0.1
        );
    }

    // ========== JSONPath 表达式测试 ==========

    /**
     * TC-SYNTAX-001: 简单字段访问
     */
    @Test
    public void testJsonPathSimpleField() throws Exception {
        String template = "{@name}";
        Object result = engine.render(template, testData);
        Assertions.assertEquals(result, "张三");
    }

    /**
     * TC-SYNTAX-002: 嵌套字段访问
     */
    @Test
    public void testJsonPathNestedField() throws Exception {
        String template = "{@user.name}";
        Object result = engine.render(template, testData);
        Assertions.assertEquals(result, "张三");

        // 深层嵌套
        String template2 = "{@user.address.city}";
        Object result2 = engine.render(template2, testData);
        Assertions.assertEquals(result2, "北京");
    }

    /**
     * TC-SYNTAX-003: 数组索引访问
     */
    @Test
    public void testJsonPathArrayIndex() throws Exception {
        String template = "{@items[0]}";
        Object result = engine.render(template, testData);
        Assertions.assertEquals(result, "A");

        String template2 = "{@items[2]}";
        Object result2 = engine.render(template2, testData);
        Assertions.assertEquals(result2, "C");
    }

    /**
     * TC-SYNTAX-004: 数组投影
     */
    @Test
    public void testJsonPathArrayProjection() throws Exception {
        String template = "{@users[*].name}";
        Object result = engine.render(template, testData);
        JSONArray array = (JSONArray) result;

        Assertions.assertEquals(3, array.size());
        Assertions.assertEquals(array.get(0), "张三");
        Assertions.assertEquals(array.get(1), "李四");
        Assertions.assertEquals(array.get(2), "王五");
    }

    /**
     * TC-SYNTAX-005: JSONPath 过滤器
     */
    @Test
    public void testJsonPathFilter() throws Exception {
        String template = "{@users[?(@.age > 18)]}";
        Object result = engine.render(template, testData);
        JSONArray array = (JSONArray) result;

        // 应该有 2 个用户 (age = 25 和 30)
        Assertions.assertEquals(2, array.size());
    }

    /**
     * TC-SYNTAX-006: 空 JSONPath 访问根对象
     */
    @Test
    public void testJsonPathRoot() throws Exception {
        String template = "{\"data\": \"{@}\"}";
        Object result = engine.renderJson(template, testData);
        JSONObject jsonResult = (JSONObject) result;

        Assertions.assertNotNull(jsonResult.get("data"), "根对象不应为 null");
    }

    /**
     * TC-SYNTAX-008: 不存在的字段
     */
    @Test
    public void testJsonPathNonExistentField() throws Exception {
        String template = "{@notExist}";
        Object result = engine.render(template, testData);
        Assertions.assertNull(result, "不存在的字段应返回 null");
    }

    // ========== 局部变量表达式测试 ==========

    /**
     * TC-SYNTAX-009: 访问局部变量
     */
    @Test
    public void testLocalVariableAccess() throws Exception {
        String template = """
            {
                "@class": "each",
                "select": "{@items}",
                "body": "{$item}"
            }
            """;

        Object result = engine.renderJson(template, testData);
        JSONArray array = (JSONArray) result;

        Assertions.assertEquals(3, array.size());
        Assertions.assertEquals(array.get(0), "A");
    }

    /**
     * TC-SYNTAX-010: 变量 + JSONPath
     */
    @Test
    public void testLocalVariableWithJsonPath() throws Exception {
        String template = """
            {
                "@class": "each",
                "select": "{@users}",
                "body": "{$item.name}"
            }
            """;

        Object result = engine.renderJson(template, testData);
        JSONArray array = (JSONArray) result;

        Assertions.assertEquals(3, array.size());
        Assertions.assertEquals(array.get(0), "张三");
    }

    /**
     * TC-SYNTAX-011: 不存在的变量
     */
    @Test
    public void testLocalVariableNonExistent() throws Exception {
        String template = "{\"value\": \"{$notExist}\"}";
        Object result = engine.renderJson(template, testData);
        JSONObject jsonResult = (JSONObject) result;

        Assertions.assertNull(jsonResult.get("value"), "不存在的变量应返回 null");
    }

    // ========== Groovy 表达式测试 ==========

    /**
     * TC-SYNTAX-012: 算术运算
     */
    @Test
    public void testGroovyExpressionArithmetic() throws Exception {
        String template = "{= @age + 10 }";
        Object result = engine.render(template, testData);
        Assertions.assertEquals(35, ((Number) result).intValue());
    }

    /**
     * TC-SYNTAX-013: 逻辑运算
     */
    @Test
    public void testGroovyExpressionLogical() throws Exception {
        String template = "{= @age >= 18 }";
        Object result = engine.render(template, testData);
        Assertions.assertTrue((Boolean) result);
    }

    /**
     * TC-SYNTAX-014: 三元运算符
     */
    @Test
    public void testGroovyExpressionTernary() throws Exception {
        String template = "{= @age >= 18 ? '成年' : '未成年' }";
        Object result = engine.render(template, testData);
        Assertions.assertEquals(result, "成年");
    }

    /**
     * TC-SYNTAX-015: 字符串方法
     */
    @Test
    public void testGroovyExpressionStringMethod() throws Exception {
        String template = "{= @name.toUpperCase() }";
        Object result = engine.render(template, testData);
        Assertions.assertEquals(result, "张三"); // 中文没有大小写，但方法应该能调用
    }

    /**
     * TC-SYNTAX-016: 集合方法
     */
    @Test
    public void testGroovyExpressionCollectionMethod() throws Exception {
        String template = "{= @items.size() }";
        Object result = engine.render(template, testData);
        Assertions.assertEquals(3, ((Number) result).intValue());
    }

    /**
     * TC-SYNTAX-017: 复杂表达式
     */
    @Test
    public void testGroovyExpressionComplex() throws Exception {
        String template = "{= @price * @quantity * (1 - @discount) }";
        Object result = engine.render(template, testData);

        // 100 * 2 * (1 - 0.1) = 200 * 0.9 = 180.0
        double expected = 180.0;
        double actual = ((Number) result).doubleValue();
        Assertions.assertEquals(expected, actual, 0.001);
    }

    /**
     * TC-SYNTAX-018: 语法错误
     */
    @Test
    public void testGroovyExpressionSyntaxError() {
        String template = "{= invalid syntax ( }";

        try {
            engine.render(template, testData);
            Assertions.fail("应该抛出 RenderException");
        } catch (Exception e) {
            Assertions.assertTrue(e.getMessage().toLowerCase().contains("script") ||
                    e.getMessage().toLowerCase().contains("error") ||
                    e.getMessage().toLowerCase().contains("syntax"), "错误信息应包含 'script' 或 'error'");
        }
    }

    // ========== Groovy 代码块测试 ==========

    /**
     * TC-SYNTAX-019: 多行脚本
     */
    @Test
    public void testGroovyScriptBlock() throws Exception {
        String template = """
            {%
              def total = 0
              for (item in @items) {
                total += 1
              }
              return total
            %}
            """;

        Object result = engine.render(template, testData);
        Assertions.assertEquals(3, ((Number) result).intValue());
    }

    // ========== 特殊字段测试 ==========

    /**
     * TC-SYNTAX-022: @ 数据覆盖
     */
    @Test
    public void testSpecialFieldDataOverride() throws Exception {
        String template = """
            {
                "@": { "name": "新名字" },
                "userName": "{@name}"
            }
            """;

        Object result = engine.renderJson(template, testData);
        JSONObject jsonResult = (JSONObject) result;

        Assertions.assertEquals(jsonResult.getString("userName"), "新名字");
    }

    /**
     * TC-SYNTAX-023: * 字段合并
     */
    @Test
    public void testSpecialFieldMerge() throws Exception {
        String template = """
            {
                "name": "张三",
                "*": {
                    "age": 25,
                    "city": "北京"
                }
            }
            """;

        Object result = engine.renderJson(template, testData);
        JSONObject jsonResult = (JSONObject) result;

        Assertions.assertEquals(jsonResult.getString("name"), "张三");
        Assertions.assertEquals(25, jsonResult.getIntValue("age"));
        Assertions.assertEquals(jsonResult.getString("city"), "北京");
    }

    /**
     * TC-SYNTAX-024: $enter{} 脚本
     */
    @Test
    public void testSpecialFieldEnterScript() throws Exception {
        String template = """
            {
                "$enter{}": "{% vars.putAt('timestamp', 12345) %}",
                "time": "{$timestamp}"
            }
            """;

        Object result = engine.renderJson(template, testData);
        JSONObject jsonResult = (JSONObject) result;

        Assertions.assertEquals(12345, jsonResult.getIntValue("time"));
    }

    /**
     * TC-SYNTAX-025: $return{} 脚本
     */
    @Test
    public void testSpecialFieldReturnScript() throws Exception {
        String template = """
            {
                "name": "{@name}",
                "$return{}": "{% _result.put('processed', true) %}"
            }
            """;

        Object result = engine.renderJson(template, testData);
        JSONObject jsonResult = (JSONObject) result;

        Assertions.assertEquals(jsonResult.getString("name"), "张三");
        Assertions.assertTrue(jsonResult.getBooleanValue("processed"));
    }

    /**
     * TC-SYNTAX-026: # 注释字段
     */
    @Test
    public void testSpecialFieldComment() throws Exception {
        String template = """
            {
                "#comment": "这是注释",
                "name": "{@name}"
            }
            """;

        Object result = engine.renderJson(template, testData);
        JSONObject jsonResult = (JSONObject) result;

        Assertions.assertEquals(jsonResult.getString("name"), "张三");
        Assertions.assertFalse(jsonResult.containsKey("#comment"), "注释字段不应该出现在结果中");
    }

    // ========== 变量定义测试 ==========

    /**
     * 变量定义: $ 字段定义变量
     */
    @Test
    public void testVariableDefinition() throws Exception {
        String template = """
            {
                "$myVar": "Hello",
                "message": "{$myVar}, {@name}!"
            }
            """;

        Object result = engine.renderJson(template, testData);
        JSONObject jsonResult = (JSONObject) result;

        Assertions.assertEquals(jsonResult.getString("message"), "Hello, 张三!");
        Assertions.assertFalse(jsonResult.containsKey("$myVar"), "变量定义字段不应出现在结果中");
    }

    /**
     * 变量定义: $ 集中定义
     */
    @Test
    public void testVariableCentralizedDefinition() throws Exception {
        String template = """
            {
                "$": {
                    "var1": "Hello",
                    "var2": "{@name}"
                },
                "message": "{$var1}, {$var2}!"
            }
            """;

        Object result = engine.renderJson(template, testData);
        JSONObject jsonResult = (JSONObject) result;

        Assertions.assertEquals(jsonResult.getString("message"), "Hello, 张三!");
    }

    /**
     * 变量作用域: 嵌套作用域
     */
    @Test
    public void testVariableScope() throws Exception {
        String template = """
            {
                "$level": "Level1",
                "value1": "{$level}",
                "nested": {
                    "$level": "Level2",
                    "value2": "{$level}"
                }
            }
            """;

        Object result = engine.renderJson(template, testData);
        JSONObject jsonResult = (JSONObject) result;

        Assertions.assertEquals(jsonResult.getString("value1"), "Level1");
        Assertions.assertEquals(jsonResult.getJSONObject("nested").getString("value2"), "Level2");
    }

    // ========== 方法定义测试 ==========

    /**
     * 方法定义: $func() 定义方法
     */
    @Test
    public void testMethodDefinition() throws Exception {
        String template = """
            {
                "$add()": "{% _args[0] + _args[1] %}",
                "result": "{= add(10, 20) }"
            }
            """;

        Object result = engine.renderJson(template, testData);
        JSONObject jsonResult = (JSONObject) result;

        Assertions.assertEquals(30, jsonResult.getIntValue("result"));
    }

    /**
     * 方法定义: 默认参数
     */
    @Test
    public void testMethodWithDefaultArgs() throws Exception {
        String template = """
            {
                "$greet()": "{= 'Hello, ' + _args(0, 'Guest') }",
                "msg1": "{= greet('张三') }",
                "msg2": "{= greet() }"
            }
            """;

        Object result = engine.renderJson(template, testData);
        JSONObject jsonResult = (JSONObject) result;

        Assertions.assertEquals(jsonResult.getString("msg1"), "Hello, 张三");
        Assertions.assertEquals(jsonResult.getString("msg2"), "Hello, Guest");
    }

    // ========== 综合测试 ==========

    /**
     * 综合测试: 混合使用多种语法
     */
    @Test
    public void testMixedSyntax() throws Exception {
        String template = """
            {
                "$userName": "{@name}",
                "greeting": "Hello, {$userName}!",
                "isAdult": "{= @age >= 18 }",
                "items": {
                    "@class": "each",
                    "select": "{@items}",
                    "body": {
                        "value": "{$item}",
                        "index": "{$key}"
                    }
                },
                "score": {
                    "@class": "if",
                    "select": "{= @score >= 90 }",
                    "then": "优秀",
                    "else": "良好"
                }
            }
            """;

        Object result = engine.renderJson(template, testData);
        JSONObject jsonResult = (JSONObject) result;

        Assertions.assertEquals(jsonResult.getString("greeting"), "Hello, 张三!");
        Assertions.assertTrue(jsonResult.getBooleanValue("isAdult"));
        Assertions.assertEquals(3, jsonResult.getJSONArray("items").size());
        Assertions.assertEquals(jsonResult.getString("score"), "优秀");
    }

    /**
     * 综合测试: 复杂嵌套场景
     */
    @Test
    public void testComplexNestedScenario() throws Exception {
        String template = """
            {
                "$": {
                    "threshold": 60,
                    "status": "active"
                },
                "report": {
                    "@class": "each",
                    "select": "{@users}",
                    "nonnull": true,
                    "body": {
                        "@class": "if",
                        "select": "{= $item.score >= $threshold }",
                        "then": {
                            "name": "{$item.name}",
                            "score": "{$item.score}",
                            "level": {
                                "@class": "switch",
                                "select": "{= Math.floor($item.score / 10) + '' }",
                                "cases": {
                                    "9": "A级",
                                    "8": "B级",
                                    "7": "C级",
                                    "6": "D级"
                                },
                                "default": "E级"
                            }
                        },
                        "else": null
                    }
                }
            }
            """;

        Object result = engine.renderJson(template, testData);
        JSONObject jsonResult = (JSONObject) result;
        JSONArray report = jsonResult.getJSONArray("report");

        // 应该有 2 个用户满足 score >= 60
        Assertions.assertEquals(2, report.size());
        Assertions.assertEquals(report.getJSONObject(0).getString("level"), "A级");
        Assertions.assertEquals(report.getJSONObject(1).getString("level"), "D级");
    }
}
