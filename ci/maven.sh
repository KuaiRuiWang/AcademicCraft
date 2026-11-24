#!/usr/bin/env bash
set -euo pipefail

: "${MAVEN_USER_HOME:?需要配置持久化 Maven 缓存目录}"
mkdir -p "$MAVEN_USER_HOME"
# 两条任务共享仓库缓存，串行执行 Maven，避免同时改写依赖和元数据。
exec 9> "$MAVEN_USER_HOME/academiccraft.lock"
flock 9
exec sh mvnw -B -ntp "-Dmaven.repo.local=${MAVEN_USER_HOME}/repository" "$@"
