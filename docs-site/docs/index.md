---
layout: home

hero:
  name: template4j
  text: JSON / YAML 声明式模板引擎
  tagline: 9 个内置组件覆盖条件、循环、分支、REST 编排、并行聚合、错误隔离 —— BFF / API 编排全要素
  actions:
    - theme: brand
      text: 快速开始
      link: /guide/
    - theme: alt
      text: 组件总览
      link: /components
    - theme: alt
      text: GitHub
      link: https://github.com/funcommons/template4j

features:
  - icon: 🧩
    title: 声明式 API 编排
    details: "@class: rest 内建 timeout / retry / cache / auth / circuitBreaker；parallel 整体 deadline 并发聚合；try 错误隔离降级。一个 JSON 模板就是一个 BFF 接口。"
  - icon: ⚡
    title: Groovy 脚本无缝融合
    details: "{= expr } 单行、{% block %} 块级，默认导入开箱即用；沙箱模式白名单拦截 + 循环禁令 + 编译产物缓存，安全开关不再意味着性能悬崖。"
  - icon: 🪶
    title: 零依赖核心
    details: 运行时零 Spring：JDK HttpClient + fastjson2 + caffeine。shaded fat jar 单文件部署，自带 CLI（render / watch / mcp）与 MCP Server，供 AI Agent 直连。
  - icon: 🛡️
    title: 生产级安全
    details: SSRF 白名单 fail-closed、响应体积上限、演示站限流过滤器；依赖漏洞清零（Dependabot 告警 21 → 0），CodeQL / Trivy / OWASP 门禁常驻 CI。
  - icon: 🌊
    title: 流式与可观测
    details: RenderStream 推/拉双模式，SSE 边渲染边推事件；TraceEvent 五类事件默认零开销；micrometer gauge 真值接线。try 页面即可视化执行流。
  - icon: 🚀
    title: 极致性能
    details: simple 热路径 0.51μs / JSON 4.3μs / 每百条循环编排 1.1ms；模板 LRU + Groovy 编译缓存 + 超时虚拟线程（JDK 21+），覆盖率棘轮门禁 95%+ 常绿。
---
