#!/usr/bin/env bash
# 스테이징된 텍스트 파일에 CR(\r)이 있으면 차단 (pre-commit 에서 호출) — 규칙: CONTRIBUTING.md 4장
# .gitattributes 의 eol=crlf 파일(*.bat, *.cmd)과 바이너리는 검사하지 않는다.
set -euo pipefail
export LC_ALL=C.UTF-8

fail=0
while IFS= read -r -d '' f; do
  [ "$(git check-attr eol -- "$f" | sed 's/.*: //')" = "crlf" ] && continue
  case "$(git ls-files --stage --eol -- "$f" | awk '{print $4}')" in
    i/-text) continue ;;
  esac
  # Git for Windows 의 grep 은 CRLF 를 줄 끝으로 처리해 \r 을 놓치므로 tr 로 센다
  if [ "$(git show ":$f" | tr -dc '\r' | wc -c)" -gt 0 ]; then
    printf '✖ CRLF(또는 CR) 줄바꿈: %s\n' "$f" >&2
    fail=1
  fi
done < <(git diff --cached --name-only -z --diff-filter=ACMR)

if [ "$fail" -ne 0 ]; then
  printf '\n  줄바꿈은 LF로 통일합니다. 편집기 줄바꿈을 LF로 바꾸거나 git add --renormalize <파일> 후 다시 커밋하세요.\n\n' >&2
  exit 1
fi
