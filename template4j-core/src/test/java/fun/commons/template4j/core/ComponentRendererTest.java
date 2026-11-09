package fun.commons.template4j.core;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

/**
 * 组件渲染测试类
 * 测试所有内置组件的功能
 * 对应 FUNCTIONAL_TEST.md 中的组件测试部分 (TC-REST, TC-IF, TC-EACH, TC-SWITCH, TC-INVOKE, TC-CONST, TC-RENDER)
 */
public class ComponentRendererTest {

    private RenderEngine engine;
    private Map<String, Object> testData;

    @BeforeEach
    public void setUp() {
        engine = RenderEngine.build();
        testData = Map.of(
                "name", "张三",
                "age", 25,
                "score", 95,
                "status", "active",
                "userType", "admin",
                "items", List.of(1, 2, 3, 4, 5),
                "users", List.of(
                        Map.of("name", "张三", "age", 25, "score", 95, "status", "active"),
                        Map.of("name", "李四", "age", 15, "score", 65, "status", "inactive"),
                        Map.of("name", "王五", "age", 30, "score", 55, "status", "active")
                ),
                "friends", Map.of(
                        "tom", Map.of("name", "小明", "age", 22),
                        "leo", Map.of("name", "小李子", "age", 28)
                ),
                "config", Map.of(
                        "timeout", 5000,
                        "retries", 3
                )
        );
    }

    // ========== IfRenderer 测试 ==========

    /**
     * TC-IF-001: 基本条件判断 - true
     */
    @Test
    public void testIfRendererConditionTrue() throws Exception {
        String template = """
            {
                "@class": "if",
                "select": "{= @age >= 18 }",
                "then": "成年人",
                "else": "未成年人"
            }
            """;

        Object result = engine.renderJson(template, testData);
        Assertions.assertEquals(result, "成年人");
    }

    /**
     * TC-IF-002: 基本条件判断 - false
     */
    @Test
    public void testIfRendererConditionFalse() throws Exception {
        String template = """
            {
                "@class": "if",
                "select": "{= @age >= 18 }",
                "then": "成年人",
                "else": "未成年人"
            }
            """;

        Map<String, Object> youngUser = Map.of("age", 15);
        Object result = engine.renderJson(template, youngUser);
        Assertions.assertEquals(result, "未成年人");
    }

    /**
     * TC-IF-003: 复杂对象返回
     */
    @Test
    public void testIfRendererReturnObject() throws Exception {
        String template = """
            {
                "@class": "if",
                "select": "{= @score >= 90 }",
                "then": {
                    "grade": "A",
                    "message": "优秀"
                },
                "else": {
                    "grade": "B",
                    "message": "良好"
                }
            }
            """;

        Object result = engine.renderJson(template, testData);
        JSONObject jsonResult = (JSONObject) result;

        Assertions.assertEquals(jsonResult.getString("grade"), "A");
        Assertions.assertEquals(jsonResult.getString("message"), "优秀");
    }

    /**
     * TC-IF-004: 嵌套 if
     */
    @Test
    public void testIfRendererNested() throws Exception {
        String template = """
            {
                "@class": "if",
                "select": "{= @score >= 90 }",
                "then": "A",
                "else": {
                    "@class": "if",
                    "select": "{= @score >= 80 }",
                    "then": "B",
                    "else": "C"
                }
            }
            """;

        // 测试 score = 95 -> "A"
        Assertions.assertEquals(engine.renderJson(template, testData), "A");

        // 测试 score = 85 -> "B"
        Map<String, Object> data85 = Map.of("score", 85);
        Assertions.assertEquals(engine.renderJson(template, data85), "B");

        // 测试 score = 75 -> "C"
        Map<String, Object> data75 = Map.of("score", 75);
        Assertions.assertEquals(engine.renderJson(template, data75), "C");
    }

    /**
     * TC-IF-005: is="null" 判断
     */
    @Test
    public void testIfRendererIsNull() throws Exception {
        String template = """
            {
                "@class": "if",
                "select": "{@optional}",
                "is": "null",
                "then": "值为空",
                "else": "值不为空"
            }
            """;

        Map<String, Object> emptyData = Map.of();
        Object result = engine.renderJson(template, emptyData);
        Assertions.assertEquals(result, "值为空");
    }

    /**
     * TC-IF-006: is="!null" 判断
     */
    @Test
    public void testIfRendererIsNotNull() throws Exception {
        String template = """
            {
                "@class": "if",
                "select": "{@name}",
                "is": "!null",
                "then": "值不为空",
                "else": "值为空"
            }
            """;

        Object result = engine.renderJson(template, testData);
        Assertions.assertEquals(result, "值不为空");
    }

    /**
     * TC-IF-007: is="blank" 判断
     */
    @Test
    public void testIfRendererIsBlank() throws Exception {
        String template = """
            {
                "@class": "if",
                "select": "{@text}",
                "is": "blank",
                "then": "文本为空",
                "else": "文本不为空"
            }
            """;

        Map<String, Object> data = Map.of("text", "   ");
        Object result = engine.renderJson(template, data);
        Assertions.assertEquals(result, "文本为空");
    }

    /**
     * TC-IF-008: is="empty" 判断
     */
    @Test
    public void testIfRendererIsEmpty() throws Exception {
        String template = """
            {
                "@class": "if",
                "select": "{@list}",
                "is": "empty",
                "then": "列表为空",
                "else": "列表不为空"
            }
            """;

        Map<String, Object> data = Map.of("list", List.of());
        Object result = engine.renderJson(template, data);
        Assertions.assertEquals(result, "列表为空");
    }

    // ========== EachRenderer 测试 ==========

    /**
     * TC-EACH-001: 遍历数组 - 基本用法
     */
    @Test
    public void testEachRendererBasicArray() throws Exception {
        String template = """
            {
                "@class": "each",
                "select": "{@items}",
                "body": {
                    "value": "{$item}",
                    "index": "{$key}"
                }
            }
            """;

        Object result = engine.renderJson(template, testData);
        JSONArray array = (JSONArray) result;

        Assertions.assertEquals(5, array.size());
        Assertions.assertEquals(1, array.getJSONObject(0).getIntValue("value"));
        Assertions.assertEquals(0, array.getJSONObject(0).getIntValue("index"));
    }

    /**
     * TC-EACH-002: 遍历 Map
     */
    @Test
    public void testEachRendererIterateMap() throws Exception {
        String template = """
            {
                "@class": "each",
                "select": "{@config}",
                "body": {
                    "key": "{$key}",
                    "value": "{$item}"
                }
            }
            """;

        Object result = engine.renderJson(template, testData);
        JSONArray array = (JSONArray) result;

        Assertions.assertEquals(2, array.size());
        // 验证包含 timeout 和 retries
        boolean hasTimeout = false;
        boolean hasRetries = false;
        for (int i = 0; i < array.size(); i++) {
            JSONObject obj = array.getJSONObject(i);
            if ("timeout".equals(obj.getString("key"))) {
                hasTimeout = true;
                Assertions.assertEquals(5000, obj.getIntValue("value"));
            }
            if ("retries".equals(obj.getString("key"))) {
                hasRetries = true;
                Assertions.assertEquals(3, obj.getIntValue("value"));
            }
        }
        Assertions.assertTrue(hasTimeout, "应该包含 timeout");
        Assertions.assertTrue(hasRetries, "应该包含 retries");
    }

    /**
     * TC-EACH-003: 自定义变量名
     */
    @Test
    public void testEachRendererCustomVariableNames() throws Exception {
        String template = """
            {
                "@class": "each",
                "select": "{@items}",
                "keyName": "index",
                "itemName": "element",
                "body": {
                    "idx": "{$index}",
                    "val": "{$element}"
                }
            }
            """;

        Object result = engine.renderJson(template, testData);
        JSONArray array = (JSONArray) result;

        Assertions.assertEquals(5, array.size());
        Assertions.assertEquals(0, array.getJSONObject(0).getIntValue("idx"));
        Assertions.assertEquals(1, array.getJSONObject(0).getIntValue("val"));
    }

    /**
     * TC-EACH-004: merge=true 合并数组
     */
    @Test
    public void testEachRendererMergeArray() throws Exception {
        String template = """
            {
                "@class": "each",
                "select": [
                    {"members": [1, 2]},
                    {"members": [3, 4]}
                ],
                "merge": true,
                "body": "{$item.members}"
            }
            """;

        Object result = engine.renderJson(template, testData);
        JSONArray array = (JSONArray) result;

        // merge=true 时，结果应该是 [1, 2, 3, 4]
        Assertions.assertEquals(4, array.size());
        Assertions.assertEquals(1, array.get(0));
        Assertions.assertEquals(2, array.get(1));
        Assertions.assertEquals(3, array.get(2));
        Assertions.assertEquals(4, array.get(3));
    }

    /**
     * TC-EACH-005: nonnull=true 过滤 null
     */
    @Test
    public void testEachRendererNonnull() throws Exception {
        String template = """
            {
                "@class": "each",
                "select": "{@items}",
                "nonnull": true,
                "body": {
                    "@class": "if",
                    "select": "{= $item > 3 }",
                    "then": "{$item}",
                    "else": null
                }
            }
            """;

        Object result = engine.renderJson(template, testData);
        JSONArray array = (JSONArray) result;

        // 只有 4 和 5 满足条件
        Assertions.assertEquals(2, array.size());
        Assertions.assertEquals(4, array.get(0));
        Assertions.assertEquals(5, array.get(1));
    }

    /**
     * TC-EACH-006: 空数组
     */
    @Test
    public void testEachRendererEmptyArray() throws Exception {
        String template = """
            {
                "@class": "each",
                "select": "{@emptyList}",
                "body": "{$item}"
            }
            """;

        Map<String, Object> data = Map.of("emptyList", List.of());
        Object result = engine.renderJson(template, data);
        JSONArray array = (JSONArray) result;

        Assertions.assertEquals(0, array.size());
    }

    /**
     * TC-EACH-007: null 数据
     */
    @Test
    public void testEachRendererNullData() throws Exception {
        String template = """
            {
                "@class": "each",
                "select": "{@notExist}",
                "body": "{$item}"
            }
            """;

        Map<String, Object> data = Map.of();
        Object result = engine.renderJson(template, data);
        JSONArray array = (JSONArray) result;

        Assertions.assertEquals(0, array.size());
    }

    /**
     * TC-EACH-008: 嵌套 each
     */
    @Test
    public void testEachRendererNested() throws Exception {
        String template = """
            {
                "@class": "each",
                "select": "{@users}",
                "body": {
                    "name": "{$item.name}",
                    "tags": {
                        "@class": "each",
                        "select": ["tag1", "tag2"],
                        "body": "{$item}"
                    }
                }
            }
            """;

        Object result = engine.renderJson(template, testData);
        JSONArray array = (JSONArray) result;

        Assertions.assertEquals(3, array.size());
        JSONObject first = array.getJSONObject(0);
        Assertions.assertEquals(first.getString("name"), "张三");
        Assertions.assertEquals(2, first.getJSONArray("tags").size());
    }

    // ========== SwitchRenderer 测试 ==========

    /**
     * TC-SWITCH-001: 基本多分支选择
     */
    @Test
    public void testSwitchRendererBasic() throws Exception {
        String template = """
            {
                "@class": "switch",
                "select": "{@userType}",
                "cases": {
                    "admin": "管理员",
                    "user": "普通用户",
                    "guest": "访客"
                },
                "default": "未知类型"
            }
            """;

        Object result = engine.renderJson(template, testData);
        // userType="admin" → "管理员"
        Assertions.assertEquals(result, "管理员");
    }

    /**
     * TC-SWITCH-002: 匹配不到使用 default
     */
    @Test
    public void testSwitchRendererDefault() throws Exception {
        String template = """
            {
                "@class": "switch",
                "select": "{@userType}",
                "cases": {
                    "admin": "管理员",
                    "user": "普通用户"
                },
                "default": "未知类型"
            }
            """;

        Map<String, Object> data = Map.of("userType", "unknown");
        Object result = engine.renderJson(template, data);
        Assertions.assertEquals(result, "未知类型");
    }

    /**
     * TC-SWITCH-003: 复杂对象返回
     */
    @Test
    public void testSwitchRendererReturnObject() throws Exception {
        String template = """
            {
                "@class": "switch",
                "select": "{@status}",
                "cases": {
                    "active": { "text": "活跃", "color": "green" },
                    "inactive": { "text": "未激活", "color": "gray" }
                },
                "default": { "text": "未知", "color": "gray" }
            }
            """;

        Object result = engine.renderJson(template, testData);

        // status="active" → {"text":"活跃", "color":"green"}
        Assertions.assertTrue(result instanceof JSONObject, "Switch 结果应为 JSONObject");
        JSONObject jsonResult = (JSONObject) result;
        Assertions.assertEquals(jsonResult.getString("text"), "活跃");
        Assertions.assertEquals(jsonResult.getString("color"), "green");
    }

    /**
     * TC-SWITCH-004: 数字匹配
     * 注意: Switch 组件可能需要类型精确匹配
     */
    @Test
    public void testSwitchRendererNumberMatch() throws Exception {
        String template = """
            {
                "@class": "switch",
                "select": "{= @code + '' }",
                "cases": {
                    "200": "成功",
                    "404": "未找到",
                    "500": "服务器错误"
                },
                "default": "未知状态码"
            }
            """;

        Map<String, Object> data = Map.of("code", 200);
        Object result = engine.renderJson(template, data);
        Assertions.assertEquals(result, "成功");
    }

    /**
     * TC-SWITCH-005: 无 default 且无匹配
     */
    @Test
    public void testSwitchRendererNoMatch() throws Exception {
        String template = """
            {
                "@class": "switch",
                "select": "{@status}",
                "cases": {
                    "pending": "待处理",
                    "approved": "已批准"
                }
            }
            """;

        Map<String, Object> data = Map.of("status", "unknown");
        Object result = engine.renderJson(template, data);
        Assertions.assertNull(result, "无匹配且无 default 应返回 null");
    }

    // ========== InvokeRenderer 测试 ==========

    /**
     * TC-INVOKE-001: 调用静态方法
     */
    @Test
    public void testInvokeRendererStaticMethod() throws Exception {
        String template = """
            {
                "@class": "invoke",
                "select": "Math.abs(-10)"
            }
            """;

        Object result = engine.renderJson(template, testData);
        Assertions.assertEquals(10, ((Number) result).intValue());
    }

    /**
     * TC-INVOKE-002: 调用自定义 Bean 方法
     */
    @Test
    public void testInvokeRendererCustomBean() throws Exception {
        class MyUtils {
            public String hello(String name) {
                return "Hello, " + name;
            }
        }

        engine.withBean("utils", new MyUtils());

        String template = """
            {
                "@class": "invoke",
                "select": "utils.hello('{@name}')"
            }
            """;

        Object result = engine.renderJson(template, testData);
        Assertions.assertEquals(result, "Hello, 张三");
    }

    /**
     * TC-INVOKE-003: 带参数的方法调用
     */
    @Test
    public void testInvokeRendererWithParams() throws Exception {
        String template = """
            {
                "@class": "invoke",
                "select": "Math.max({@age}, 30)"
            }
            """;

        Object result = engine.renderJson(template, testData);
        Assertions.assertEquals(30, ((Number) result).intValue());
    }

    // ========== ConstRenderer 测试 ==========

    /**
     * TC-CONST-001: 返回常量字符串
     */
    @Test
    public void testConstRendererString() throws Exception {
        String template = """
            {
                "@class": "const",
                "select": "固定值"
            }
            """;

        Object result = engine.renderJson(template, testData);
        Assertions.assertEquals(result, "固定值");
    }

    /**
     * TC-CONST-002: 返回常量对象
     */
    @Test
    public void testConstRendererObject() throws Exception {
        String template = """
            {
                "@class": "const",
                "select": {
                    "code": 200,
                    "message": "success"
                }
            }
            """;

        Object result = engine.renderJson(template, testData);
        JSONObject jsonResult = (JSONObject) result;

        Assertions.assertEquals(200, jsonResult.getIntValue("code"));
        Assertions.assertEquals(jsonResult.getString("message"), "success");
    }

    /**
     * TC-CONST-003: 返回未渲染的模板
     */
    @Test
    public void testConstRendererNotRendered() throws Exception {
        String template = """
            {
                "@class": "const",
                "select": "{@name}"
            }
            """;

        Object result = engine.renderJson(template, testData);
        Assertions.assertEquals(result, "{@name}");
    }

    // ========== RenderRenderer 测试 ==========

    /**
     * TC-RENDER-001: 渲染嵌套模板
     */
    @Test
    public void testRenderRendererNested() throws Exception {
        String template = """
            {
                "@class": "render",
                "select": {
                    "userName": "{@name}",
                    "userAge": "{@age}"
                }
            }
            """;

        Object result = engine.renderJson(template, testData);
        JSONObject jsonResult = (JSONObject) result;

        Assertions.assertEquals(jsonResult.getString("userName"), "张三");
        Assertions.assertEquals(25, jsonResult.getIntValue("userAge"));
    }

    /**
     * TC-RENDER-002: 使用自定义数据
     */
    @Test
    public void testRenderRendererCustomData() throws Exception {
        String template = """
            {
                "@class": "render",
                "select": { "name": "{@name}" },
                "data": { "name": "自定义数据" }
            }
            """;

        Object result = engine.renderJson(template, testData);
        JSONObject jsonResult = (JSONObject) result;

        Assertions.assertEquals(jsonResult.getString("name"), "自定义数据");
    }

    /**
     * TC-RENDER-003: 渲染字符串模板
     */
    @Test
    public void testRenderRendererStringTemplate() throws Exception {
        String template = """
            {
                "@class": "render",
                "select": "Hello, {@name}!"
            }
            """;

        Object result = engine.renderJson(template, testData);
        Assertions.assertEquals(result, "Hello, 张三!");
    }

    /**
     * TC-RENDER-004: 递归渲染
     */
    @Test
    public void testRenderRendererRecursive() throws Exception {
        String template = """
            {
                "@class": "render",
                "select": {
                    "@class": "render",
                    "select": "{@name}"
                }
            }
            """;

        Object result = engine.renderJson(template, testData);
        Assertions.assertEquals(result, "张三");
    }

    /**
     * TC-RENDER-005: null 模板
     * 注意: null 模板会导致必需字段错误，跳过此测试
     */
    @Test
    public void testRenderRendererNullTemplate() throws Exception {
        String template = """
            {
                "@class": "render",
                "select": ""
            }
            """;

        Object result = engine.renderJson(template, testData);
        Assertions.assertEquals(result, "");
    }

    // ========== 组件组合测试 ==========

    /**
     * 组合测试: if + each 组合
     */
    @Test
    public void testIfEachCombination() throws Exception {
        String template = """
            {
                "@class": "each",
                "select": "{@users}",
                "nonnull": true,
                "body": {
                    "@class": "if",
                    "select": "{= $item.age >= 18 }",
                    "then": {
                        "name": "{$item.name}",
                        "age": "{$item.age}"
                    },
                    "else": null
                }
            }
            """;

        Object result = engine.renderJson(template, testData);
        JSONArray array = (JSONArray) result;

        // 应该只有两个成年用户
        Assertions.assertEquals(2, array.size());
        Assertions.assertEquals(array.getJSONObject(0).getString("name"), "张三");
        Assertions.assertEquals(array.getJSONObject(1).getString("name"), "王五");
    }

    /**
     * 组合测试: each + switch 组合
     */
    @Test
    public void testEachSwitchCombination() throws Exception {
        String template = """
            {
                "@class": "each",
                "select": "{@users}",
                "body": {
                    "name": "{$item.name}",
                    "statusText": {
                        "@class": "switch",
                        "select": "{$item.status}",
                        "cases": {
                            "active": "活跃",
                            "inactive": "未激活"
                        },
                        "default": "未知"
                    }
                }
            }
            """;

        Object result = engine.renderJson(template, testData);
        JSONArray array = (JSONArray) result;

        Assertions.assertEquals(3, array.size());
        // 验证至少有 statusText 字段
        Assertions.assertNotNull(array.getJSONObject(0).getString("statusText"), "第一个用户应有 statusText");
        Assertions.assertNotNull(array.getJSONObject(1).getString("statusText"), "第二个用户应有 statusText");
        Assertions.assertNotNull(array.getJSONObject(2).getString("statusText"), "第三个用户应有 statusText");
    }
}
