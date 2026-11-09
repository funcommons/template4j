# 12 命令行工具 (CLI, v0.5+)

template4j-core 自带轻量 CLI，零依赖（仅 JDK + core）。入口类 `fun.commons.template4j.core.Cli`。

## 用法

```bash
# 模板 + 内联 JSON 数据
java -cp template4j-core.jar fun.commons.template4j.core.Cli render template.jt \
    --data '{"id":123, "name":"alice"}'

# 模板 + 数据文件
java -cp template4j-core.jar fun.commons.template4j.core.Cli render template.jt \
    --data-file data.json

# YAML 模板
java -cp template4j-core.jar fun.commons.template4j.core.Cli render template.jt \
    --data-file data.yaml --yaml

# 美化输出
java -cp template4j-core.jar fun.commons.template4j.core.Cli render template.jt \
    --data-file data.json --pretty

# 元信息
java -cp template4j-core.jar fun.commons.template4j.core.Cli version
java -cp template4j.core.Cli help
```

## 命令

| 命令 | 说明 |
|---|---|
| `render <template-file>` | 用给定数据渲染模板 |
| `version` | 打印版本号 |
| `help` | 打印帮助 |

## `render` 选项

| 选项 | 说明 |
|---|---|
| `--data '<json>'` | 内联 JSON 数据字符串 |
| `--data-file <path>` | 数据文件（`.json` / `.yml` / `.yaml`） |
| `--yaml` | 模板是 YAML（默认 JSON） |
| `--pretty` | 美化输出 |

## 打包

`template4j-core.jar` 默认是 thin jar，依赖 fastjson2 / groovy / commons-text / jackson-yaml / networknt 等传递依赖。生产使用有两种方式：

### 方式 1: thin jar + classpath

把所有传递依赖放到 classpath 上（`mvn dependency:copy-dependencies` 或 IDE 导出）。然后：

```bash
java -cp "template4j-core.jar:libs/*" fun.commons.template4j.core.Cli render template.jt --data-file data.json
```

### 方式 2: shaded fat jar (推荐)

启用 `-Pshade` profile 打 fat jar：

```bash
mvn -Pshade -pl template4j-core package -DskipTests
# 产物: template4j-core/target/template4j-core-shaded.jar (~16 MB)
```

`pom.xml` 配置：

```xml
<plugin>
    <artifactId>maven-shade-plugin</artifactId>
    <executions>
        <execution>
            <id>shade-cli</id>
            <phase>package</phase>
            <goals><goal>shade</goal></goals>
            <configuration>
                <finalName>template4j-core-shaded</finalName>
                <outputFile>target/template4j-core-shaded.jar</outputFile>
                <transformers>
                    <transformer implementation="org.apache.maven.plugins.shade.resource.ManifestResourceTransformer">
                        <mainClass>fun.commons.template4j.core.Cli</mainClass>
                    </transformer>
                </transformers>
            </configuration>
        </execution>
    </executions>
</plugin>
```

打包后可直接：

```bash
java -jar template4j-core-shaded.jar render template.jt --data-file data.json
```

MCP server 入口换成 `mcp` 子命令即可：

```bash
# thin jar (需要 classpath 完整)
java -cp template4j-core.jar fun.commons.template4j.core.Cli mcp
# 或直接调用 McpServer.main
java -cp template4j-core.jar fun.commons.template4j.core.McpServer

# fat jar (推荐)
java -jar template4j-core-shaded.jar mcp
```

Claude Desktop 配置示例：

```json
{
  "mcpServers": {
    "template4j": {
      "command": "java",
      "args": ["-jar", "/path/to/template4j-core-shaded.jar", "mcp"]
    }
  }
}
```

## CI 集成

`template4j render` 退出码：

- `0`：渲染成功
- `1`：参数错误 / 模板缺失等业务错误
- `2`：异常退出（Stacktrace 输出到 stderr）

可在 CI 中加 `validate` 子命令的占位（后续版本提供 schema 批量校验）。当前可用：

```bash
template4j render template.jt --data-file example-data.json \
    && echo "OK" || echo "FAIL"
```
