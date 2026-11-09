# 01 快速开始

## 环境

- JDK 17+
- Maven 3.8+

## 添加依赖

```xml
<dependency>
    <groupId>fun.commons</groupId>
    <artifactId>template4j-core</artifactId>
    <version>0.1.0</version>
</dependency>
```

## 第一个模板

```java
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONWriter;
import fun.commons.template4j.core.RenderEngine;

public class Main {
    public static void main(String[] args) {
        // 1. 创建渲染引擎（线程安全，建议单例）
        RenderEngine engine = RenderEngine.build();

        // 2. 准备数据
        JSONObject data = JSONObject.parse("""
                {
                    "name": "小红",
                    "age": 19,
                    "likes": ["电影", "音乐", "小说"]
                }
                """);

        // 3. 定义模板（普通 JSON + 占位符）
        String templateJson = """
                {
                    "info": "{@name} 同学 {@age} 岁，是 {= @age >= 18 ? '成年' : '未成年' } 人",
                    "likes": "{@likes[0]}、{@likes[1]} 等"
                }
                """;

        // 4. 渲染
        Object result = engine.render(JSONObject.parse(templateJson), data);

        // 5. 输出
        System.out.println(JSON.toJSONString(result, JSONWriter.Feature.PrettyFormat));
    }
}
```

输出：

```json
{
    "info": "小红 同学 19 岁，是 成年 人",
    "likes": "电影、音乐 等"
}
```

## 三种入参形式

`engine.render(...)` 有多个重载，按使用频率排序：

```java
// 最常用：模板是 JSONObject/Map，数据是 Map
Object render(Object template, Map<String, Object> data);

// 字符串模板：自动按格式推断（JSON / YAML / 纯文本）
Object render(String template, Map<String, Object> data);

// 字符串模板 + 字符串数据：自动按格式推断
Object render(String template, String dataJsonOrYaml);
```

若要显式指定格式，可使用 `renderJson(...)` / `renderYaml(...)`。

## YAML 模板

模板同样支持 YAML。多行脚本建议写在 YAML 的 `|` 字面量里，避免 JSON 转义：

```java
String yaml = """
        status: 0
        message: success
        welcome%: |-
          String hello(String name){ return "hello " + name; }
          return hello(@name);
        """;

Object out = engine.renderYaml(yaml, Map.of("name", "template4j"));
// out.get("welcome") == "hello template4j"
```

> `key%` 后缀的写法见 [语法基础 - 脚本后缀](02-syntax.md#key--脚本后缀)。

## 线程安全

`RenderEngine.build()` 返回的是不可变快照；`withBean` / `withComponent` / `withImport` 会返回**新实例**，不修改原对象。因此可以单例持有，跨线程复用：

```java
public final class Engines {
    public static final RenderEngine DEFAULT = RenderEngine.build();
    public static RenderEngine withAuth(Map<String, Object> session) {
        return DEFAULT.withBean("session", session);
    }
}
```

## 下一步

- 想系统学语法 → [02 语法基础](02-syntax.md)
- 想从已有工程代码迁移 → [03 数据与变量](03-data-and-vars.md)
- 想做控制流（条件/循环/分支）→ [06 内置组件](06-components.md)