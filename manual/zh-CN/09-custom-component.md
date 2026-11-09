# 09 自定义组件

内置 9 个组件覆盖了大多数场景。当业务需要"重复出现的模板模式"——比如"取分页"、"鉴权注入"、"字段脱敏"——可以把它封装成自定义组件。

## 一、`RenderComponent` 接口

```java
package fun.commons.template4j.core;

public interface RenderComponent {
    String name();                                  // 组件名（对应模板里的 @class）
    Object render(RenderContext context) throws Exception;
}
```

最简实现——"把所有字符串值转大写"：

```java
public class UpperComponent implements RenderComponent {
    @Override public String name() { return "upper"; }

    @Override
    public Object render(RenderContext ctx) throws Exception {
        Object value = ctx.getValue("select");       // 自动渲染后的值
        return value == null ? null : value.toString().toUpperCase();
    }
}
```

注册：

```java
RenderEngine engine = RenderEngine.build().withComponent(new UpperComponent());
```

模板：

```json
{ "out": { "@upper": "hello world" } }
// {"out":"HELLO WORLD"}
```

## 二、`RenderContext` 能力

组件的实现常常需要：从模板读字段、再渲染某些字段、断言必填字段、抛错。`RenderContext` 把这些集中起来。

| 方法 | 用途 |
| :--- | :--- |
| `getValue(name)` / `getValue(name, defaultValue)` | 读字段（已渲染） |
| `containsField(name)` | 判断字段是否存在 |
| `assertField(names...)` | 必填字段缺失时抛 `IllegalArgumentException` |
| `assertFieldOne(names...)` | 至少有一个存在 |
| `firstNonField(names...)` | 返回第一个存在的字段名 |
| `render(template[, subMap])` | 在当前 self 下渲染任意模板 |
| `renderField(name[, subMap])` | 渲染同级的某个字段 |
| `getSelf()` | 当前 SelfObject（含 vars、data、parent） |
| `getRenderer()` | 当前 JsonRenderer |

### 示例：分页查询包装

```java
public class PaginationComponent implements RenderComponent {
    @Override public String name() { return "pagination"; }

    @Override
    public Object render(RenderContext ctx) throws Exception {
        ctx.assertField("select");                              // select 必填
        JSONObject page = ctx.getValue("select");               // 已是渲染后的 JSONObject
        JSONObject template = ctx.getValue("body");             // body 是模板（未渲染）

        int size = page.getIntValue("size", 20);
        int num  = page.getIntValue("num", 1);
        String sort = page.getString("sort");

        // 把分页参数塞进 vars 再渲染 body
        return ctx.render(template, Map.of(
            "size", size,
            "num", num,
            "sort", sort == null ? "" : sort
        ));
    }
}
```

模板：

```json
{
    "@class": "pagination",
    "select": "{@page}",
    "body": {
        "limit": "{=$num * $size}",
        "offset": "{=$size}",
        "sort": "{=$sort}"
    }
}
```

## 三、组件命名冲突

如果新组件 `name()` 与内置同名（如 `if`），后注册的会**覆盖**内置。要恢复内置：

```java
engine.withComponent(new MyIfComponent());   // 覆盖内置 if
engine.withComponent(new IfRenderer());      // 恢复内置 if
```

## 四、依赖外部状态

组件是无状态的 Java 对象，但可以通过构造器注入任何依赖（数据库连接、配置、其它 service）：

```java
public class MaskComponent implements RenderComponent {
    private final MaskService maskService;
    public MaskComponent(MaskService s) { this.maskService = s; }
    @Override public String name() { return "mask"; }
    @Override
    public Object render(RenderContext ctx) throws Exception {
        String field = ctx.getValue("select");
        String type  = ctx.getValue("type", "phone");
        return maskService.mask(field, type);
    }
}

RenderEngine engine = RenderEngine.build()
    .withComponent(new MaskComponent(myMaskService));
```

模板：

```json
{
    "phone":    { "@mask": "{@user.phone}",    "type": "phone" },
    "idNumber": { "@mask": "{@user.idNumber}", "type": "id" }
}
```

## 五、调试技巧

1. **临时打印**：组件里 `System.out.println(ctx.getSelf().path() + " → " + result)`。
2. **错误位置**：抛 `RenderException(self.path(), "@class:xxx", "message", cause)`，错误会自动带上模板路径。
3. **复用内置**：优先想"是否能用 Groovy 脚本 + `withBean` 解决"——业务简单时往往更轻量。

## 六、注册时机

`withComponent` 返回**新引擎**——可与 `withBean`、`withImport` 任意组合：

```java
RenderEngine engine = RenderEngine.build()
    .withBean("session", session)
    .withComponent(new MaskComponent(maskService))
    .withImport("fun.example.MyUtil");
```