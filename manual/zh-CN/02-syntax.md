# 02 语法基础

模板与普通 JSON / YAML 的唯一区别，是**字符串中的占位符**与**对象的特殊键**。本章把这两套语法列全。

## 一、占位符（5 种）

占位符只能出现在**字符串值**里，遇到时按类型分派：

| 形态 | 名字 | 何时用 | 返回 |
| :--- | :--- | :--- | :--- |
| `{@ path}` | 数据表达式 | 引用数据字段 | JSONPath 求值结果 |
| `{$ name}` | 变量表达式 | 引用 `$name` 定义的变量 | 变量值 |
| `{= expr}` | 行内表达式 | 一行计算 | Groovy 单行表达式的结果 |
| `{% script %}` | 脚本块 | 多行 Groovy | `return` 的值 |
| `<& … &>` | 字面文本 | 不渲染、转义 | 原样字符串 |

### 1. `{@ path}` 数据表达式

`@` 后跟 JSONPath（基于 fastjson2），用于从数据 Map 取值。

```java
engine.render("""
        {
            "name": "{@name}",
            "firstLike": "{@likes[0]}",
            "friendName": "{@friends.tom.name}",
            "root": "{@}"
        }
        """,
        Map.of(
            "name", "小红",
            "likes", List.of("电影", "音乐"),
            "friends", Map.of("tom", Map.of("name", "小明"))
        ));

// 结果
// {
//   "name":"小红",
//   "firstLike":"电影",
//   "friendName":"小明",
//   "root": { "name":"小红", "likes":[...], "friends":{...} }
// }
```

完整 JSONPath 语法参考 fastjson2 文档：[fastjson2 JSONPath](https://alibaba.github.io/fastjson2/jsonpath_cn)。要点：

- `@`、`@.x`、`@.x.y`、`@[0]` 等价
- `@'key with space'` 用引号包含特殊键名
- `@$` 表示根（可省略为 `@`）
- 过滤：`@.items[?(@.price > 100)]`
- 函数：`@.name.upper()`、`@.values.size()`

> ⚠️ JSONPath 中**不能包含 `}` 字符**；遇到此类键名请用 `[@'xxx']` 形式。

> ✅ 0.3.0 起：`@..x`（JSONPath deepScan）已支持。`codeReplaceAt` 把 `@..x` 改写为 `data("..x")` 函数调用，避开 Groovy 的 `..` spread 算子语义。

### 2. `{$ name}` 变量表达式

引用模板内 `$name` 定义的变量。变量名后可继续跟 JSONPath：

```json
{
    "$users": [{"name": "李雷"}, {"name": "韩梅梅"}],
    "first": "{$users[0].name}"
}
```

如果变量未定义，**整个表达式返回 null**，字符串中的 null 被替换为空字符串。

### 3. `{= expr}` 行内表达式

Groovy 单行表达式。`@` 在表达式内自动被改写为 `data.…`：

```json
{
    "status": "{= @age >= 18 ? '成年' : '未成年' }",
    "upperName": "{= @name.toUpperCase() }",
    "combined": "{= @first + ' ' + @last }"
}
```

注意：

- 表达式**不可以有 `{}` 子块**（避免嵌套解析问题），需要多行用 `{% … %}`。
- `{=@x}` 与 `{@x}` 等价；后者更快（不走 Groovy）。

### 4. `{% script %}` 脚本块

多行 Groovy，必须 `return` 出值（或最后一行是值表达式）：

```json
{
    "greeting%": [
        "String hello(String name){",
        "  return 'hello ' + name;",
        "}",
        "return hello(@name);"
    ]
}
```

脚本中可直接写：

- `@xxx` → `data.xxx`（自动改写）
- `cast(value, 'int')` → 类型转换
- `data(...)` / `vars.xxx` / `render(...)` / `load(...)` / `fne(...)` → 内置工具
- 所有 `java.*` / `com.alibaba.fastjson2.*` / `org.apache.commons.lang3.*` → 默认导入

> 多行脚本建议写到 YAML 的 `|` 字面量里，或拆成 Java 方法通过 `withBean` 注入。详见 [脚本章节](04-scripting.md)。

### 5. `<& … &>` 字面文本

把内容标记为不渲染的原文。常用于在文档/示例里展示模板语法本身：

```json
{
    "demo": "原样：<&{@name}&> 实际值：{@name}"
}
```

输入 `name=小红` → `"原样：{@name} 实际值：小红"`。

## 二、键的语法

对象的 key 上有 9 种特殊前缀/后缀，所有普通 key 与之并列：

| 形态 | 语义 | 是否渲染 | 详见 |
| :--- | :--- | :--- | :--- |
| `#` / `#xxx` | 注释 | 否 | [§ 注释](#注释) |
| `#` 后缀 (`xxx#`) | 字段值原样 | 否 | [§ 不渲染值](#不渲染值) |
| `$` | 变量定义 | 否（按需渲染） | [变量章节](03-data-and-vars.md) |
| `$$` | 变量定义（先渲染后存） | 是 | [变量章节](03-data-and-vars.md) |
| `$xxx()` | 用户方法定义 | — | [方法章节](05-functions.md) |
| `$enter{}` | 渲染前脚本 | — | [变量章节](03-data-and-vars.md) |
| `$return{}` | 渲染后脚本 | — | [变量章节](03-data-and-vars.md) |
| `@` | 局部数据（值为模板） | — | [变量章节](03-data-and-vars.md) |
| `@@` | 局部数据（值为字面） | — | [变量章节](03-data-and-vars.md) |
| `@class` | 渲染组件调度 | — | [组件章节](06-components.md) |
| `@xxx\|yyy` | 组件简写 | — | [组件章节](06-components.md) |
| `*` | 合并键 | — | [合并章节](07-merge-and-include.md) |
| `&include` | 包含子模板 | — | [合并章节](07-merge-and-include.md) |
| `%` 后缀 (`xxx%`) | 字段值是脚本 | 是（按 Groovy） | [§ 脚本后缀](#脚本后缀) |
| `&` 后缀 (`xxx&`) | 字段值不渲染 | 否 | [§ 不渲染值](#不渲染值) |
| `<&…&>` 包裹的 key | 转义 key 字面 | — | [§ 转义 key](#转义-key) |

### 注释

以 `#` 开头的 key 会被忽略：

```json
{
    "#": "这是一段说明",
    "#TODO": "未来要改",
    "name": "{@name}"
}
```

### 不渲染值

`xxx#` 与 `xxx&` 后缀都让字段**原样取值、不走占位符渲染**：

```json
{
    "demo1": "{@name}",            // 渲染： 小红
    "demo2#": "{@name}",           // 原样： {@name}
    "demo3&": "{@name}",           // 原样： {@name}
    "$a#": "{@name}",              // 变量 a 的值是字面 "{@name}"
    "$b&": "{@name}"               // 同上
}
```

区别：`xxx#` 用于"注释性保留"，`xxx&` 用于"显式声明不解"。两者结果相同。

### 脚本后缀

`xxx%` 后缀的字段值是裸脚本（不需要写 `{% %}` 包裹）：

```json
{
    "result%": "return @age >= 18 ? '成年' : '未成年'"
}
```

值也可以是字符串数组，会用 `\r\n` 拼接（适合多行脚本）。

### 转义 key

如果某个 key 形如 `#xxx` 但**你真的想保留它**作为字面 key，可以用 `<&…&>` 包裹：

```json
{
    "<&#demo&>": "{@name}"          // 真正的 key 是 "#demo"
}
```

## 三、占位符求值顺序

同一个字符串里出现多个占位符时，**从后往前**解析，以保证嵌套表达式正确：

```json
"out": "{= @user.{@field} }"      // 内层 {@field} 先求值，得到 "name"
                                  // 外层 {= @user.name } 再求值
```

整个字符串是单个占位符的特殊情况：返回值的**原始类型**（如 JSONObject、List）会保留，而不是被强制字符串化。

## 四、null 的处理

| 场景 | 字符串中行为 | 完整占位符时 |
| :--- | :--- | :--- |
| JSONPath 未命中 | `null` 文本（保留 JSON null 语义） | 返回 `null` |
| 变量未定义 | 空字符串 | 返回 `null` |
| Groovy 脚本返回 null | 空字符串 | 返回 `null` |

若想"未命中返回默认值"，在 Groovy 里用 `fne(@'path', 'default')`。