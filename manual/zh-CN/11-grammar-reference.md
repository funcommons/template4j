# 11 语法参考

本章把模板语法**完整列出**，便于速查。每一项都注明章节链接深入阅读。

## 一、字符串中的占位符

| 形态 | 用途 | 详见 |
| :--- | :--- | :--- |
| `{@ path}` | JSONPath 取值（fastjson2） | [§ 02-1](02-syntax.md#1--path-数据表达式) |
| `{$ name}` | 引用模板变量 `$name` | [§ 02-2](02-syntax.md#2--name-变量表达式) |
| `{= expr}` | Groovy 单行表达式 | [§ 02-3](02-syntax.md#3--expr-行内表达式) |
| `{% script %}` | Groovy 多行脚本 | [§ 02-4](02-syntax.md#4--script-脚本块) |
| `<& … &>` | 字面文本，不渲染 | [§ 02-5](02-syntax.md#5----字面文本) |
| `{# … #}` | 同 `<&…&>`，备用写法 | — |

### `@` 数据的三种语法（脚本内）

| 写法 | 含义 |
| :--- | :--- |
| `@.x.y` | Java 字段访问，缺失抛 NPE |
| `@'jsonpath'` | JSONPath，缺失返回 null |
| `@('jsonpath')` | 同上，路径可由变量拼出 |

## 二、键的语法（按前缀/后缀）

### 前缀

| 形态 | 语义 | 详见 |
| :--- | :--- | :--- |
| `#xxx` | 注释，不渲染 | [§ 02 注释](02-syntax.md#注释) |
| `$xxx` | 变量定义（惰性求值） | [§ 03-二](03-data-and-vars.md#二模板变量-name-与-name) |
| `$$xxx` | 变量定义（立即求值） | [§ 03-二](03-data-and-vars.md#二模板变量-name-与-name) |
| `$xxx()` | 方法定义 | [§ 05](05-functions.md) |
| `$enter{}` | 渲染前钩子 | [§ 03-四](03-data-and-vars.md#四enter-与-return) |
| `$return{}` | 渲染后钩子 | [§ 03-四](03-data-and-vars.md#四enter-与-return) |
| `@` | 局部数据（值走模板） | [§ 03-三](03-data-and-vars.md#三局部数据--与-) |
| `@@` | 局部数据（值当字面） | [§ 03-三](03-data-and-vars.md#三局部数据--与-) |
| `@class` | 组件调度 | [§ 06](06-components.md) |
| `@class\|method` | 组件简写（select 在 value） | [§ 06-九](06-components.md#九组件简写规则) |
| `@class\|method:select` | 组件简写（单行文本） | [§ 06-九](06-components.md#九组件简写规则) |
| `*` | 合并键 | [§ 07-一](07-merge-and-include.md#一-合并键) |
| `&include` | 模板嵌套 | [§ 07-二](07-merge-and-include.md#二include-模板嵌套) |

### 后缀

| 形态 | 语义 | 详见 |
| :--- | :--- | :--- |
| `xxx%` | 值是裸脚本（`{% %}` 包裹） | [§ 02 脚本后缀](02-syntax.md#脚本后缀) |
| `xxx#` | 值原样，不渲染 | [§ 02 不渲染值](02-syntax.md#不渲染值) |
| `xxx&` | 同 `xxx#` | [§ 02 不渲染值](02-syntax.md#不渲染值) |
| `xxx@` | 同 `xxx#`（用于变量与组件） | — |

### 转义

| 形态 | 用途 |
| :--- | :--- |
| `<&#xxx&>` | 把形如 `#xxx` 的 key 当字面 key |
| `<&@xxx&>` | 把形如 `@xxx` 的 key 当字面 key（一般不需要，因为 `@` 仅在 `@class` 等少量语义下触发） |

## 三、内置组件

| name | 触发 | 主要参数 | 详见 |
| :--- | :--- | :--- | :--- |
| `if` | `@class: if` | `select`, `is`, `then`, `else` | [§ 06-二](06-components.md#二class-if--条件分支) |
| `each` | `@class: each` | `select`, `body`, `keyName`, `itemName`, `merge`, `nonnull` | [§ 06-三](06-components.md#三class-each--循环) |
| `switch` | `@class: switch` | `select`, 分支 key, `default` | [§ 06-四](06-components.md#四class-switch--分支表) |
| `invoke` | `@class: invoke` | `select`, `method`, `args` | [§ 06-五](06-components.md#五class-invoke--调用方法) |
| `render` | `@class: render` | `select`, `data` | [§ 06-六](06-components.md#六class-render--二次渲染) |
| `const` | `@class: const` | `select` | [§ 06-七](06-components.md#七class-const--不渲染) |
| `rest` | `@class: rest` | `url`/`select`, `method`, `headers`, `data`, `dataType`, `success`, `error` | [§ 06-八](06-components.md#八class-rest--http-客户端) |

## 四、脚本内置工具（`Func*`）

| 名称 | 类型 | 用途 | 详见 |
| :--- | :--- | :--- | :--- |
| `data` | FuncData | JSONPath 查询 / 数据根切换 | [§ 04-四](04-scripting.md#data) |
| `vars` | FuncVars | 读/写模板变量（支持 `vars.x = 1`） | [§ 04-四](04-scripting.md#vars) |
| `render` | FuncRender | 在当前上下文二次渲染模板 | [§ 04-四](04-scripting.md#render) |
| `load` | FuncLoad | 加载并解析 URL 资源（自动 `&include` 展开） | [§ 04-四](04-scripting.md#load) |
| `cast` | FuncCast | 类型转换（支持简写类型名） | [§ 04-四](04-scripting.md#cast) |
| `fne` | FuncFne | 取第一个非空值 | [§ 04-四](04-scripting.md#fne) |

## 五、引擎 API

```java
public interface RenderEngine {
    // 工厂
    static RenderEngine build();

    // 配置（返回新实例）
    RenderEngine withComponent(RenderComponent c);
    RenderEngine withBean(String name, Object bean);
    RenderEngine withBeans(Map<String, Object> beans);
    RenderEngine withImport(String className);
    RenderEngine withLoader(TemplateLoader loader);

    // 渲染
    Object render(Object template, Map<String, Object> data);
    Object render(Object template, String dataJsonOrYaml);
    Object render(String template, Map<String, Object> data);
    Object render(String template, String dataJsonOrYaml);

    // 便捷
    Object renderJson(String templateJson, Map<String, Object> data);
    Object renderJson(String templateJson, String dataJson);
    Object renderYaml(String templateYaml, Map<String, Object> data);
    Object renderYaml(String templateYaml, String dataYaml);

    // URL 加载
    Object renderUrl(String url, String resourceType, Map<String, Object> data);
    Object renderUrl(String url, Map<String, Object> data);
    Object renderJsonUrl(String url, Map<String, Object> data);
    Object renderYamlUrl(String url, Map<String, Object> data);
}
```

## 六、JSONPath 速查

完整语法见 fastjson2 文档。常用：

| 写法 | 含义 |
| :--- | :--- |
| `@` / `@$` / `@.$` | 根 |
| `@.x` / `@['x']` | 字段访问 |
| `@[0]` / `@[0,2]` / `@[1:3]` | 数组下标 |
| `@[*]` / `@.*` | 全部元素 |
| `@..x` | deepScan |
| `@.x[?(@.y > 1)]` | 过滤 |
| `@.x.size()` | 长度 |
| `@.x.upper()` | 函数调用 |
| `@.x[?(@ in ('a','b'))]` | IN 过滤 |

## 七、错误码

| 异常 | 触发 |
| :--- | :--- |
| `RenderException` | 任何渲染错误（带路径） |
| `IllegalArgumentException` | 组件必填字段缺失 |
| `StackOverflowError`（被包装） | `$name()` 递归调用（被 `locks` 阻止） |
| `IOException` / HTTP 异常 | `@rest` 网络错误，未提供 `error` 块时抛出 |

## 八、版本与依赖

| 依赖 | 用途 |
| :--- | :--- |
| JDK 17+ | 编译目标 |
| fastjson2 | JSON 解析、JSONPath |
| groovy-jsr223 | Groovy 脚本引擎 |
| jackson-dataformat-yaml | YAML 解析（配合 utils4j） |
| commons-text | `StringSubstitutor`（占位符扫描） |
| commons-lang3 | `StringUtils` 等内置类 |
| json-schema-validator (networknt) | data/result schema 校验（v0.5+） |
| commons-io | 资源读取 |
| utils4j | `UJson`、`UType`、`UString` 工具 |