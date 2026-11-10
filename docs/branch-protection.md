# Branch protection policy for `main`

> **已启用 (2026-11-10, 经 API 配置)**。单人维护者阶段采用修正版:
> 原初稿的 `approvals:1 + 禁 bypass` 在 GitHub 上是死锁 (无法 approve 自己的 PR,
> 连管理员也会被挡), 必改 `approvals:0 + 允许 admin bypass`。多人协作时再收紧。

## Required settings (实际生效配置)

- **Branch name pattern**: `main`
- ✅ Require a pull request before merging
  - Required approvals: **0** (单人阶段; 多人后升 1)
  - Dismiss stale approvals on new push: ❌ (无 approval 可 dismiss)
  - Require review from Code Owners: ❌ (CODEOWNERS 文件已备, 多人后启用)
- ✅ Require status checks to pass
  - Require branches up to date: ✅ (`strict: true`)
  - Required checks (与 build/codeql/security-scan 实际 job 名一致):
    - `core (JDK 17, ubuntu)`
    - `site (JDK 17, ubuntu)`
    - `frontend (node 22, ubuntu)`
    - `e2e (smoke on PR/push, full regression nightly)`
    - `shade (fat jar)`
    - `CodeQL`
    - `Dependency review (PR diff gate, fail on high)`
- ✅ Require conversation resolution before merging
- ✅ Require linear history (`allow_merge_commit: false`, 仅 rebase / squash)
- ❌ Do not allow bypassing the above settings (**admin 可 bypass** — 单人阶段必要)
- Allow force push / deletions: ❌

## Code Owners

仓库根目录加 `CODEOWNERS`:

```
# Default: project maintainers
*                            @justin

# Core engine: requires extra-careful review
/template4j-core/            @justin

# Docs (lower bar)
/manual/                     @justin
/docs/                       @justin
```

## Release flow

1. PR 合并到 `main`
2. `main` HEAD 通过 CI
3. 准备发版：
   - 改 4 个 pom.xml `version` 去 `-SNAPSHOT`
   - 改 CHANGELOG 标 `[0.5.0] - YYYY-MM-DD`
   - 提交 PR "release: 0.5.0"
4. 合并后打 tag：`git tag v0.5.0 && git push origin v0.5.0`
5. GitHub Release：用 CHANGELOG 0.5.0 段作为 release notes
6. 下一个开发周期：`0.5.0` → `0.6.0-SNAPSHOT`（4 个 pom 同步改）

## 备注

- 不开 force-push 到 main（除非紧急回滚，需 2 个 maintainer 同意）
- hotfix 流程：从 main 拉分支 `hotfix/x.y.z` → 修 → PR 回 main → tag
