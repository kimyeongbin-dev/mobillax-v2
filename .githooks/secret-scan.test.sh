#!/usr/bin/env bash
# secret-scan.sh 회귀 테스트: bash .githooks/secret-scan.test.sh
# 가짜 키는 실행 중에 만들어 이 파일 자체가 시크릿 검사에 걸리지 않게 한다.
set -uo pipefail
scan="$(cd "$(dirname "$0")" && pwd)/secret-scan.sh"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

fake40="$(head -c 400 /dev/urandom | tr -dc 'A-Za-z0-9' | head -c 40)"
fake32="$(head -c 400 /dev/urandom | tr -dc 'a-f0-9' | head -c 32)"
envname=".$(printf 'e')nv"
pemhead="-----BEGIN RSA PRIV""ATE KEY-----"

failed=0
run() { # $1=기대(PASS|BLOCK) $2=설명 $3=파일명 $4=내용
  rm -rf "$tmp/repo" && mkdir "$tmp/repo" && cd "$tmp/repo" && git init -q
  mkdir -p "$(dirname "$3")" && printf '%s\n' "$4" > "$3" && git add -- "$3"
  if bash "$scan" 2>/dev/null; then actual=PASS; else actual=BLOCK; fi
  if [ "$actual" = "$1" ]; then mark="✓"; else mark="✗"; failed=$((failed + 1)); fi
  printf '%s %-5s %s\n' "$mark" "$actual" "$2"
}

run BLOCK "비밀값 파일 커밋" "$envname" "DB_PASSWORD=abc"
run PASS  "예시 파일 커밋" "$envname.example" "DB_PASSWORD=change-me"
run BLOCK "JS 에 하드코딩된 Tmap 키" "static/a.js" "const h = { appKey: \"$fake40\" };"
run BLOCK "HTML 에 하드코딩된 Kakao 키" "t/home.html" "<script src=\"sdk.js?appkey=$fake32\"></script>"
run BLOCK "따옴표 안 32자 키" "Svc.java" "headers.set(\"x\", \"$fake40\");"
run BLOCK "properties 에 비밀번호" "app.properties" "spring.datasource.password=$fake40"
run BLOCK "개인키" "k.txt" "$pemhead"
run BLOCK "키 파일" "certs/server.pem" "dummy"
run PASS  "환경 변수 참조" "app.properties" "spring.datasource.password=\${DB_PASSWORD}"
run PASS  "자리표시자" "x.env.example" "TMAP_APPKEY=your-tmap-appkey"
run PASS  "마스킹된 로그" "Log.java" "log(\"serviceKey=****\");"
run PASS  "명시적 예외" "Hash.java" "String h = \"$fake40\"; // secret-scan: allow"
run PASS  "일반 코드" "Route.java" "String url = \"https://apis.openapi.sk.com/transit/routes\";"

[ "$failed" -eq 0 ] && echo && echo "모두 통과" || { echo; echo "${failed}건 실패"; exit 1; }
