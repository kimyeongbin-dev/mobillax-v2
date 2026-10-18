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
- pre-commit 훅이 시크릿 검사, 줄바꿈 검사(CRLF 차단)와 lint-staged(Prettier + ESLint)를 실행한다. 훅을 우회(`--no-verify`)하지 않는다.
- 테스트용 가짜 키를 파일에 쓸 때는 실행 중에 만들거나 `FAKE_` 접두어를 쓴다.

## 보안 규칙
- [docs/SECURITY_RULES.md](docs/SECURITY_RULES.md)의 규칙은 기능을 고쳐도 깨지면 안 된다. 각 규칙은 회귀 테스트(`SecurityRegressionTests` 등)로 고정되어 있다.
- 회귀 테스트를 지우거나, 단언을 약하게 바꾸거나, 실패하는 테스트를 건너뛰게 하지 않는다. 규칙을 바꿔야 하면 문서·테스트를 같은 PR에서 고치고 이유를 적는다.
- 새 API를 만들면 로그인 필요 여부(S2)·CSRF(S3)·소유권(S4)을 확인하고 테스트를 추가한다.
- 브라우저에서 오는 값은 모두 서버가 검증한다(S10).
  - 요청 DTO에 `Common/Validation`의 제약 어노테이션을 붙인다. 본문은 `RequestBodyValidationAdvice`가 자동으로 검사한다.
  - 폼 객체에는 `@Valid`, 경로·쿼리 문자열에는 제약 어노테이션을 붙인다.
  - 화면에 다시 보일 문자열은 서버 값으로 만든다.
- 새 화면은 공통 레이아웃 조각(`fragments/layout :: head`, `header(...)` 또는 `compactNav`)을 쓴다. 헤더를 직접 쓰지 않는다(`npm run test:web`의 web-layout 검사).
- 화면 스크립트에서 HTML을 넣을 때는 `` html`...` `` 태그 템플릿(`safe-html.js`)을 쓰고, 인라인 이벤트 문자열에 값을 넣지 않는다.
- fetch 로 상태를 바꾸는 화면은 `csrf.js`를 먼저 불러온다.

## 코드 품질
- 공통 관심사(인증, CSRF, 입력 검증, 출력 이스케이프, 예외 응답, 로그 마스킹)는 기능 코드에 흩지 않는다. 공통 계층(필터·어드바이스·공용 모듈)에 한 번 구현하고, 새 기능은 그 계층을 거치게 한다.
- 규칙 값(정규식·범위·문구)은 한 곳에 둔다(`InputRules`, `AccountRules`, 재사용 제약 어노테이션). 같은 검사를 다시 쓰지 않는다.
- 규칙을 빠뜨리면 빌드가 실패하도록 구조 테스트로 강제한다(`InputValidationCoverageTests`, `npm run test:web`).
- 계층마다 책임을 하나만 둔다. 형식은 DTO, 업무 규칙은 서비스, 외부 연동은 클라이언트 클래스가 맡고, 컨트롤러는 요청과 응답만 다룬다.
- 의도를 드러내는 이름, 한 가지 일을 하는 함수, 이름 붙인 상수를 쓰고, 쓰지 않는 코드는 지운다.
- 구조만 바꾸는 리팩터링은 `refactor:` 커밋으로 나누고, 기존 테스트가 그대로 통과해야 한다.
- 자세한 기준: CONTRIBUTING.md "코드 품질"

## 실행 환경
- 로컬 JDK 버전과 무관하게 **Docker로 빌드·실행·테스트**한다. 호스트에서 `./gradlew`를 직접 돌리지 않는다.
  - 실행: `docker compose up -d --build`
  - 테스트: `docker compose --profile test run --rm --build test`
  - Dockerfile·docker-compose.yml을 고치면 `npm run test:infra`(보안 규칙 S8)도 돌린다.
  - 화면 스크립트를 고치면 `npm run test:web`(보안 규칙 S10)도 돌린다.
- TMAP 대중교통 API는 FREE 요금제가 하루 10건이므로 검증 호출을 최소화한다.
