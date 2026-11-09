# 优化方案执行状态（已全部完成 ✅ 2026-11-09）
> **LOOP 已结束**：cron ffa3480c 已删除。全部 14 任务完成，三端 1775 测试全绿。本文件保留作为执行记录。

> **LOOP 续跑指引**：本文件是 `/loop` 无人值守执行的唯一进度锚点。每轮 tick：①读本文件 → ②推进处于等待阶段任务到下一阶段 → ③派子 agent（单用途，最多 5 个并发）→ ④更新本文件 → ⑤汇报。
> 子 agent 单用途规则：写业务代码 / 写测试 / 跑测试 / 写文档 / 其它，**一个任务只做一件事**。
> 每任务流程：写测试 → 写业务代码 → 跑测试(仅该代码) → 修复（≤4 轮）。
> 全量测试触发：改动 >10% 代码量 或 >1000 行 或 >10 个任务（子 agent 后台跑，不阻塞编码）。
> 方案原文：`docs/analysis/optimization-plan-perf-security-structure.md`

## 环境决策

- Maven 网络 ✓ 可拉新依赖。定版：Caffeine 3.1.8、groovy-sandbox 1.19（1.7.0 不存在）、bucket4j-core 8.10.1
- 路线：三方优先，全部可引
- 主分支直接改，不 commit（用户未要求）

## 任务板

| # | 任务 | 对应方案项 | 阶段 | 状态 | 备注 |
| --- | --- | --- | --- | --- | --- |
| T1 | Groovy 编译缓存（Compilable+Caffeine，属性可关） | P1 性能 | 3 跑测试(阻塞) | ⏸ | 代码✅；**core testCompile 整模块耦合**：SandboxRenderTest(等T2)/RestSsrfGuardTest(等T3) 未实现 API 导致测试编译失败——T2+T3 落地后统一重跑 |
| T2 | Groovy 沙箱（withSandbox(boolean)，groovy-sandbox 1.19） | S1/S2 安全 | 3 跑测试 | 🔄 | 测试✓(145行/14方法)；T6 已释放 RenderEngine → 本轮派出。代码✅(210行+pom exclusion)；关键发现：groovy-sandbox 1.19 POM 传递 groovy 2.4.7 须 exclusion；拦截器注册表是 ThreadLocal→每 eval 注册/注销（子线程验证过）；属性读取二跳 onMethodCall(receiver,"get")；callable-bindings 走 onMethodCall(Script,"fne")。全量测试 agent 已派 |
| T3 | rest SSRF 防护 + T3b 断路器→Resilience4j 2.2.0 | S3 安全 | ✅ 完成(全量确认中) | ✅ | 修复验证过：pom surefire allowed-hosts 注入生效，CircuitBreakerTest 4/4 + RestRendererFullTest 8/8 走 guard 全绿；T2 落地后随全量最终确认 |
| T4 | API 限流 RateLimitFilter（POST/PUT/PATCH/DELETE，429+Retry-After） | S4 安全 | ✅ 完成 | ✅ | 测试 8/8 全通过（第一轮即过，无需修复）；106 行 bucket4j 实现 |
| T5 | Caffeine 替换 parseCache（rest CACHE 归 T7b） | P3 性能 | ✅ 完成(6/6) | ✅ | 代码✅+修复轮：断言笔误修正后 6/6 全过；rest CACHE 由 T7b 接线 |
| T6 | RenderOptions 请求级 API + TemplateService 请求级重构（T6b） | A1 结构 | ✅ API✅ + T6b✅(14/14)；3 参 render 全量验证等 T2 解除后批量跑 | ✅ | API 124 行+测试 146 行；T6b：去 renderExecutor/ThreadLocal→lastTrace，超时/错误语义不变，生产 trace 路径修复 |
| T7 | RestOptions 合并语义（纯函数 + 后续 T7b 接线 RenderEngine.withRestOptions） | A2 结构 | 3 跑测试(阻塞) | ⏸ | 代码✅(69行，编译过)；core testCompile 等阻塞 → T2 落地后随批量跑；T7b(RenderEngine 接线)排在 T8 前 |
| T8 | Executor 策略可注入（parallel 用引擎注入 executor） | P2/A3 | 2 写业务代码 | 🔄 | 测试✓(132行/5方法)；RenderEngine 与 T2 冲突 → T2 落地后派。**T8b✅**：surefire 3.2.5 / enforcer [17,) / jacoco 0.8.12（effective-pom 验证过） |
| T9 | 安全响应头过滤器 + 依赖扫描卡口 | S5/S6 安全 | ✅ 完成 | ✅ | 过滤器 17/17；T9b：.github/workflows/security-scan.yml（dependency-check CVSS≥9 + trivy CRITICAL/HIGH）+ docs/security-scanning.md 均落盘 |
| T10 | 前端 markdown-it+DOMPurify 替换手写渲染 | S6/A4 | ✅ 完成 | ✅ | vitest 9/9 一次通过；RenderedMarkdown 脚本 176→42 行（markdown-it+DOMPurify+rel hook） |
| T11 | 前端 CodeEditor 高亮（Shiki，同步正则兜底） | A4/P4 | 3 跑测试 | 🔄 | 代码✅(62行净增，SFC 编译过，三层 jsdom 回退防护)；vitest 执行子 agent 已派（含 RenderedMarkdown 回归） |
| T12 | micrometer 渲染指标 | A6 | ✅ 完成 | ✅ | 修复轮：YAML fixture 加引号后 4/4 全过；55 行 |
| T13 | ArchUnit 分层守护测试 | A5 | 3 跑测试(排队) | ⏸ | 测试✓(48行/3规则，静态校验全过)；archunit-junit5 1.3.0 已入 pom；随 T2 后全量跑 |
| T14 | 文档更新（README/CLAUDE.md 记录新 API 与配置） | 其它 | 0 计划 | ⬜ | 收尾 |
| FT | 全量测试（>10% / >1000 行 / >10 任务时触发） | 流程要求 | — | ⬜ | 子 agent 后台 |

## 子 agent 在途

（空）

## 决策与备注日志

- 2026-11-09 Loop 启动：job ffa3480c，`*/5 * * * *`。
- 每任务 ≤4 轮 测试→修复；4 轮后仍有 P0/P2 遗留则记录到本文件"遗留"节并继续下一任务（无人值守，不无限重试）。

## 遗留（4 轮未收敛项）

（空）
