# 07 合并与包含

两个让模板结构更整洁的关键字：`*`（合并）与 `&include`（嵌套模板）。

## 一、`*` 合并键

把渲染后的某个节点的字段**合并进当前对象**，相当于 ES6 的对象展开 `{...obj}`。

### 基本语义

```json
{
    "a": "A",
    "*": {
        "b": "B",
        "c": "C"
    },
    "d": "D"
}
```

合并后：

```json
{ "a": "A", "b": "B", "c": "C", "d": "D" }
```

### 同名字段的优先级

```json
{
    "x": "1",
    "*": { "x": "2", "y": "2" },
    "x": "3"
}
```

合并顺序：

1. `*` 之前的字段先放；
2. `*` 内的字段**覆盖**同名；
3. `*` 之后的字段**再次覆盖**同名。

最终：`{"x":"3","y":"2"}`。

### `*` 是数组

```json
{
    "*": [
        { "a": "A1" },
        {
            "@class": "each",
            "select": "{@xs}",
            "body": { "b": "{@item}" }
        }
    ]
}
```

数组中**每个 Map** 都会被合并进父对象。`@each` 渲出来是 List<Map> 时，每个 Map 也合并。

## 二、`&include` 模板嵌套

```json
{
    "demo1": "{@name}",
    "&include": "./partial.json"
}
```

加载 `partial.json` 并合并进来。**先静态合并再渲染**——`&include` 不在运行时执行。

### 字符串 vs 数组

```json
// 字符串
{
    "&include": "./base.json"
}

// 字符串数组：按顺序依次 include
{
    "&include": ["./base.json", "./extras.json"]
}
```

### 同名字段

后 include 的覆盖先 include 的。

### 相对路径

`&include` 解析的基准是**当前资源 URL**。如果在 `renderUrl` 中加载 `classpath:/tpl/a.json`，而 `a.json` 里写 `"&include": "./b.json"`，则解析为 `classpath:/tpl/b.json`。

`LoaderUtil.relativeUrl` 用于计算相对 URL；URL 拼接遵循标准 URI 规则。

### `&include: ...` 在字符串中

模板里某个**字符串值**也可以以 `&include:` 开头，等价于在该位置嵌入对应模板的内容：

```json
{
    "title": "&include:./title.json",
    "body": "正文"
}
```

## 三、典型组合

### 1. 公共片段

`partials/header.json`:

```json
{
    "status": 0,
    "traceId": "{= UUID.randomUUID().toString() }"
}
```

`page.json`:

```json
{
    "&include": "./partials/header.json",
    "data": { ... }
}
```

### 2. 配合 `*` 做"条件合并"

```json
{
    "code": 0,
    "msg": "success",
    "*": "{= @result != null ? { 'data': @result } : [:] }"
}
```

`$result` 存在时多一个 `data` 字段，否则不增加。

### 3. 组件嵌套

```json
{
    "@class": "each",
    "select": "{@rows}",
    "body": {
        "user": "{@item.name}",
        "&include": "./user-extras.json"
    }
}
```

每个迭代结果都自动 include 公共字段。