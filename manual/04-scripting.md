# 04 Groovy 脚本

模板中需要复杂计算时，嵌入 Groovy 是首选。本章讲 Groovy 在模板里能用的全部能力。

## 一、两种占位符

| 形态 | 行数 | `{}` 子块 | 必填 `return` |
| :--- | :--- | :--- | :--- |
| `{= expr}` | 单行 | 不允许 | 否（取表达式值） |
| `{% script %}` | 多行 | 允许 | 是 |

### 单行

```json
{
    "adult": "{= @age >= 18 }",
    "firstLike": "{= @likes[0].toUpperCase() }"
}
```

### 多行

```json
{
    "result%": [
        "int line = 18;",
        "String status(int age){ return age >= line ? '成年' : '未成年'; }",
        "return @name + '是' + status(@age) + '人';"
    ]
}
```

## 二、`@` 自动改写

模板里写 `@xxx`，进入 Groovy 前会被改写成 `data.xxx`。这个改写会**保留**字符串字面量与注释里的 `@`：

| 原始 | 改写后 |
| :--- | :--- |
| `@name` | `data.name` |
| `@.x` | `data.x` |
| `@['k']` | `data['k']` |
| `@` | `data` |
| `'literal @name'` | `'literal @name'`（保留） |
| `// comment @name` | `// comment @name`（保留） |
| `@('jsonpath')` | `data('jsonpath')` |

三种引用数据的方式（在 `{% %}` 脚本里）：

```java
String script = """
        return [
            a: @user.name,                              // Java 字段访问
            b: @'user.name',                             // JSONPath（推荐用于复杂路径）
            c: @('user.' + fieldName),                  // 动态路径
            root: @                                      // 根数据
        ];
        """;
```

`@'xxx'` 和 `@"xxx"` 内是 JSONPath，对**未命中字段返回 null**；`@.xxx` 是 Java 字段访问，**未命中会抛 NPE**。不确定是否存在的字段，建议用 `@'…'`。

## 三、默认导入

`{% %}` / `{= …}` 内的脚本**无需 import** 即可使用：

- `java.*`
- `com.alibaba.fastjson2.*`（JSONObject、JSONArray、JSONPath 等）
- `org.apache.commons.lang3.*`（StringUtils、BooleanUtils、ArrayUtils 等）

可通过 `engine.withImport("com.example.MyClass")` 注册额外的类。

## 四、内置 Func\* 工具

脚本内可直接调用 6 个工具函数（不需要前缀，都是顶层标识符）：

| 名称 | 用途 | 例子 |
| :--- | :--- | :--- |
| `data` | 读/查数据 | `data('users[0].name')`、`data(@user)` |
| `vars` | 读/写模板变量 | `vars.x = 1; return vars.foo` |
| `render` | 在当前上下文二次渲染子模板 | `render(@subTemplate)` |
| `load` | 加载并解析 URL 资源 | `load('./partial.json')` |
| `cast` | 类型转换 | `cast(@pageSize, 'int')` |
| `fne` | 取第一个非空值 | `fne(@a, @b, 'default')` |

### `data`

```groovy
// JSONPath 字符串查询
String name = data('user.name');

// 切换数据根（返回新的 FuncData，绑定到传入的 Map）
FuncData sub = data(someMap);
String x = sub('key');        // 等价于 JSONPath.eval(someMap, "key")

// 在脚本里构造 JSON 数据也是 data() 模式（传入 Map）
return data([a: 1, b: 2]);    // 渲染后是 JSONObject
```

### `vars`

```groovy
// 读
String t = vars.title;        // 等价于 {$title}

// 写
vars.x = 123;                 // 在当前节点定义 x
```

### `render`

在当前 SelfObject 上下文里渲染一段新模板。常用于"加载到的子模板再用当前数据渲染"：

```groovy
JSONObject tpl = load('./profile.json');
return render(tpl);           // 用当前 data/vars 渲染 tpl
```

### `load`

```groovy
JSONObject profile = load('http://example.com/templates/profile.json');
```

URL 是相对于当前资源 URL 解析的。

### `cast`

```groovy
Integer n = cast(@page.size, 'int');      // 字符串 → int
Long l = cast(@rowCount, Long.class);     // 字符串 → Long
JSONObject obj = cast(@rawJson, JSONObject.class);   // 字符串 → JSONObject
JSONArray arr = cast(@rawList, JSONArray.class);
```

支持的简写类型名：`int`、`long`、`double`、`float`、`boolean`、`String`、`Date`、`Integer`、`Long`、`Double`、`Float`、`Boolean`、`BigInteger`、`BigDecimal`、`JSONObject`、`JSONArray`。

`null` 输入返回 `null`（不会 NPE）。

### `fne`

返回第一个"非空"的值（`null`、空字符串、空集合、空 Map 都算空）：

```groovy
String name = fne(@user.nickname, @user.name, '匿名');
Integer code = fne(@result.code, @result.errno, 0);
```

## 五、注入对象（Beans）

通过 `engine.withBean(name, bean)` 把 Java 对象暴露给所有脚本，作为顶层标识符：

```java
Map<String, Object> ctx = Map.of("user", Map.of("age", 19));
RenderEngine engine = RenderEngine.build().withBean("templates", new MyTemplateRepo());

Object out = engine.render("""
        {
            "$tpl": "{= templates.find('welcome') }",
            "greeting": "{= templates.render(@tpl, @user) }"
        }
        """, ctx);
```

注入对象的**属性访问**和**方法调用**都能在脚本里直接写：

```java
public class MyService {
    public String hello(String name) { return "Hi " + name; }
    public int version = 1;
}

engine.withBean("svc", new MyService());

// 模板
"{= svc.hello(@name) }"        // 调用方法
"{= svc.version }"             // 读字段
```

## 六、类型优先级

脚本中遇到同名标识符，按以下优先级解析：

1. **本地变量**（脚本内 `int x = …`）
2. **注入对象**（`withBean` 注册的）
3. **内置对象**（`data` / `vars` / `render` / `load` / `cast` / `fne`）
4. **模板变量**（`$name` 定义的）

## 七、限制

- 模板编译是**一次性**的（第一次渲染时）。后续渲染命中编译产物；同一模板反复渲染几乎无开销。
- 模板中的脚本**不可调试**——单步断点只能 Java 端做到。复杂逻辑建议写在 Java 类里，通过 `withBean` 注入。
- 模板引擎**信任模板来源**：可执行任意代码。不要把不受信的模板直接渲染（详见 [最佳实践](10-best-practices.md)）。