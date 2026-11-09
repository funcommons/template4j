# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this project is

`template4j-core` is a Java template engine that renders JSON / YAML / string templates against a data context. Templates are themselves JSON-shaped objects that mix literal data with embedded expressions, plus reusable components invoked via an `@class` key.

The core flow: `RenderEngine.build()` → `engine.render(template, data)` returns the rendered `Object` (often a `JSONObject` / `JSONArray`).

## Commands

Maven build (JDK 17 required):

```bash
mvn -q -DskipTests package          # compile + jar
mvn test                            # run all tests (JUnit 5, via maven-surefire-plugin 3.2.5)
mvn -Dtest=TemplateSyntaxTest test  # run a single test class
mvn -Dtest=TemplateSyntaxTest#testJsonPathSimpleField test   # run a single method
mvn -q compile                      # compile only
mvn -B verify                       # full check + JaCoCo gate (0.95 LINE / 0.85 BRANCH)
mvn -Pbenchmark -pl template4j-core test    # run perf benchmark
mvn -Pshade -pl template4j-core package -DskipTests   # build fat jar (target/template4j-core-shaded.jar)
```

There is no separate lint step; the build uses `maven-compiler-plugin` with JDK 17 source/target (enforcer `requireJavaVersion [17,)` — JDK 17+ builds, including 21 and later, so virtual threads and other newer-JDK runtime features are available). Lombok is used in source — IDE annotation processing must be enabled for editing, but `mvn` compiles without extra config.

Tests live under `src/test/java/fun/commons/template4j/{core,utils,arch}/`. The `core` package mirrors `main/.../core`. Core surefire sets `template4j.rest.allowed-hosts=127.0.0.1,localhost,0.0.0.0` as a system property so unit tests that spin up `com.sun.net.httpserver.HttpServer` on loopback pass the RestRenderer SSRF guard.

CI workflows: `.github/workflows/security-scan.yml` runs OWASP dependency-check (`-DfailBuildOnCVSS=9`) plus a Trivy filesystem scan.

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
- `rest` (`RestRenderer`) — HTTP via JDK `java.net.http.HttpClient`; `url` / `method` / `headers` / `data` plus optional `timeout` / `retry` / `cache` / `auth` / `circuitBreaker`; on success renders optional `success` with `$responseData`; on failure renders optional `error`. Relative `url`s (leading `/`) are prefixed with the base url (`RestRenderer.setBaseUrl`, default from system property `template4j.rest.baseUrl`). SSRF guard `isBlockedHost` is fail-closed — anything not whitelisted via `template4j.rest.allowed-hosts` (comma-separated) or per-instance `RestOptions.allowedHosts` is rejected; response bodies are capped at `template4j.rest.maxResponseBytes` (default 2MB). Instance config flows through `RestOptions` (baseUrl / allowedHosts / maxResponseBytes): the `effective*` merge functions are pure (instance value wins when set, else static default) and are wired into the render flow via `engine.withRestOptions(RestOptions)` — `JsonRenderer` forwards to `RestRenderer.setRestOptions` through `ComponentRegistry.find("rest")`; `null` clears back to static defaults. The rest response `CACHE` is a Caffeine LRU (replacing the handwritten `synchronizedMap`; `template4j.rest.cache.maxEntries`, default 1024), and `circuitBreaker` uses `resilience4j-circuitbreaker` (`CircuitBreakerRegistry`), not the old handwritten state machine.
- `parallel` (`ParallelRenderer`) — concurrent sub-template execution; `tasks` map + `body` aggregating results, with `timeout` (overall deadline) and `continueOnError` fallback.
- `try` (`TryRenderer`) — error isolation wrapper; `select` + optional `fallback` / `onError` / `rethrow`.

### Engine APIs (v0.5+)

- `engine.compile(template)` / `compileJson` / `compileYaml` → `RenderEngine.Template` (thread-safe, reusable). Internal `parseStrTemplate` LRU cache (1024 entries, `template4j.parseCache.maxEntries`).
- Trace: `engine.addTraceConsumer(Consumer<TraceEvent>) → TraceHandle` — append-style registration returning a deregisterable handle (`handle.close()` / `remove()`). Streaming callers (SSE / per-request subscriptions) MUST close the handle or the consumer leaks on the shared engine. `engine.withTraceConsumer(...)` is kept for compatibility but is *permanent* (no handle) — prefer `addTraceConsumer` for anything scoped. Zero overhead when no consumer registered.
- `engine.render(template, data, RenderOptions)` — per-request options via `RenderOptions.builder()`: `traceConsumer` (auto-removed after the render) and `timeoutMs` (`0` = unbounded; on expiry throws `RenderException` whose message contains `timed out after Nms`). Does not mutate engine-global state; `null` options equals plain `render`.
- `engine.withSandbox(boolean)` — Groovy sandbox, default **off** (off = behavior identical to before). When on, `GroovySandbox` runs `{= }` / `{% %}` through `org.kohsuke:groovy-sandbox` (`SandboxTransformer` rewrites calls into `Checker.checked*`) plus `SecureASTCustomizer` rejecting all loop statements at compile time (loops can't be caught at runtime). The `Whitelist` interceptor deny-by-default blocks `File` / `URL` / `Runtime` / `System.exit` / `Class.forName` / `Eval`; allowed receivers are the `Func*` beans, fastjson2 containers, `Map`/`List`, `Math`, `StringUtils`. Violations throw `RenderException` with `sandbox` in the message. Two gotchas: groovy-sandbox 1.19's POM pulls transitive groovy 2.4.7 (excluded — the sandbox actually runs on the project's Groovy 4), and its interceptor registry is a `ThreadLocal`, so the interceptor is registered/unregistered around *every* `eval` (not once at build) or worker threads would bypass it entirely. Note the sandbox path recompiles per eval (no `Compilable` cache) — the script cache below applies to the non-sandboxed path.
- Script cache (`ScriptEnvironment`) — compiled Groovy scripts are cached in a Caffeine LRU keyed by imports fingerprint + script. `template4j.scriptCache.maxEntries` (default 2000) and `template4j.scriptCache.enabled` (default true) are read at engine build time, so they are per-engine-instance.
- `engine.withExecutor(ExecutorService)` — engine-level executor for `@class: parallel` tasks only (the synchronous render path never goes through it). Per-instance, so engines are isolated from each other: `ParallelRenderer` resolves it via `context.renderer instanceof JsonRenderer` → `executorForParallel()` (any non-JsonRenderer renderer falls back to the built-in pool). `null` restores the built-in daemon pool; the engine never shuts it down — caller owns its lifecycle.
- `engine.withInputSchema(schemaContent)` / `withOutputSchema(schemaContent)` — fail-fast JSON Schema validation via networknt.

### Entry points (v0.5+)

- `fun.commons.template4j.core.Cli` — CLI main. Subcommands: `render` / `watch` / `mcp` / `version` / `help`. Manifest main class in shaded jar.
- `fun.commons.template4j.core.McpServer` — MCP stdio JSON-RPC server exposing `render_template` tool. Reachable via `Cli mcp` or `McpServer.main`.

To add a component: implement `RenderComponent`, expose `name()` and `render(RenderContext)`, then `engine.withComponent(new MyRenderer())`. `RenderContext` gives `render` / `renderField` helpers, `getValue` / `containsField` / `assertField` / `assertFieldOne`, and access to the current `SelfObject`.

### Resource loading (`TemplateLoader` / `LoaderUtil`)

`DefaultTemplateLoader` reads classpath resources (UTF-8). `LoaderUtil.loadTemplate` infers format from the URL extension (`.json/.json5/.jt/.japi/.jpage` → JSON, `.yml/.yaml/.yt/.yapi/.ypage` → YAML, otherwise tries JSON then YAML then raw text). `&include` as a key (object form) or `&include:relative/url` as a string value pulls in another resource and merges it before rendering. Entry points: `engine.renderUrl(url, resourceType, data)` plus the `renderJsonUrl` / `renderYamlUrl` defaults.

### template4j-site (demo web app)

Spring Boot app under `template4j-site/` (`fun.commons.template4j.site`) exposing the engine over HTTP plus demo fixtures:

- `MockController` — mock REST targets for the `rest` component: `GET /users/{id}` + `GET /api/user`, `GET /users/{id}/orders` + `GET /api/orders`, and `GET /flaky` which always returns 500 (circuit-breaker / retry demos).
- `RateLimitFilter` — bucket4j token bucket per client on `/api/**` **write methods only** (POST/PUT/PATCH/DELETE); configured via `template4j.demo.rate-limit.enabled` / `capacity` / `refill-minutes`; exhausted requests get 429 + `Retry-After` + a JSON error body.
- `SecurityHeadersFilter` — adds `X-Content-Type-Options`, `X-Frame-Options: DENY`, `Referrer-Policy`, and a tightened default `Content-Security-Policy`; a request attribute `SKIP="true"` opts a handler out.
- `RenderMetrics` (micrometer) — `template4j.render.total{type}` counter, `template4j.render.duration` timer, `template4j.script.cache.size` gauge (registered as a constant placeholder; the engine's live cache size is not yet plumbed through).
- `StreamController` — SSE endpoints validate the template against `template4j.demo.max-template-size` (default 64KB) before rendering.

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
- `org.kohsuke:groovy-sandbox` 1.19 for `withSandbox(true)` — its POM transitively drags in groovy 2.4.7 (and junit), both excluded in `template4j-core/pom.xml`; the sandbox runs on the project's Groovy 4.
- `com.github.ben-manes.caffeine:caffeine` 3.1.8 — `ScriptEnvironment` compiled-script LRU (and the replacement target for the handwritten LRU caches).
- `com.tngtech.archunit:archunit-junit5` 1.3.0 (test scope) — `ArchitectureGuardTest` layering rules (core must not depend on site/spring).
- `commons-text` for `StringSubstitutor` (placeholder extraction in `TemplateUtil.parseStrTemplate`).
- `json-schema-validator` (networknt) for `engine.withInputSchema` / `withOutputSchema`.
- `io.github.resilience4j:resilience4j-circuitbreaker` 2.2.0 — the rest component's `circuitBreaker` (`CircuitBreakerRegistry`); transitive deps are only `resilience4j-core` + `slf4j-api`.
- `RestRenderer` uses JDK built-in `java.net.http.HttpClient` (no Spring); `ParallelRenderer` uses a daemon `FixedThreadPool` (size 16, `template4j.parallel.poolSize`).
- `jackson-dataformat-yaml` + `fun.commons:utils4j` for YAML parsing (`UJson.JSONFromYaml`).
- `commons-lang3`, `commons-io`, Lombok.