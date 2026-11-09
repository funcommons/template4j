#!/usr/bin/env bash
# 发布助手: 把 SNAPSHOT 版本号改为正式版, 同步所有 pom + CHANGELOG。
# 用法: ./scripts/release-prepare.sh 0.5.0
#
# 检查清单 (手动确认):
#   1. mvn -B verify 全绿
#   2. CHANGELOG 已经写完整
#   3. 没有未提交改动
#   4. 当前分支是 main, 已 pull 最新

set -euo pipefail

VERSION="${1:-}"
if [[ -z "$VERSION" ]]; then
  echo "Usage: $0 <version>  (e.g. 0.5.0)"
  exit 1
fi

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

# 0. 前置检查
echo "==> Pre-flight checks"
git diff --quiet || { echo "ERR: working tree dirty, commit/stash first"; exit 1; }
BRANCH=$(git rev-parse --abbrev-ref HEAD)
if [[ "$BRANCH" != "main" ]]; then
  echo "WARN: on branch '$BRANCH' (recommend main)"
fi

# 1. 找当前 SNAPSHOT 版本号
CUR=$(grep -m1 "<version>" pom.xml | sed -E 's/.*<version>([^<]+)<.*/\1/')
if [[ ! "$CUR" =~ -SNAPSHOT$ ]]; then
  echo "ERR: current version '$CUR' is not a SNAPSHOT"
  exit 1
fi
echo "==> Bumping $CUR -> $VERSION"

# 2. 改 4 个 pom.xml 的 <version>
for pom in pom.xml template4j-core/pom.xml template4j-site/pom.xml; do
  sed -i.bak "s|<version>$CUR</version>|<version>$VERSION</version>|g" "$pom"
  rm "$pom.bak"
  echo "    patched $pom"
done

# 3. CHANGELOG [Unreleased] / [0.5.0-SNAPSHOT] -> [$VERSION] - YYYY-MM-DD
TODAY=$(date +%Y-%m-%d)
sed -i.bak -E "s|^## \[Unreleased\]|## [$VERSION] - $TODAY|" CHANGELOG.md
sed -i.bak -E "s|^## \[[^]]*-SNAPSHOT\]|## [$VERSION] - $TODAY|" CHANGELOG.md
rm CHANGELOG.md.bak
echo "    patched CHANGELOG.md (date: $TODAY)"

# 4. 验证编译
echo "==> Verify build"
mvn -B -q -pl template4j-core -am compile || { echo "ERR: compile failed, abort"; exit 1; }

# 5. 给提示
cat <<EOF

==> Ready to commit. Next steps:

  git add -A
  git commit -m "release: v$VERSION"
  git tag v$VERSION
  git push origin main --tags

Then create GitHub Release with notes from CHANGELOG.md [$VERSION] section.

After release, start next dev cycle:
  ./scripts/release-prepare-snapshot.sh 0.6.0-SNAPSHOT
EOF
