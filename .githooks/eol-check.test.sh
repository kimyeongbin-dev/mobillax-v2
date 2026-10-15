#!/usr/bin/env bash
# eol-check.sh 회귀 테스트: bash .githooks/eol-check.test.sh
set -uo pipefail
check="$(cd "$(dirname "$0")" && pwd)/eol-check.sh"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

failed=0
run() { # $1=기대(PASS|BLOCK) $2=설명 $3=파일명 $4=내용(printf 형식) $5=.gitattributes(선택)
  rm -rf "$tmp/repo" && mkdir "$tmp/repo" && cd "$tmp/repo" && git init -q && git config core.autocrlf false
  [ -n "${5:-}" ] && printf '%s\n' "$5" > .gitattributes && git add .gitattributes
  mkdir -p "$(dirname "$3")" && printf "$4" > "$3" && git add -- "$3"
  if bash "$check" 2>/dev/null; then actual=PASS; else actual=BLOCK; fi
  if [ "$actual" = "$1" ]; then mark="✓"; else mark="✗"; failed=$((failed + 1)); fi
  printf '%s %-5s %s\n' "$mark" "$actual" "$2"
}

run PASS  "LF 텍스트" "a.js" 'const a = 1;\nconst b = 2;\n'
run BLOCK "CRLF 텍스트" "a.js" 'const a = 1;\r\nconst b = 2;\r\n'
run BLOCK "LF·CRLF 혼합" "t/home.html" '<p>a</p>\n<p>b</p>\r\n'
run PASS  "eol=crlf 로 지정한 배치 파일" "gradlew.bat" '@echo off\r\n' '*.bat -text eol=crlf'
run PASS  "바이너리" "img.png" '\x89PNG\r\n\x1a\n\x00\x00'
run PASS  "text=auto eol=lf 가 커밋 시 LF로 변환" "a.css" 'a{}\r\n' '* text=auto eol=lf'

[ "$failed" -eq 0 ] && echo && echo "모두 통과" || { echo; echo "${failed}건 실패"; exit 1; }
