#!/usr/bin/env bash
# 运行示例 5 (schema 守护)。
set -euo pipefail
cd "$(dirname "$0")"

java -cp ../../../template4j-core/target/template4j-core.jar \
    fun.commons.template4j.core.Cli render template.jt \
    --data '{"id": 42, "name": "alice"}'

# 试试非法数据 (id 为负数):
#   java ... render template.jt --data '{"id": -1, "name": "x"}'
# 应该立即抛 "schema validation failed: ... minimum ..."
