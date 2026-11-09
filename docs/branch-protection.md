# Branch protection policy for `main`

> 配置入口：GitHub repo → Settings → Branches → Branch protection rules → Add rule

## Required settings

- **Branch name pattern**: `main`
- ✅ Require a pull request before merging
  - Required approvals: **1**
  - Dismiss stale approvals on new push: ✅
  - Require review from Code Owners: ✅
- ✅ Require status checks to pass
  - Require branches up to date: ✅
  - Required checks:
    - `build (JDK 17, ubuntu-latest)`
    - `build (JDK 21, ubuntu-latest)`
    - `build (JDK 17, macos-latest)`
- ✅ Require conversation resolution before merging
- ✅ Require linear history
- ✅ Do not allow bypassing the above settings

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
