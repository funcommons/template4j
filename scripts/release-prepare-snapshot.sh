#!/usr/bin/env bash
# 发布完成后的下个开发周期: 把版本号改回 SNAPSHOT。
# 用法: ./scripts/release-prepare-snapshot.sh 0.6.0-SNAPSHOT

set -euo pipefail
NEXT="${1:-}"
if [[ -z "$NEXT" ]]; then
  echo "Usage: $0 <next-version>  (e.g. 0.6.0-SNAPSHOT)"
  exit 1
fi
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

CUR=$(grep -m1 "<version>" pom.xml | sed -E 's/.*<version>([^<]+)<.*/\1/')
echo "==> Bumping $CUR -> $NEXT"

for pom in pom.xml template4j-core/pom.xml template4j-site/pom.xml; do
  sed -i.bak "s|<version>$CUR</version>|<version>$NEXT</version>|g" "$pom"
  rm "$pom.bak"
done

# CHANGELOG 加 [Unreleased] 区块在顶部
if ! grep -q "^## \[Unreleased\]" CHANGELOG.md; then
  # 在第一段 ## 后插入
  awk -v next="$NEXT" '
    /^## / && !inserted { print "## [Unreleased]\n"; inserted=1 }
    { print }
  ' CHANGELOG.md > CHANGELOG.md.new && mv CHANGELOG.md.new CHANGELOG.md
fi

echo "==> Done. Commit with: 'chore: start $NEXT cycle'"
