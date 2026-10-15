#!/usr/bin/env bash
# 스테이징된 변경에서 비밀값을 검사 (pre-commit 에서 호출) — 규칙: CONTRIBUTING.md 4장
# 예외가 꼭 필요한 줄에는 "secret-scan: allow" 주석을 붙인다.
set -euo pipefail
export LC_ALL=C.UTF-8

fail=0
report() {
  printf '✖ %s\n' "$1" >&2
  fail=1
}

# 1) 비밀값 파일 자체가 커밋되는지
while IFS= read -r f; do
  [ -z "$f" ] && continue
  base="${f##*/}"
  if [[ "$base" =~ ^\.env(\..+)?$ && ! "$base" =~ \.example$ ]]; then
    report "비밀값 파일은 커밋할 수 없습니다: $f"
  fi
  case "$base" in
    *.pem | *.key | *.p12 | *.jks | *.keystore | id_rsa* | id_ed25519*) report "키 파일은 커밋할 수 없습니다: $f" ;;
  esac
done < <(git diff --cached --name-only --diff-filter=ACMR)

# 2) 추가된 줄에서 키 형태의 문자열
patterns=(
  "AKIA[0-9A-Z]{16}"
  "-----BEGIN [A-Z ]*PRIVATE KEY-----"
  "gh[pousr]_[A-Za-z0-9]{36,}"
  # 키 이름 = 값. 값 자리에 변수·메서드 호출(점이나 괄호가 이어짐)이 오는 경우는 제외
  "(appKey|serviceKey|api[_-]?key|secret|password|passwd|token)[\"']?[[:space:]]*[:=][[:space:]]*[\"']?[A-Za-z0-9+/_%-]{16,}([\"'[:space:],;&]|$)"
  "[\"'][A-Za-z0-9]{32,}[\"']"
)
# 자리표시자·마스킹·테스트용 가짜 값은 허용
placeholder='your-|change-me|example|\$\{|xxxx|FAKE_|\*\*\*\*|secret-scan: allow'
# 검사하지 않는 파일
skip_file='(^|/)(package-lock\.json|jquery-[^/]*\.js)$'

# 추가된 줄을 "파일<TAB>내용" 형태로 한 번에 모은 뒤 grep 을 몇 번만 실행 (줄마다 프로세스를 띄우지 않음)
added="$(git diff --cached -U0 --diff-filter=ACMR --no-color |
  awk '/^\+\+\+ b\// { f = substr($0, 7); next } /^\+\+\+ / { f = ""; next } /^\+/ && f != "" { print f "\t" substr($0, 2) }' |
  grep -vE "^[^	]*$skip_file	" | grep -vE "$placeholder" || true)"

if [ -n "$added" ]; then
  combined="$(IFS='|'; echo "${patterns[*]}")"
  # 키 이름(appKey/appkey/APP_KEY 등)은 대소문자를 구분하지 않음. 값은 출력하지 않고 파일명만 알림
  while IFS= read -r f; do
    [ -n "$f" ] && report "비밀값으로 보이는 문자열이 있습니다: $f"
  done < <(printf '%s\n' "$added" | grep -iE -- "$combined" | cut -f1 | sort -u || true)
fi

if [ "$fail" -ne 0 ]; then
  printf '\n  키는 .env 에 두고 코드에서는 환경 변수로 읽으세요. (CONTRIBUTING.md 4장)\n' >&2
  printf '  오탐이면 해당 줄에 "secret-scan: allow" 주석을 붙이세요.\n\n' >&2
  exit 1
fi
