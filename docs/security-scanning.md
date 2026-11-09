# 依赖安全扫描（security-scan）

依赖漏洞卡口由 [`.github/workflows/security-scan.yml`](../.github/workflows/security-scan.yml) 提供，与 `build`、`codeql` 两个工作流以及 Dependabot 互补。

## 触发时机

- push 到 `main`
- 每周一定时 cron（GitHub cron 按 UTC 调度，实际执行可能有分钟级延迟）
- `workflow_dispatch`（Actions 页面手动 Run workflow）

## 扫描 Job 与门槛

| Job | 工具 | 失败条件（卡口） |
| --- | --- | --- |
| `dependency-check` | OWASP dependency-check-maven 10.0.4 | 任一依赖 CVSS ≥ 9 即阻断（`-DfailBuildOnCVSS=9`）；已关闭 RetireJS 分析器（`-DretireJsAnalyzerEnabled=false`）以容忍扫描超时 |
| `trivy-fs` | aquasecurity/trivy-action@master（fs 扫描） | 发现 CRITICAL / HIGH 级别问题即失败（`exit-code: 1`） |

两个 job 的扫描报告均以 artifacts 上传（`dependency-check-report` / `trivy-fs-report`），job 失败也会上传（`if: always()`），便于事后排查。

## Dependabot alerts（需管理员手动开启）

仓库已有 [`.github/dependabot.yml`](../.github/dependabot.yml)（maven + github-actions，每周一更新），但 **Dependabot alerts 当前处于关闭状态**。开启步骤（需要仓库管理员权限，无法通过提交文件自动化）：

1. 仓库页 → **Settings** → **Code security**（Code security and analysis）
2. 找到 **Dependabot alerts** → 点击 **Enable**
3. 建议同时确认 **Dependabot version updates** 已启用（读取已有 dependabot.yml 即生效）
4. 开启后 Security 标签页会展示依赖告警；CI 中 dependency-check / trivy 的命中可与 GitHub advisory 交叉对照

## 本地复跑

```bash
# OWASP dependency-check（HTML 报告输出到各模块 target/dependency-check-report.html）
mvn org.owasp:dependency-check-maven:10.0.4:check

# Trivy 文件系统扫描（配置文件 / lockfile / IaC）
trivy fs .
```

## 已知现状与误报处理

`groovy-sandbox 1.19`、`bucket4j-core 8.10.1`、`caffeine 3.1.8` 为本次新引入的依赖。扫描如命中与它们相关的告警，请按 GitHub advisory 逐条评估：确认影响版本范围、本仓库实际调用路径、上游修复版本，再决定升级或豁免；确认为误报时在 PR 中说明结论并附 advisory 链接，便于后续审计追溯。
