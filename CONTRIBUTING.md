# 개발 규칙

## 1. 시작하기

클론한 직후에 한 번 실행합니다. Git 훅을 연결하고, `.env`가 없으면 `.env.example`을 복사해 만듭니다.

```bash
# macOS / Linux / Git Bash
./scripts/setup.sh
```
```powershell
# Windows PowerShell
./scripts/setup.ps1
```

---

## 2. 커밋 컨벤션

[Conventional Commits](https://www.conventionalcommits.org/ko/v1.0.0/)를 따르고, 요약은 한국어로 씁니다.

```
<type>(<scope>): <요약>

<본문 (선택)>
```

### 2-1. type

| type | 용도 | 예시 |
|---|---|---|
| `feat` | 사용자에게 보이는 기능 추가·변경 | `feat(cart): 장바구니 항목 개별 삭제` |
| `fix` | 버그 수정 | `fix(auth): 로그아웃 후 500 에러 페이지로 이동하던 문제 수정` |
| `refactor` | 동작 변화 없는 구조 개선 | `refactor(route): Tmap 호출을 서비스 계층으로 이동` |
| `perf` | 성능 개선 | `perf(route): 경로 검색 결과 캐싱` |
| `style` | 포맷·세미콜론 등 동작과 무관한 코드 스타일 | `style: 들여쓰기 통일` |
| `test` | 테스트 추가·수정 | `test(payment): 결제 검증 단위 테스트 추가` |
| `docs` | 문서만 변경 | `docs: 로컬 실행 가이드 보강` |
| `build` | 빌드·의존성·Docker 설정 | `build: Spring Boot 3.3으로 업그레이드` |
| `ci` | CI 설정 | `ci: PR 빌드 워크플로 추가` |
| `chore` | 그 밖의 잡무 (설정 파일, 스크립트 등) | `chore: Git 훅 셋업 스크립트 추가` |
| `revert` | 이전 커밋 되돌리기 | `revert: feat(cart): 장바구니 항목 개별 삭제` |

### 2-2. scope (선택)
- 변경된 영역을 영문 소문자로 씁니다. 예: `auth`, `cart`, `payment`, `route`, `map`, `mypage`, `config`, `docker`
- 여러 영역에 걸치면 생략합니다.
- 하위 호환이 깨지는 변경은 type 뒤에 `!`를 붙입니다. 예: `feat(api)!: 장바구니 응답 형식 변경`

### 2-3. 요약 (첫 줄)
- **50자 이내** (type·scope 포함), 끝에 마침표를 찍지 않습니다.
- "무엇을 했는지"를 명사형이나 "○○ 수정", "○○ 추가"로 끝맺습니다.
- 한 커밋에는 하나의 논리적 변경만 담습니다.

### 2-4. 본문 (선택)
- 요약과 본문 사이는 한 줄 비웁니다.
- **왜** 바꿨는지, 이전에 어떤 문제가 있었는지를 씁니다. 무엇을 바꿨는지는 diff로 알 수 있습니다.
- 목록은 `- `로 시작합니다.

### 2-5. 트레일러
- AI 도구의 `Co-authored-by`, `Generated with ...` 같은 트레일러는 **커밋에 넣지 않습니다.**
  AI 도구를 어떻게 활용했는지는 README의 기술 스택·회고 섹션과 PR 본문에서 밝힙니다.
- 사람 공동 작업자의 `Co-authored-by`, 이슈 참조(`Refs: #12`) 같은 트레일러는 써도 됩니다.

### 2-6. 임시 커밋
- 작업 중에는 `wip: ...`, `fixup! ...`, `squash! ...` 커밋을 만들어도 됩니다.
- 이런 커밋은 `main`, `dev`에 push할 수 없습니다. PR을 머지하기 전에 `git rebase -i --autosquash`로 정리합니다.

---

## 3. 브랜치

| 브랜치 | 용도 |
|---|---|
| `main` | 릴리스 기준. 버전 태그(`v1`, `v1.1`, ...)를 이 브랜치에 답니다 |
| `dev` | 다음 릴리스를 통합하는 브랜치 |
| `feature/<이름>` | 기능·수정 단위 작업. `dev`에서 갈라져 PR로 `dev`에 머지 |

### 작업 흐름
1. `git switch -c feature/<이름> dev`
2. 컨벤션에 맞춰 커밋한 뒤 push
3. `dev`를 대상으로 PR을 엽니다. [PR 템플릿](.github/pull_request_template.md)의 구현 내용, 트러블슈팅, 고민한 점을 채워 개발 기록으로 남깁니다.
4. **Merge commit**으로 머지합니다 (기능 단위 커밋과 PR 연결을 보존). 머지한 feature 브랜치는 삭제합니다.
5. 버전 목표를 달성하면 `dev` → `main` PR로 릴리스하고, `main`에 버전 태그를 답니다.

---

## 4. Git 훅이 자동으로 막는 것

`.githooks/`에 있으며, `scripts/setup.*`가 `git config core.hooksPath .githooks`로 연결합니다.

| 훅 | 막는 것 |
|---|---|
| `pre-commit` | ① **시크릿 검사**: `.env`·키 파일, 하드코딩된 키 형태(키이름=값, 따옴표 안 32자 이상 영숫자, 개인키, AWS·GitHub 토큰). ② **줄바꿈 검사**: CRLF(또는 CR)가 들어간 텍스트 파일. ③ **포맷·린트**: 스테이징된 JS는 Prettier + ESLint(오류 시 차단), CSS는 Prettier (lint-staged) |
| `commit-msg` | 위 형식에 맞지 않는 첫 줄, 50자를 넘는 요약, 마침표로 끝나는 요약, 요약 다음 줄이 비어 있지 않은 경우, AI 트레일러 |
| `pre-push` | `main`·`dev`로 push되는 커밋 중 `wip`·`fixup!`·`squash!`·`amend!` 커밋, AI 트레일러가 남은 커밋 |

Git이 자동으로 만드는 `Merge ...`, `Revert "..."` 메시지는 통과시킵니다.

### 시크릿 검사 오탐
- 키가 아닌데 걸리면 해당 줄에 `secret-scan: allow` 주석을 붙입니다.
- `your-...`, `change-me`, `${ENV_VAR}` 같은 자리표시자와 환경 변수 참조는 자동으로 통과합니다.

### 줄바꿈 (LF)
- 저장소의 모든 텍스트 파일은 **LF**로 통일합니다. CRLF가 섞이면 diff가 줄 전체 변경으로 보이고, 셸 스크립트가 리눅스·컨테이너에서 `\r` 때문에 깨집니다.
- `.gitattributes`(`* text=auto eol=lf`)가 커밋할 때 LF로 맞추고, `.editorconfig`가 편집기의 새 파일을 LF로 만듭니다. Windows 전용 `*.bat`·`*.cmd`만 CRLF입니다.
- 스크립트로 파일을 쓸 때도 줄바꿈을 명시합니다. Python은 `open(..., newline="\n")`, PowerShell은 `[IO.File]::WriteAllText`에 LF 문자열을 씁니다.

### 린트·포맷 명령
| 목적 | 명령 |
|---|---|
| 전체 린트 | `npm run lint` |
| 전체 포맷 | `npm run format` |
| 훅 회귀 테스트 | `npm run test:hooks` |
| 컨테이너 보안 규칙(S8) | `npm run test:infra` |
| 화면 출력 이스케이프(S10) | `npm run test:web` |

### 코드 품질 (클린 코드·재사용·유지보수)
기능을 늘려도 규칙이 빠지지 않고, 한 곳만 고치면 되도록 짭니다.

- **공통 관심사는 공통 계층에 한 번만.** 인증·CSRF·입력 검증·출력 이스케이프·예외 응답·로그 마스킹 같은 규칙은 기능 코드마다 흩어 쓰지 않습니다. 필터, 어드바이스, 공용 모듈처럼 모든 요청과 화면이 지나가는 곳에 한 번 구현하고, 새 기능은 그 계층을 거치기만 하면 되게 합니다.
  - 요청 본문 검증: `Common/Validation/RequestBodyValidationAdvice`가 모든 `@RequestBody`를 검사합니다. 컨트롤러는 `@Valid`를 쓰지 않아도 됩니다.
  - 화면 출력: `safe-html.js`의 `` html`...` ``가 끼워 넣는 값을 모두 이스케이프합니다.
  - 예외 응답: `GlobalExceptionHandler` 한 곳에서 상태 코드와 문구를 정합니다.
  - 화면 공통 부분: `templates/fragments/layout.html`이 공통 head(CSRF·출력 이스케이프·세션 타이머 스크립트)와 상단 메뉴를 한 번만 정의하고, 모든 화면이 이 조각을 씁니다. 헤더 값(장바구니 수, 세션 시간)은 `LayoutInfo`가 화면을 그릴 때 계산합니다.
  - 시간대: `Common/Time/KoreaTime` 한 곳에서 정하고 앱이 뜰 때 기본 시간대로 적용합니다.
  - 화면이 미리 보여 주는 판단과 서버가 거절하는 판단은 같은 규칙을 씁니다. 예: 장바구니 묶음을 결제할 수 있는지는 `CartPayability` 하나를 화면 응답과 결제 준비가 함께 씁니다. 편 표시(교통수단 이름, 출발 일시)는 `TripDisplay` 하나를 장바구니와 예매 내역이 씁니다.
- **규칙 값은 한 곳에.** 정규식·범위·문구는 상수(`InputRules`, `AccountRules`)나 재사용 어노테이션(`@TagoId`, `@PaymentId`, `@UserName` 등)에 두고, DTO는 어노테이션만 붙입니다. 같은 검사를 다른 곳에 다시 쓰지 않습니다.
- **빠뜨리면 빌드가 실패하게.** 규칙은 사람의 기억 대신 구조 테스트로 강제합니다. 예: `InputValidationCoverageTests`는 모든 컨트롤러 입력에 제약이 선언됐는지, `npm run test:web`은 모든 HTML 대입이 `` html`` ``을 거치는지 검사합니다.
- **응집도는 높게, 결합도는 낮게.** 계층마다 한 가지 책임만 집니다.

  | 계층 | 책임 |
  |---|---|
  | DTO | 입력 형식 선언 |
  | 컨트롤러 | 요청을 받아 서비스로 넘기고 응답을 고름. 형식 검사를 직접 하지 않음 |
  | 서비스 | 업무 규칙 (중복 이메일, 당일 예매, 소유권 등) |
  | 클라이언트 | 외부 API 호출 (TAGO, PortOne, 카카오, 도로명주소) |

  한 파일은 한 관심사만 다룹니다. 예를 들어 교통수단 규칙은 `transport.js`, 출력 안전은 `safe-html.js`입니다.
- **읽기 쉽게.**
  - 이름은 의도를 드러냅니다.
  - 함수는 한 가지 일만 합니다.
  - 숫자와 문자열 상수에는 이름을 붙입니다.
  - 쓰지 않는 코드와 API는 지웁니다.
  - 주석은 "무엇"보다 "왜"를 적습니다.
- **리팩터링은 동작 변경과 커밋을 나눕니다.** 구조만 바꾼 커밋은 `refactor:`로 쓰고, 기존 테스트가 그대로 통과해야 합니다.

### 보안 규칙
기능을 고쳐도 깨지면 안 되는 보안 규칙은 [docs/SECURITY_RULES.md](docs/SECURITY_RULES.md)에 있고, `SecurityRegressionTests` 등 회귀 테스트로 고정되어 있습니다. 회귀 테스트를 지우거나 약하게 바꾸지 않습니다. 규칙을 바꿔야 하면 문서와 테스트를 같은 PR에서 고치고 이유를 적습니다.

### AI 도구 하네스
Claude Code 같은 AI 도구에는 [CLAUDE.md](CLAUDE.md)의 규칙과 `.claude/` 설정이 적용됩니다. `.claude/hooks/guard-secrets.js`가 AI 도구의 `.env` 접근과 환경변수 출력을 도구 단계에서 차단합니다.
