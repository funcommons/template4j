# 08 资源加载

当模板需要从文件、classpath、网络加载时，使用 `renderUrl` 系列方法。本章讲 URL 解析、格式推断与自定义加载器。

## 一、API 速查

```java
Object renderUrl(String url, String resourceType, Map<String, Object> data);
Object renderUrl(String url, Map<String, Object> data);                    // resourceType=null, 按扩展名推断

Object renderJsonUrl(String url, Map<String, Object> data);                // 强制 JSON
Object renderYamlUrl(String url, Map<String, Object> data);                // 强制 YAML
```

入参可以是 `String`（自动解析 JSON / YAML）或 `Map<String, Object>`。

## 二、URL 形式

支持的 URL scheme 由 `TemplateLoader` 决定。`DefaultTemplateLoader` 默认支持：

- `classpath:/foo/bar.json`（也可省略 `classpath:` 前缀）
- 任何 `ClassLoader.getResource` 可见的路径
- HTTP URL（如 `https://...`）—— 只要 `TemplateLoader.load(url)` 能返回字符串

`classpath:` 前缀和开头的 `/` 会被剥离。

## 三、格式推断

`resourceType` 为 `null` 时按 URL 后缀判断：

| 后缀 | 解析为 |
| :--- | :--- |
| `.json`、`.json5`、`.jt`、`.japi`、`.jpage` | JSON |
| `.yml`、`.yaml`、`.yt`、`.yapi`、`.ypage` | YAML |
| 其它 / 无后缀 | 先尝试 JSON，再尝试 YAML，最后保留原始字符串 |

也可以**显式**指定 `resourceType="json"` / `"yaml"` 跳过推断。

## 四、自定义 `TemplateLoader`

需要从数据库、加密文件、私有协议加载时，实现 `TemplateLoader` 接口：

```java
public interface TemplateLoader {
    String load(String url) throws Exception;

    // 默认实现：把 load 的字符串解析为 JSONObject
    default JSONObject loadJson(String url) throws Exception {
        return JSONObject.parse(load(url));
    }

    // 默认实现：把 load 的字符串解析为 YAML（fastjson2 / utils4j）
    default JSONObject loadYaml(String url) throws Exception {
        return UJson.JSONFromYaml(load(url));
    }
}
```

注册到引擎：

```java
RenderEngine engine = RenderEngine.build().withLoader(new TemplateLoader() {
    @Override
    public String load(String url) {
        if (url.startsWith("db://")) {
            return templateRepo.findById(url.substring("db://".length()));
        }
        return new DefaultTemplateLoader().load(url);
    }
});

engine.renderUrl("db://welcome", data);
```

`load` 只负责"取字符串"，格式解析走引擎默认实现；要换 JSON / YAML 解析器才需要重写 `loadJson` / `loadYaml`。

## 五、`&include` 与 `renderUrl`

`renderUrl` 加载的模板里如果出现 `&include:...`，**URL 相对当前资源**解析。这是分模块组织模板的标准做法：

```
templates/
├── page.json         # &include: ./partials/header.json
├── partials/
│   ├── header.json
│   └── footer.json
└── list/
    └── list-item.json
```

调用 `engine.renderUrl("classpath:/templates/page.json", data)`，引擎自动从 `partials/` 目录加载。

## 六、在脚本里加载：`load(...)`

脚本中也提供 `load(url)`，效果与 `renderUrl` 相同，但用**当前 SelfObject 的资源 URL** 作为基准：

```groovy
JSONObject profile = load('./profile.json');   // 相对当前模板路径
```

适合"加载一份配置 JSON 用作数据"。注意返回值会经过 `&include` 展开。