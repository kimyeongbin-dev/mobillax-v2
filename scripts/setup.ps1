# 클론 직후 1회 실행: Git 훅 연결 + .env 준비
$ErrorActionPreference = 'Stop'
Set-Location (git rev-parse --show-toplevel)

git config core.hooksPath .githooks
Write-Host "✔ Git 훅 연결 (core.hooksPath=.githooks)"

if (-not (Test-Path .env)) {
    Copy-Item .env.example .env
    Write-Host "✔ .env 생성 — 값 입력 방법: docs/ENV_SETUP.md"
} else {
    Write-Host "• .env 이미 있음 — 그대로 둠"
}

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    Write-Host "⚠ Docker 가 없습니다. README 의 사전 요구사항을 확인하세요."
}
