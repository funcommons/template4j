# 05 方法 `$name()` 与 FuncObject

除了"变量"和"注入对象"，模板还支持用 `$name()` 定义**用户方法**——一个可以在脚本里多次调用、参数任意的小函数。

## 一、定义方法

`$name()` 后缀的 key 是方法定义，值是 Groovy 脚本块（隐式包了 `{% … %}`）：

```json
{
    "$add()": "{% return _args[0] + _args[1] %}",
    "$demo()": "{% return _args[0] + ',' + _args(1, '默认值') %}",

    "result1": "{= add(1, 2) }",
    "result2": "{= demo('a') }"
}
```

调用：

```json
"计算": "{= add(@a, @b) }"
```

## 二、`_args` 参数访问

方法体内有内置数组 `_args`，接收调用时传入的所有实参：

| 写法 | 等价 |
| :--- | :--- |
| `_args[0]` | `_args(0)`、`_args.get(0)`、第一个参数 |
| `_args[1]` | `_args(1)`、`_args.get(1)`、第二个参数 |
| `_args(3)` | `_args.get(3)`、第三个参数（越界返回 null） |
| `_args(3, '默认值')` | 越界或 null 时返回 `'默认值'` |
| `_args` | 整个参数列表（List） |

## 三、方法与变量的区别

| | 变量 `$name` | 方法 `$name()` |
| :--- | :--- | :--- |
| 是否每次引用重新求值 | 否（首次求值后缓存） | 是（每次调用都执行） |
| 参数 | 无 | 有 `_args[]` |
| 引用语法 | `{$name}` 或脚本里 `name` | 脚本里 `name(arg1, …)` |
| 是否进入 self.vars 查找 | 是 | 是（自动包装成 FuncObject） |

## 四、上下文绑定

方法体内的 `_args`、`@`（= `data`）、`vars` 都是**调用点的上下文**，不是定义点的：

```json
{
    "$user()": "{= @name }",
    "child": {
        "@": { "name": "小明" },
        "out": "{= user() }"           // "小明"（调用点的 @）
    }
}
```

## 五、递归守卫（`locks`）

方法体内**不允许直接 / 间接递归调用自己**——引擎通过 `SelfObject.locks` 列表抛错：

```json
{
    "$fact()": "{% return _args[0] <= 1 ? 1 : _args[0] * fact(_args[0] - 1) %}"
}
```

→ 抛 `RenderException`。这是**有意的**：模板作者往往不是程序员，递归容易写出栈溢出。同理，方法之间相互递归（A 调 B，B 又调 A）也会被阻止。

需要递归时，把它写成 Java 方法，通过 `withBean` 注入：

```java
public class Utils {
    public static long factorial(int n) {
        return n <= 1 ? 1 : n * factorial(n - 1);
    }
}

engine.withImport("fun.example.Utils");   // 或 withBean("factorial", new Utils())
```

## 六、注入与内置方法的区别

| 场景 | 推荐 |
| :--- | :--- |
| 模板里只用到 1-2 行简单逻辑 | `$name()` |
| 需要递归 / 复杂算法 / 单元测试 | `withBean` / `withImport` Java 类 |
| 需要从数据库 / 文件 / 网络加载数据 | `withBean` 注入 Repository / Service |

## 七、方法在脚本里的返回值

方法必须 `return` 一个值；不 return 时返回 null。返回值类型可以任意（String、Integer、JSONObject、JSONArray、自定义 Java 对象等）。