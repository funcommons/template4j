# 02 · REST API 规范

> 对齐 mc-api-spec v1.6：HTTP 200 业务码 + 6 字段信封 + snake_case + Long→String + Idempotency-Key + X-Trace-Id。

## 1. 响应信封

```jsonc
{
  "code": 0,                  // 0=成功，其它=业务错误
  "message": "OK",
  "data": <any>,              // 成功的载荷
  "error": null,              // 失败时的子项数组（field/code/message/value）
  "trace_id": "abc123def456", // 32 位 hex
  "timestamp": 1735900800000  // Unix ms
}
```

错误码枚举：

| code | 含义 | 触发场景 |
|------|------|---------|
| 0 | 成功 | — |
| 10001 | 系统繁忙 | 未捕获异常 |
| 10300 | 模板渲染失败 | 渲染过程抛 RenderException |
| 10301 | 模板格式无法解析 | JSON/YAML 解析失败 |
| 10404 | 学习示例不存在 | /api/learn/{id} 找不到 |

## 2. 端点清单

### 2.1 渲染：POST /api/render

通用渲染入口，接受 JSON 或 YAML 模板字符串。

**请求体**：

```jsonc
{
  "template_type": "json",          // 必填，json | yaml
  "template": "{ \"msg\": \"{@name}\" }",
  "data": { "name": "template4j" }, // 选填，默认 {}
  "data_format": "json"              // 选填，json | yaml，默认 json
}
```

**cURL 示例**：

```bash
curl -X POST http://localhost:8088/api/render \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: 4f6e8a1c-2b3d-4e5f-8a9b-7c6d5e4f3a2b' \
  -d '{
    "template_type": "json",
    "template": "{ \"greeting\": \"Hello, {@user.name}!\" }",
    "data": { "user": { "name": "template4j" } }
  }'
```

**响应**：

```jsonc
{
  "code": 0,
  "message": "OK",
  "data": {
    "rendered": { "greeting": "Hello, template4j!" },
    "rendered_text": "{\"greeting\":\"Hello, template4j!\"}",
    "timing_ms": 8
  },
  "trace_id": "abc123def456",
  "timestamp": 1735900800000
}
```

### 2.2 示例列表：GET /api/learn

返回所有学习示例的元信息。

**响应**：

```jsonc
{
  "code": 0,
  "data": {
    "examples": [
      {
        "id": "01-syntax-data",
        "title": "数据路径 {@...}",
        "category": "syntax",
        "difficulty": 1,
        "tags": ["{@}","jsonpath"],
        "summary": "演示 @ 表达式从 data 中取值"
      },
      // ...
    ],
    "total": 8
  }
}
```

### 2.3 示例详情：GET /api/learn/{id}

返回完整示例：源模板 + 数据 + 说明 + 上一个/下一个 ID。

**响应**：

```jsonc
{
  "code": 0,
  "data": {
    "id": "01-syntax-data",
    "title": "数据路径 {@...}",
    "category": "syntax",
    "difficulty": 1,
    "tags": ["{@}","jsonpath"],
    "summary": "演示 @ 表达式从 data 中取值",
    "description": "## 数据路径\n`{@path}` 是 JSONPath 表达式...\n",
    "template_type": "json",
    "template": "{ \"msg\": \"{@user.name}\" }",
    "data": { "user": { "name": "template4j" } },
    "expected": { "msg": "template4j" },
    "expected_text": "{\"msg\":\"template4j\"}",
    "prev_id": null,
    "next_id": "02-syntax-vars"
  }
}
```

### 2.4 playground：POST /api/try

在线试用入口，比 /api/render 多返回 trace 和 timing。

**请求体**：

```jsonc
{
  "template_type": "json",
  "template": "{ ... }",
  "data": { ... },
  "options": {
    "with_trace": true,
    "with_timing": true
  }
}
```

**响应**：

```jsonc
{
  "code": 0,
  "data": {
    "rendered": { ... },
    "rendered_text": "...",
    "timing_ms": 12,
    "trace": [
      { "step": "parse_template", "detail": "json parsed: 1 key" },
      { "step": "render_root",    "detail": "dispatch map" },
      { "step": "render_string",  "detail": "expression: {@user.name}" },
      { "step": "eval_jsonpath",  "detail": "user.name -> 'template4j'" }
    ]
  }
}
```

> trace 是 best-effort：当前实现通过在 TemplateService 中包装 RenderEngine 实现，未来可改为 ASPECT 切入。

### 2.5 引擎元信息：GET /api/engine

返回引擎能力，供前端 UI 展示。

**响应**：

```jsonc
{
  "code": 0,
  "data": {
    "version": "0.4.0",
    "components": ["rest","invoke","if","each","switch","const","render"],
    "expression_forms": ["<&...&>","{#...#}","{@...}","{$...}","{=...}","{%...%}"],
    "default_imports": [
      "java.math.*","java.text.*","java.io.*","java.net.*","java.util.*",
      "java.util.regex.*","com.alibaba.fastjson2.*","org.apache.commons.lang3.*",
      "org.apache.commons.lang3.time.*"
    ],
    "registered_beans": ["time","math","text"]
  }
}
```

## 3. 公共 Header 约定

| Header | 方向 | 必填 | 说明 |
|--------|------|------|------|
| `Content-Type: application/json` | 请求 | 写操作必填 | — |
| `X-Trace-Id` | 双向 | 选填 | 客户端可指定，未指定由后端生成；响应回传 |
| `Idempotency-Key` | 请求 | 写操作必填 | UUID v4，幂等键 |
| `X-Response-Timing` | 响应 | 选填 | 服务端处理耗时（ms） |

## 4. 错误示例

```jsonc
// 模板语法错误
HTTP 200
{
  "code": 10301,
  "message": "模板格式无法解析: Unexpected character at line 3 col 5",
  "data": null,
  "error": [
    { "field": "template", "code": "FORMAT_INVALID", "message": "JSON parse failed", "value": null }
  ],
  "trace_id": "abc123def456",
  "timestamp": 1735900800000
}

// 模板渲染错误
HTTP 200
{
  "code": 10300,
  "message": "template path:/out/% script error by {= $undefined } : No such property: $undefined",
  "data": null,
  "error": null,
  "trace_id": "abc123def456",
  "timestamp": 1735900800000
}
```

## 5. 限流（可选）

demo 默认不启用限流。生产可在网关层加：

- 按 IP 限流：100 req/min
- 按 Idempotency-Key 去重：5 min 内重复提交返回首次结果
- 超限响应 `HTTP 429` + `Retry-After: 60` + `body.code=10500`

## 6. 版本与兼容

- API 版本通过 URL 前缀 `/api` 隐含（当前为 v1）
- 引入破坏性变更时新增 `/api/v2`
- 向后兼容期：6 个月
