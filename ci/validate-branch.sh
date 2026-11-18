#!/usr/bin/env bash
set -euo pipefail

: "${SOURCE_BRANCH:?需要选择应用分支}"
git check-ref-format --branch "$SOURCE_BRANCH" >/dev/null
if [[ "$SOURCE_BRANCH" == -* || "$SOURCE_BRANCH" == *'..'* ]]; then
    echo '应用分支名称无效' >&2
    exit 1
fi
