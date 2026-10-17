# 보안 규칙

기능을 고치거나 늘려도 **깨지면 안 되는 보안 규칙**과, 각 규칙을 지키는 회귀 테스트를 정리합니다. 규칙은 테스트로 고정되어 있어서, 어기는 변경은 `docker compose --profile test run --rm --build test`에서 실패합니다.

- 회귀 테스트를 지우거나 약하게 바꾸지 않습니다. 규칙 자체를 바꿔야 하면 이 문서와 테스트를 같은 PR에서 고치고, PR 본문에 이유를 적습니다.
- 새 API를 추가하면 아래 규칙에 해당하는지 확인하고 테스트에 추가합니다.

## 규칙과 상태

| #   | 규칙                                                                                                       | 상태                                          | 회귀 테스트                                                    |
| --- | ---------------------------------------------------------------------------------------------------------- | --------------------------------------------- | -------------------------------------------------------------- |
| S1  | 결제 금액은 서버가 정하고, PortOne에서 확인한 뒤에만 완료로 기록한다                                       | ✅ v2.2                                       | `PaymentVerificationRegressionTests`                           |
| S2  | 로그인하지 않으면 API·결제·개인 화면을 쓸 수 없다. API는 로그인 페이지로 보내지 않고 401 JSON으로 응답한다 | ✅ v2.1                                       | `SecurityRegressionTests.S2_AnonymousBlocked`                  |
| S3  | 상태를 바꾸는 요청(POST·PUT·DELETE)은 CSRF 토큰이 있어야 한다. 로그아웃은 POST로만 된다                    | ✅ v2.1                                       | `SecurityRegressionTests.S3_Csrf`, `CsrfCookieRegressionTests` |
| S4  | 다른 사용자의 장바구니·결제 기록은 보거나 바꿀 수 없다                                                     | ✅ v2.1                                       | `SecurityRegressionTests.S4_Ownership`                         |
| S5  | 응답에 예외 메시지·SQL·내부 주소 같은 내부 정보를 넣지 않는다                                              | ✅ v2.3                                       | `ErrorAndLogRegressionTests`                                   |
| S6  | 로그에 SQL 바인드 값·보안 디버그 로그·키를 남기지 않는다                                                   | ✅ v2.3                                       | `ErrorAndLogRegressionTests`, `LogSecretMaskingTests`          |
| S7  | 헬스체크: 실패하면 503, 로그인 없이 상태만 응답(데이터·내부 정보 없음), DB와 외부 API를 모두 확인          | ✅ v2.3                                       | `HealthRegressionTests`, `HealthServiceTests`                  |
| S8  | 컨테이너는 root가 아닌 사용자로 실행하고, 비밀번호를 명령 인자에 넣지 않는다                               | ✅ v2.3                                       | `npm run test:infra` (`scripts/infra-rules.test.js`)           |
| S9  | 서버용 키는 브라우저로 보내지 않는다. 브라우저에는 도메인 제한된 카카오 JavaScript 키만 허용한다          | ✅ v2.3                                       | `KeyExposureRegressionTests`                                   |

## S1. 결제 금액과 결제 완료 확인 (v2.2)

브라우저가 보낸 금액과 "결제됨" 알림은 믿지 않습니다.

1. **운임 확인 (담기·바로 결제):** 구간마다 편을 조회한 조건(교통수단·출발/도착 TAGO ID)과 고른 편(편 번호·출발 일시)을 받아, 서버가 TAGO를 다시 조회해 그 편의 운임과 같을 때만 받습니다. 편이 없거나 운임이 다르거나 당일 출발이 아니면 400입니다. 장바구니에는 확인된 항목만 `fare_verified = true`로 저장하고, 그 전에 담긴 항목은 결제하지 않습니다.
2. **결제 준비:** 서버가 금액을 합산해 결제 기록을 `PENDING`으로 먼저 남기고, 그 결제 번호·금액으로만 결제창을 엽니다.
3. **결제 완료:** 결제 번호만 받습니다. 자기 `PENDING` 기록이 없으면 404입니다. PortOne V2 결제 단건 조회(`GET /payments/{paymentId}`, API Secret은 서버에만)로 다음을 확인합니다.
   - `status = PAID`가 아니면 완료로 기록하지 않습니다 (`PENDING` 유지).
   - 금액(`amount.total`)·통화(`KRW`)·상점(`storeId`)이 준비 때 기록과 다르면 **PortOne 결제를 취소**하고 `FAIL`로 기록합니다.
   - 같은 묶음·교통수단이 이미 다른 결제로 `SUCCESS`이면 중복 결제로 보고 취소·`FAIL`합니다.
   - 모두 맞으면 `SUCCESS`로 바꾸고 장바구니 묶음을 비웁니다. 이미 `SUCCESS`인 결제는 다시 확인하지 않습니다.
4. **결제 실패 알림:** 자기 `PENDING` 기록만 `FAIL`로 바꿉니다. 새 기록을 만들거나 `SUCCESS`를 바꾸지 않습니다.
5. 마이페이지 이번 달 결제 합계는 `SUCCESS`만 셉니다.

남은 일: 결제 후 브라우저를 닫아 완료 알림이 오지 않으면 `PENDING`으로 남습니다. PortOne 웹훅으로 보완하는 것은 배포(v3) 때 다룹니다.

## S2. 로그인 필요 범위 (v2.1)

- 로그인 없이 열리는 것: `/`, `/home`, `/login`, `/register`, `/error`, 정적 파일(`/css/**`, `/javascript/**`, `/images/**`)
- 그 밖의 화면(`/route`, `/cart`, `/mypage` 등)은 로그인 페이지로 보냅니다.
- `/api/**`, `/payment/**`는 로그인하지 않으면 `401 {"error":"로그인이 필요합니다."}`를 돌려줍니다.
- 경로 검색·편 조회·장소 매칭도 지금은 로그인 사용자만 씁니다. 비로그인 사용은 호출 횟수 제한(TMAP 하루 10건 보호)과 함께 나중에 엽니다.

## S3. CSRF 토큰 전달 방식 (v2.1)

- 서버는 `XSRF-TOKEN` 쿠키(HttpOnly 아님)로 토큰을 내려보냅니다.
- 폼: Thymeleaf `th:action`이 숨은 `_csrf` 필드를 넣습니다. 로그인·회원가입 폼은 `_csrf` 필드를 직접 넣습니다.
- fetch: `static/javascript/csrf.js`가 같은 사이트로 보내는 GET 이외 요청에 `X-XSRF-TOKEN` 헤더를 붙입니다. fetch를 쓰는 화면은 이 파일을 다른 스크립트보다 먼저 불러옵니다.
- 로그아웃: 헤더의 로그아웃은 POST 폼 버튼입니다.

## S4. 소유권 (v2.1)

- 장바구니 조회·삭제·결제 준비는 로그인한 사용자의 이메일 조건으로만 찾습니다. 다른 사용자의 묶음은 없는 것처럼 동작합니다.
- 결제 실패 기록은 다른 사용자의 결제 번호면 404로 거절합니다(기록이 있다는 사실도 알리지 않음).
- 화면에서 쓰지 않던 장바구니 복원 API(`/payment/restore-cart`)는 지웠습니다.

## S5. 응답에 내부 정보를 넣지 않는다 (v2.3)

- 사용자에게 보여 줄 문구는 `UserFacingException`으로 던집니다. 응답(400)에는 이 예외의 메시지만 싣습니다.
- 라이브러리·프레임워크가 던진 `IllegalArgumentException`은 클래스 이름, 값 형식 같은 내부 정보가 섞일 수 있어 "요청 값이 올바르지 않습니다."로 바꾸고, 원문은 로그에만 남깁니다.
- 처리하지 못한 예외(500)는 일반 문구만 응답합니다. 로그에는 예외 종류와 메시지만 남기고, 키가 든 URL이 섞일 수 있는 스택트레이스는 남기지 않습니다.
- 스프링 기본 오류 응답도 메시지·스택트레이스·바인딩 오류·예외 이름을 넣지 않습니다(`server.error.include-*`).
- 예외 메시지를 응답에 내보내던 `/health/db`는 없앴습니다(S7).

## S6. 로그에 남기지 않는 것 (v2.3)

- SQL(`spring.jpa.show-sql`)은 콘솔에 찍지 않습니다. `.env`의 `SHOW_SQL`·`FORMAT_SQL`은 더 이상 쓰지 않습니다.
- 아래 로거는 루트 로그 수준과 상관없이 INFO로 고정합니다.
  - `org.springframework.security`: 요청·세션 정보를 남김
  - `org.hibernate.SQL`, `org.hibernate.orm.jdbc.bind`, `org.hibernate.type.descriptor.sql`: SQL과 바인드 값(개인정보, 비밀번호 해시)을 남김
  - `org.springframework.web.client`, `org.springframework.web.reactive.function.client`, `reactor.netty.http.client`: 키가 든 외부 호출 URL을 남김
- 로컬에서 SQL 문만 보려면 `SPRING_PROFILES_ACTIVE=sqldebug`를 씁니다. 이 프로필에서도 바인드 값은 켜지 않습니다.
- 요청 본문을 `System.out`으로 찍던 코드는 지웠습니다.
- 키 형태 마스킹은 로그 패턴과 `LogSecretMaskingTests`가 지킵니다(v1.x).

## S7. 헬스체크 (v2.3)

| 경로 | 확인하는 것 | 용도 |
| --- | --- | --- |
| `GET /health` | DB + 외부 API(TMAP, TAGO, 카카오, 도로명주소, PortOne) | 서비스 전체 상태 |
| `GET /health/live` | DB | 컨테이너 헬스체크(`docker-compose.yml`) |

- 로그인 없이 열리고, 응답은 `{"status":"UP"}`(200) 또는 `{"status":"DOWN"}`(503)뿐입니다. 어느 구성 요소가 왜 실패했는지는 응답에 넣지 않고 로그에만 남깁니다. `Cache-Control: no-store`입니다.
- **비로그인 사용자가 DB·외부 API를 건드릴 수 없게:**
  - DB는 연결이 유효한지만 봅니다(쿼리·데이터 없음). 결과를 5초 동안 재사용하므로 요청이 몰려도 연결 시도는 5초에 한 번입니다.
  - 외부 API는 요청 때 호출하지 않습니다. 30분마다 가벼운 호출로 점검해 둔 결과만 씁니다. 그래서 헬스체크 요청으로 외부 호출이나 한도 소모가 일어나지 않습니다.
- **외부 API 점검 방법:**
  - TAGO: 열차 도시 코드 목록
  - 카카오: 서울역 좌표 → 주소
  - 도로명주소: "서울역" 검색
  - PortOne: 없는 결제 번호 조회(404면 키 정상)
  - 한 번 실패하면 한 번 더 시도하고, 두 번 모두 실패해야 이상으로 기록합니다. 도로명주소 API는 연결의 절반가량이 시간 초과되는 때가 있었습니다.
  - **TMAP 대중교통은 하루 10건 한도라 점검 호출을 하지 않습니다.** 실제 경로 검색 결과로 판단합니다. 키 오류·한도 초과·응답 없음은 이상, 검색 결과 없음(400)은 정상입니다.
  - 아직 점검하지 않은 API는 이상으로 보지 않습니다. 키를 설정하지 않은 선택 API(카카오 REST, 도로명주소)는 점검하지 않습니다.
- **컨테이너는 `/health/live`를 씁니다.** 외부 API 장애나 TMAP 한도 소진으로 앱 컨테이너가 "unhealthy"가 되어 재시작되는 일을 막기 위해서입니다.

## S8. 컨테이너 (v2.3)

- 앱 이미지는 전용 사용자 `app`(uid 10001)으로 실행합니다. JAR은 root 소유라 앱이 자기 실행 파일을 바꿀 수 없습니다.
- MySQL 헬스체크는 앱 계정 비밀번호를 명령 인자가 아니라 `MYSQL_PWD` 환경변수로 넘깁니다. compose 파일에는 `$$MYSQL_PASSWORD`(컨테이너 안 변수 이름)만 있어 `docker inspect`에 값이 나오지 않습니다. 예전에는 `-p${DB_ROOT_PASSWORD}`로 root 비밀번호가 인자에 들어갔습니다.
- 앱 컨테이너에 `/health/live` 헬스체크를 추가했습니다.
- `scripts/infra-rules.test.js`(`npm run test:infra`)가 Dockerfile 실행 단계의 `USER`, compose 명령 인자의 비밀번호, 앱 헬스체크 경로를 검사합니다. Gradle 테스트 컨테이너에는 이 파일들이 들어가지 않아 Node로 검사합니다.
- 남은 일(v3): 앱 컨테이너는 `env_file: .env`로 쓰지 않는 값(`DB_ROOT_PASSWORD` 등)까지 받습니다. 배포 때 필요한 변수만 넘기도록 나눕니다.

## S9. 브라우저로 가는 키 (v2.3)

화면 소스·JS·응답은 사용자가 모두 볼 수 있으므로, 브라우저로 보낸 키는 숨길 수 없습니다. 그래서 키마다 **브라우저로 보내도 되도록 설계된 키인지**를 공식 문서와 콘솔 설정으로 확인하고 정했습니다.

| 키 | 제공사의 보호 방식 (확인한 근거) | 결론 |
| --- | --- | --- |
| 카카오 JavaScript 키 | 공식 지도 가이드가 `sdk.js?appkey=`로 브라우저에 넣음. 콘솔 설명: "등록한 도메인에서만 JavaScript 키로 Kakao SDK for JavaScript를 사용할 수 있습니다" | 브라우저 허용 (도메인 등록 필수) |
| TMAP appKey | SK open API 콘솔: 앱마다 키 하나, 신청한 상품(TMAP, TMAP 대중교통)이 같은 키에 묶임. 제한은 **IP 허용 목록뿐**, 도메인 제한 없음 | 서버 전용 (배포 때 서버 IP만 허용) |
| 카카오 REST 키 | 클라이언트 시크릿·호출 허용 IP 설정이 붙는 서버용 키 | 서버 전용 |
| PortOne V2 API Secret, 공공데이터·도로명주소 키, DB 비밀번호 | 비밀값 | 서버 전용 |
| PortOne `storeId`·`channelKey` | 결제창 SDK에 넘기는 식별자. 결제 확인·취소에는 쓸 수 없음 | 브라우저 허용 (키가 아님) |

**바꾼 것**
- 경로 지도를 TMAP 지도 SDK에서 **카카오맵**으로 바꿨습니다. TMAP이 주는 좌표가 위경도(WGS84)라 경로 선을 그대로 옮겨 그립니다. 경로 계산은 그대로 서버가 TMAP으로 합니다.
- 홈 화면이 쓰지 않으면서 불러오던 TMAP 지도 SDK를 지웠습니다. 이 때문에 **로그인하지 않은 사용자도 홈 소스에서 하루 10건 한도의 TMAP 키를 볼 수 있었습니다.**
- `FrontendKeyAdvice`는 카카오 JavaScript 키만 템플릿에 넣습니다.

**회귀 테스트 (`KeyExposureRegressionTests`)**
- 로그인 전(`/`, `/home`, `/login`, `/register`)과 후(`/route`, `/cart`, `/mypage`, `/mypage/edit`) 화면, 모든 정적 JS를 실제로 렌더링해 **실제 설정값**과 비교합니다. 서버용 키가 한 글자라도 들어 있으면 실패합니다.
- 카카오 JavaScript 키는 카카오 지도 SDK 주소 안에서만 쓰여야 합니다.
- 외부 스크립트는 카카오 지도 SDK와 PortOne SDK만 허용하고, TMAP 주소(`apis.openapi.sk.com`)는 금지합니다.
- 실패 메시지에는 키 이름과 화면 경로만 나오고, 키 값이나 본문은 출력하지 않습니다.
- 예전 코드(홈에 TMAP SDK)로 되돌려 실행하면 `서버용 키 TMAP_APPKEY 가 / 응답에 들어 있다`로 실패하는 것을 확인했습니다.

**배포(v3) 때 할 일**
- 카카오 JavaScript SDK 도메인에 운영 도메인 추가 (현재 `http://localhost:8080`만 등록)
- SK appKey의 IP 허용 목록에 서버 IP만 등록 (현재 "Any IP allowed")
- 필요하면 카카오 REST 키에도 호출 허용 IP 등록
