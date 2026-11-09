# 依赖安全扫描（security-scan）

依赖漏洞卡口由 [`.github/workflows/security-scan.yml`](../.github/workflows/security-scan.yml) 提供，与 `build`、`codeql` 两个工作流以及 Dependabot 互补。

## 触发时机

- push 到 `main`
- 每周一定时 cron（GitHub cron 按 UTC 调度，实际执行可能有分钟级延迟）
- `workflow_dispatch`（Actions 页面手动 Run workflow）

## 扫描 Job 与门槛

| Job | 工具 | 失败条件（卡口） |
| --- | --- | --- |
| `dependency-check` | OWASP dependency-check-maven 10.0.4 | 任一依赖 CVSS ≥ 9 即阻断（`-DfailBuildOnCVSS=9`）；已关闭 RetireJS 分析器（`-DretireJsAnalyzerEnabled=false`）以容忍扫描超时。NVD 数据库按周缓存 + `-DfailOnError=false`（NVD API 偶发 524 不终止分析，该轮检测降级、随缓存收敛）；可选 secret `NVD_API_KEY` 提升全量拉取速度 |
| `trivy-fs` | aquasecurity/trivy-action@v0.36.0（fs 扫描，版本 pin 死） | 发现 CRITICAL / HIGH 级别问题即失败（`exit-code: 1`）。扫描前先 `mvn dependency:go-offline` 填充本地 Maven 仓——空 `~/.m2` 下逐 pom 直连 Maven Central 会撞 429 全 IP 封禁 30min |

两个 job 的扫描报告均以 artifacts 上传（`dependency-check-report` / `trivy-fs-report`），job 失败也会上传（`if: always()`），便于事后排查。

## Dependabot alerts（已开启 ✅ 2026-11-09）

仓库已有 [`.github/dependabot.yml`](../.github/dependabot.yml)（maven + github-actions，每周一更新）。Dependabot vulnerability alerts 与 automated security fixes 已于 2026-11-09 通过 API 开启；开启后 security updates 立即对前端 npm lockfile 生效（tinypool / @intlify/core-base / vue-i18n / vite 四个修复更新自动开跑）——这类存量漏洞 PR diff 门禁看不到，两层互补。已知缺口：dependabot.yml 尚无 npm ecosystem 条目（前端暂无每周例行版本更新，只有出漏洞才修）。

## 本地复跑

```bash
# OWASP dependency-check（HTML 报告输出到各模块 target/dependency-check-report.html）
mvn org.owasp:dependency-check-maven:10.0.4:check

# Trivy 文件系统扫描（配置文件 / lockfile / IaC）
trivy fs .
```

## 已知现状与误报处理

`groovy-sandbox 1.19`、`bucket4j-core 8.10.1`、`caffeine 3.1.8` 为本次新引入的依赖。扫描如命中与它们相关的告警，请按 GitHub advisory 逐条评估：确认影响版本范围、本仓库实际调用路径、上游修复版本，再决定升级或豁免；确认为误报时在 PR 中说明结论并附 advisory 链接，便于后续审计追溯。
