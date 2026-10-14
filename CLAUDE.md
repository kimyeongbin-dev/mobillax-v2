# CLAUDE.md

AI 코딩 도구(Claude Code)가 이 저장소에서 작업할 때 지키는 규칙입니다. 사람이 지키는 규칙은 [CONTRIBUTING.md](CONTRIBUTING.md)에 있고, 이 파일은 그 규칙을 AI 쪽에서도 똑같이 따르게 하는 하네스입니다.

## 비밀값
- `.env`(및 `.env.local` 등)는 **읽지도, 출력하지도, 수정하지도 않는다.** 키 수정은 사용자가 직접 한다.
  - Read/Grep/`cat`/`grep`/셸 변수로 값을 읽는 것 모두 금지. 길이나 앞 몇 글자를 출력하는 것도 금지.
  - `docker compose config`, `docker inspect`, 컨테이너 안 `printenv`처럼 주입된 환경변수를 펼쳐 출력하는 명령도 금지.
  - `.claude/hooks/guard-secrets.js`(PreToolUse 훅)와 `.claude/settings.json`의 deny 규칙이 이를 강제한다.
- 키가 동작하는지는 HTTP 상태와 오류 코드 같은 **결과로만** 판단한다.
- 코드·설정·로그에 키를 남기지 않는다. 외부 API 호출 URL에 키가 들어가면 로그로 출력하지 않는다.

## 커밋과 브랜치
- 커밋 메시지는 CONTRIBUTING.md 2장 형식(`<type>(<scope>): <한국어 요약>`, 50자 이내)을 따른다.
- **AI 트레일러(`Co-authored-by: Claude`, `Generated with Claude Code` 등)를 커밋과 PR에 넣지 않는다.** AI 활용은 PR 본문의 "AI 활용" 절과 README에서 밝힌다.
- 작업은 `dev`에서 `feature/<이름>` 브랜치를 만들어 하고, PR 템플릿을 채워 `dev`로 머지한다.
- 버전 릴리스는 `dev` → `main` PR과 태그(`v1.1`, `v1.2`, ...)로 한다.

## 커밋 전 검사
- pre-commit 훅이 시크릿 검사와 lint-staged(Prettier + ESLint)를 실행한다. 훅을 우회(`--no-verify`)하지 않는다.
- 테스트용 가짜 키를 파일에 쓸 때는 실행 중에 만들거나 `FAKE_` 접두어를 쓴다.

## 실행 환경
- 로컬 JDK 버전과 무관하게 **Docker로 빌드·실행·테스트**한다. 호스트에서 `./gradlew`를 직접 돌리지 않는다.
  - 실행: `docker compose up -d --build`
  - 테스트: `docker compose --profile test run --rm --build test`
- TMAP 대중교통 API는 FREE 요금제가 하루 10건이므로 검증 호출을 최소화한다.
