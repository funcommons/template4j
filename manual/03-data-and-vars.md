# 03 数据与变量

模板的"输入"有两层：**外部数据**（来自 Java 端）和**模板内部变量**（模板自己定义）。本章把这两种状态以及它们在作用域上的切换讲清楚。

## 一、外部数据：传进来的 Map

`engine.render(template, data)` 的 `data` 是任意 `Map<String, Object>`，引擎不要求事先定义 schema。在模板中，用 `{@…}` 引用：

```java
Map<String, Object> data = Map.of(
    "user", Map.of("name", "小红", "age", 19),
    "page", Map.of("size", "20", "num", 1)
);

engine.render("""
        {
            "name": "{@user.name}",
            "ageInt": "{@user.age}",
            "pageSize": "{= cast(@page.size, 'int') }"
        }
        """, data);
```

## 二、模板变量 `$name` 与 `$$name`

模板内部可以用 `$` 开头的 key **定义**变量，用 `{$name}` 引用：

```json
{
    "$title": "标题",
    "$greeting": "{@name} 你好",
    "out": "{$title} - {$greeting}"
}
```

### 两种定义的差别

| 形态 | 时机 | 多次引用 |
| :--- | :--- | :--- |
| `$name: value` | **惰性**：第一次 `{$name}` 引用时才渲染 `value` | 渲染一次，结果复用 |
| `$$name: value` | **立即**：定义时就先渲染 `value`，把结果存为变量 | 引用时直接返回已渲染结果 |

```json
{
    "$t1": "{= new Date() }",          // 惰性
    "$$t2": "{= new Date() }",          // 立即
    "a": "{$t1}",
    "b": "{$t1}",                       // a 与 b 是同一个 Date 实例（同一时刻）
    "c": "{$t2}",                       // 与 a 不同，$t2 在定义时就渲染过了
    "d": "{$t2}"
}
```

`$$` 适合"在定义时就执行逻辑（带副作用、记录日志、调用接口）"的场景。

### 集中定义

变量数量多时，可以用 `$` / `$$` 作为 key 集中定义：

```json
{
    "$": {
        "title": "标题",
        "color": "red"
    },
    "out": "{$title}: {$color}"
}
```

### 作用域

变量在**定义节点及其后代节点**有效；在子节点中重新定义同名变量会**遮蔽**父层变量：

```json
{
    "$level": "顶层",
    "sub": {
        "$level": "子层",
        "val": "{$level}"            // "子层"
    },
    "topVal": "{$level}"             // "顶层"
}
```

## 三、局部数据 `@` 与 `@@`

有时候需要在某个子树里"切换数据源"，例如从外部数据转向一个查询结果。用 `@` / `@@`：

| 形态 | 值是否走模板渲染 |
| :--- | :--- |
| `"@": value` | **是**：value 仍按模板渲染（可继续引用变量等） |
| `"@@": value` | **否**：value 当作原始数据 Map 用（更快） |

```json
{
    "name": "@",                       // 当前数据.name
    "sub": {
        "@": {
            "name": "小明"
        },
        "name": "@",                   // "小明"
        "id": "{@name}-001"            // 还能继续渲染子模板
    }
}
```

`@` 之后的 `@` 看到的是新数据；之前的 `@` 看到的是父数据。**已经在 `$enter{}` 之前解析过的变量不受 `@` 影响**。

## 四、`$enter{}` 与 `$return{}`

两个特殊的"钩子"key，用于在渲染前后插入任意 Groovy 逻辑：

### `$enter{}` 渲染前执行

```json
{
    "$enter{}": "{% _template.put('greeting', 'Hi ' + @name + '!') %}",
    "out": "{@greeting}"
}
```

要点：

- `_template` 是当前节点的 JSONObject（已被解析过，`$enter{}`/`$return{}` 等 key 已被剥离）。
- 可读写 `_template` 任意字段来干预后续渲染。
- 可引用变量、引用数据。

### `$return{}` 渲染后执行

```json
{
    "likes": "{@likes}",
    "$return{}": "{% _result.likes.removeIf(v -> StringUtils.isBlank(v)) %}"
}
```

要点：

- `_result` 是当前节点的最终 JSONObject。
- 可用于"清理空值"、"补字段"、"打日志"等收尾逻辑。
- **慎用**：滥用会让模板逻辑难追踪。

### 何时用 `$enter{}`/`$return{}` vs Groovy

| 场景 | 推荐 |
| :--- | :--- |
| 字段补全、归一化、清理 | `$return{}` |
| 根据运行时数据**修改模板结构**（如动态加字段） | `$enter{}` |
| 简单条件分支 | `{= ... ? : : }` |
| 简单循环 | `@each` 组件（见组件章节） |

## 五、JSON Object 键顺序

引擎在 JSONObject 中保留了**键的插入顺序**，与浏览器侧 JSON.stringify 一致：

```json
{
    "1": "一",
    "3": "三",
    "2": "二"
}
```

输出仍是 `{"1":"一","3":"三","2":"二"}`，不会按字母序重排。这在 `&include`、`*` 合并等场景中很重要。