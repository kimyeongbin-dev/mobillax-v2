#!/usr/bin/env bash
# 클론 직후 1회 실행: Git 훅 연결 + .env 준비
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"

git config core.hooksPath .githooks
chmod +x .githooks/commit-msg .githooks/pre-push scripts/setup.sh
echo "✔ Git 훅 연결 (core.hooksPath=.githooks)"

if [ ! -f .env ]; then
  cp .env.example .env
  echo "✔ .env 생성 — 값 입력 방법: docs/ENV_SETUP.md"
else
  echo "• .env 이미 있음 — 그대로 둠"
fi

command -v docker > /dev/null || echo "⚠ Docker 가 없습니다. README 의 사전 요구사항을 확인하세요."
