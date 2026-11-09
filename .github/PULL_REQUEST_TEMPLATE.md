<!--
感谢你的 PR！请按以下清单准备。
-->

## Summary

<!-- 1-3 句话说明改了什么、为什么 -->

## Type

- [ ] feat — 新功能
- [ ] fix — bug 修复
- [ ] refactor — 重构（不改外部行为）
- [ ] docs — 文档
- [ ] test — 测试
- [ ] chore — 工程化 / CI / 依赖
- [ ] breaking — 破坏性变更

## Checklist

- [ ] `mvn -B verify` 全绿（4 模块）
- [ ] 新功能有测试覆盖
- [ ] JaCoCo 阈值未下降（LINE ≥ 0.95, BRANCH ≥ 0.85）
- [ ] CHANGELOG 已更新（`[0.5.0-SNAPSHOT]` 区块）
- [ ] `manual/` 文档已更新（如涉及用户可见行为）
- [ ] commit message 清晰

## Benchmark / 性能影响

<!-- 如果改动影响热路径, 贴 -Pbenchmark 数据 -->

```
| Benchmark | 改前 | 改后 |
|---|---|---|
| ... | ... | ... |
```

## 关联 issue

Closes #
