# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this project is

`template4j-core` is a Java template engine that renders JSON / YAML / string templates against a data context. Templates are themselves JSON-shaped objects that mix literal data with embedded expressions, plus reusable components invoked via an `@class` key.

The core flow: `RenderEngine.build()` → `engine.render(template, data)` returns the rendered `Object` (often a `JSONObject` / `JSONArray`).

## Commands

Maven build (JDK 17 required):

```bash
mvn -q -DskipTests package          # compile + jar
mvn test                            # run all tests (JUnit 5, via maven-surefire-plugin 2.22.1)
mvn -Dtest=TemplateSyntaxTest test  # run a single test class
mvn -Dtest=TemplateSyntaxTest#testJsonPathSimpleField test   # run a single method
mvn -q compile                      # compile only
mvn -B verify                       # full check + JaCoCo gate (0.95 LINE / 0.85 BRANCH)
mvn -Pbenchmark -pl template4j-core test    # run perf benchmark
mvn -Pshade -pl template4j-core package -DskipTests   # build fat jar (target/template4j-core-shaded.jar)
```

There is no separate lint step; the build uses `maven-compiler-plugin` with JDK 17 source/target. Lombok is used in source — IDE annotation processing must be enabled for editing, but `mvn` compiles without extra config.

Tests live under `src/test/java/fun/commons/template4j/{core,utils}/`. The `core` package mirrors `main/.../core`.

Profiles: `-Pbenchmark` runs `PerformanceBenchmarkTest` (default skipped). `-Pshade` produces `template4j-core-shaded.jar` with `Cli` as main class (~16MB).

## Architecture

### Render pipeline (`JsonRenderer.convertSelf`)

`JsonRenderer.convertSelf(value, SelfObject)` is the single recursive entry point. It dispatches by runtime type:

- `String` → `TemplateUtil.parseStrTemplate` extracts placeholder expressions (`{@…}`, `{$…}`, `{=…}`, `{%…%}`, `<&…&>`), then evaluates each in reverse order so nested expressions resolve correctly.
- `Map` / `JSONObject` → applies a sequence of transformations in `JsonRenderer.convertSelf`: `convertKey` (strips `#`-prefix comment keys, handles `key%` raw-script and `key&` no-render suffixes), `convertVars` (processes special `@`, `$`, `$$`, `$name`, `$name()` keys), `escapeKey` (`<&…&>`-wrapped keys become literal), `simplify` (shorthand `@`, `$`, `@|type`, `$|type`, `@class…`). Then dispatches either to a `RenderComponent` (when `@class` is present) or recursively renders each field.
- `Collection` / `JSONArray` → recursively renders each element. A `*` key in a rendered Map causes its entries to be merged into the parent (`TemplateUtil.mergeAllMap`).
- other types → returned unchanged.

The `$enter{}` / `$return{}` keys run Groovy before/after the children of a Map are rendered; they read/write `_template` and `_result`.

### Expression evaluation (`JsonRenderer.expressionValue`)

Five placeholder forms in strings, decided by delimiter:

| Form | Meaning |
| --- | --- |
| `<& … &>` | literal text, not evaluated |
| `{@ path}` | JSONPath (`com.alibaba.fastjson2.JSONPath`) against `self.getData()` |
| `{$ name[.jsonpath]}` | lookup in `SelfObject.varMap`; trailing JSONPath applied to the result |
| `{= script }` | Groovy expression, result returned |
| `{% script %}` | Groovy script block (multi-line, must `return`) |

Groovy scripts run inside `RenderBindings` (extends `SimpleBindings`) which exposes `data`, `vars`, `render`, `load`, `cast`, `fne`, plus any beans registered via `engine.withBean(name, bean)` and any classes added via `engine.withImport(fqn)`. `@` inside Groovy is rewritten to `data.` by `ScriptUtil.codeReplaceAt` before evaluation (with quoted-string and comment regions preserved).

The default Groovy imports (`GroovyImports`) cover `java.*`, `com.alibaba.fastjson2.*`, and `org.apache.commons.lang3.*` — so `JSONObject`, `StringUtils`, etc. are usable without an explicit import.

### `SelfObject` (render context)

Each recursive descent constructs a child `SelfObject` that carries: a reference to the renderer, the data map, a parent link (used to walk up for variable lookup via `vars()`), a `varMap` (`LinkedHashMap`) for local variables, a `name` used to build the `/`-joined error path, and an inherited `locks` list used by `FuncObject` to prevent infinite recursion in user-defined methods.

Variable scoping: `$name` and `$name()` entries live in the current `SelfObject`'s `varMap`. `$name()` is wrapped in `FuncObject` (locked per call). `$$` entries are *rendered first* then put into vars — distinct from `$` which is taken as-is.

### Component system (`RenderComponent`)

Built-in components are registered in `JsonRenderer`'s constructor and discovered by `name()` matching the template's `@class`:

- `if` (`IfRenderer`) — `select` evaluated, then `then` / `else` / `true` / `false` chosen; `is` / `method` controls the predicate (`true`, `!true`, `false`, `!false`, `null`, `!null`, `blank`, `!blank`, `empty`, `!empty`).
- `each` (`EachRenderer`) — iterates `select` (array or map), renders `body` per item with `$key` / `$item` (configurable via `keyName` / `itemName`); `merge: true` flattens list bodies; `nonnull: true` drops null results.
- `switch` (`SwitchRenderer`) — string-coerces `select`, looks it up as a sibling key under `cases`, falls back to `default`.
- `invoke` (`InvokeRenderer`) — calls `select.method(args)`; `select` may be a `Bean` (registered via `withBean`) or any expression evaluating to a callable.
- `render` (`RenderRenderer`) — recursively renders `select` with optional `data`; useful for late-binding.
- `const` (`ConstRenderer`) — returns `select` without rendering (use `key&` suffix for inline).
- `rest` (`RestRenderer`) — HTTP via JDK `java.net.http.HttpClient`; `url` / `method` / `headers` / `data` plus optional `timeout` / `retry` / `cache` / `auth` / `circuitBreaker`; on success renders optional `success` with `$responseData`; on failure renders optional `error`.
- `parallel` (`ParallelRenderer`) — concurrent sub-template execution; `tasks` map + `body` aggregating results, with `timeout` (overall deadline) and `continueOnError` fallback.
- `try` (`TryRenderer`) — error isolation wrapper; `select` + optional `fallback` / `onError` / `rethrow`.

### Engine APIs (v0.5+)

- `engine.compile(template)` / `compileJson` / `compileYaml` → `RenderEngine.Template` (thread-safe, reusable). Internal `parseStrTemplate` LRU cache (1024 entries, `template4j.parseCache.maxEntries`).
- `engine.withTraceConsumer(Consumer<TraceEvent>)` — render / REST / script / component event stream. Zero overhead when no consumer registered.
- `engine.withInputSchema(schemaContent)` / `withOutputSchema(schemaContent)` — fail-fast JSON Schema validation via networknt.

### Entry points (v0.5+)

- `fun.commons.template4j.core.Cli` — CLI main. Subcommands: `render` / `watch` / `mcp` / `version` / `help`. Manifest main class in shaded jar.
- `fun.commons.template4j.core.McpServer` — MCP stdio JSON-RPC server exposing `render_template` tool. Reachable via `Cli mcp` or `McpServer.main`.

To add a component: implement `RenderComponent`, expose `name()` and `render(RenderContext)`, then `engine.withComponent(new MyRenderer())`. `RenderContext` gives `render` / `renderField` helpers, `getValue` / `containsField` / `assertField` / `assertFieldOne`, and access to the current `SelfObject`.

### Resource loading (`TemplateLoader` / `LoaderUtil`)

`DefaultTemplateLoader` reads classpath resources (UTF-8). `LoaderUtil.loadTemplate` infers format from the URL extension (`.json/.json5/.jt/.japi/.jpage` → JSON, `.yml/.yaml/.yt/.yapi/.ypage` → YAML, otherwise tries JSON then YAML then raw text). `&include` as a key (object form) or `&include:relative/url` as a string value pulls in another resource and merges it before rendering. Entry points: `engine.renderUrl(url, resourceType, data)` plus the `renderJsonUrl` / `renderYamlUrl` defaults.

### Key files

- `RenderEngine.java` — public interface and `build()` factory; most overloads live here as defaults (`renderJson`, `renderYaml`, `renderUrl`, `withBeans`, etc.).
- `JsonRenderer.java` — package-private implementation; the place to look for any control-flow question.
- `TemplateUtil.java` — string and key parsing (`parseStrTemplate`, `convertKey`, `convertVars`, `simplify`, `escapeKey`, `mergeAllMap`).
- `ScriptUtil.java` — `codeReplaceAt` (the `@` → `data` rewrite that respects `'…'` / `"…"` / `/* */` / `//` regions) and `getProgramBesieged` (wraps multi-line `{% … %}` blocks).
- `SelfObject.java` — per-node render state, `varMap`, path tracking, recursion lock.
- `RenderBindings.java` — JSR-223 bindings; routes `$foo` lookups into the `FuncVars` bean so Groovy sees them as identifiers.
- `LoaderUtil.java` — resource loading + `&include` expansion.
- `beans/Func*.java` — the objects exposed inside Groovy scripts (`FuncData`, `FuncVars`, `FuncRender`, `FuncLoad`, `FuncCast`, `FuncFne`).
- `components/*.java` — one file per built-in component listed above.

### Dependencies worth knowing

- `fastjson2` for JSON + `JSONPath` (this is why `@` expressions use fastjson syntax, not Jayway JsonPath).
- `groovy-jsr223` for in-template scripting (`ScriptEngineManager().getEngineByName("groovy")`).
- `commons-text` for `StringSubstitutor` (placeholder extraction in `TemplateUtil.parseStrTemplate`).
- `json-schema-validator` (networknt) for `engine.withInputSchema` / `withOutputSchema`.
- `RestRenderer` uses JDK built-in `java.net.http.HttpClient` (no Spring); `ParallelRenderer` uses a daemon `FixedThreadPool` (size 16, `template4j.parallel.poolSize`).
- `jackson-dataformat-yaml` + `fun.commons:utils4j` for YAML parsing (`UJson.JSONFromYaml`).
- `commons-lang3`, `commons-io`, Lombok.