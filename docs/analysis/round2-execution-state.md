# 第二轮优化 · 执行状态（已全部完成 ✅ 2026-11-09）

> **LOOP 已结束**：cron 34ac0bd5 已删除。方案 12 项（T0-T10 + T0b）全部闭环，PR #1 CI 功能全绿。
> 本文件保留作为执行记录。方案原文：`docs/analysis/optimization-plan-round2.md`

## 最终账目

| # | 任务 | 结果 | 落库 |
| --- | --- | --- | --- |
| T0 | A1 落盘提交 + PR 门禁 | 15 个主题提交 / PR #1 / CI 9 绿 1 待用户设置 | feat/v0.5 全程 |
| T0b | Cli.java 5 处 CodeQL error 修复 | 49/49（含 5 个新分支测试） | 25731a9 |
| T1 | S1 限流两缺陷（XFF 伪造 + 无界桶） | 32/32 三类测试 + 覆盖率补齐 | dc9ace8 + 25013fb |
| T2 | P1 沙箱脚本缓存 | 9/9（miss 即 put 契约） | 238a26f |
| T3 | S4 PR dependency-review 门禁 | actionlint 0 告警；等用户开 Dependency graph | ce6ed84 |
| T4 | A2 CI frontend + e2e job | frontend 22s 绿 / e2e smoke 绿 / nightly 全量 | 393aa30 |
| T5 | P2 trace 零分配 | 7/7（TOCTOU 防御） | 238a26f |
| T6 | A3 gauge 真值接线 | 7/7（site verify 200 互证） | 238a26f |
| T7 | S2 沙箱 imports 收敛 | 7/7 + 53 回归 + 1635 全量 + 双门禁过 | 6ec226c |
| T8 | S3 注入语义钉死 | 11/11，无 FINDING（数据≠模板语义归档） | 238a26f |
| T9 | P3 超时虚拟线程 | 6/6 双策略 + jacoco 平台类排除 | 238a26f |
| T10 | e2e smoke 抓到的真实溢出缺陷 | 三根因修复，smoke 0 issue | 6b25c1f |

**三端终态**：core 1635 / site 200 / frontend vitest 33 全绿；门禁 LINE 95.74% / BRANCH 90.48%；CodeQL 0 error。

## 仓库设置事项（已闭环）

- Dependency graph：用户确认**本来就是开的**；早前 dependency-review 报 "not supported" 是 graph 尚未为 PR 的全新 manifest 建 SHA 快照（由 push 事件逐步填充），后续 run 自动恢复。
- **门禁首战战果**：连出三层真实 high 漏洞并全部修复——@vue/server-renderer 3.5.39 XSS（GHSA-g2v6-rqmx-r4w6）→ axios 1.18.1 两条（GHSA-542g-h47m-68v8 / GHSA-3pq3-5fj3-cg6v）→ follow-redirects 1.16.0 三条（GHSA-8r9p-f939-6h3c 等）。升级链：vue 3.5.45（4c74637）→ axios 1.21.0（ceb97a5）→ follow-redirects 1.16.3（d48b817）。**PR #1 CI 11/11 全绿，可合并。**
- Dependabot vulnerability alerts + automated security fixes：API 开启 ✅。

## 立项池（本轮不做，已有诊断）

- 全局 box-sizing reset（影响全站盒模型，需全量回归）
- <600px 移动端汉堡 drawer（导航 @480/380 仍溢出，smoke 暂不检查该宽度）
- P4 renderString O(k·n) / A4 singleReturn 类型漂移（方案 §观察池，等真实信号）
