# `.env` 설정 가이드

MobilLax가 사용하는 환경 변수와 각 키의 발급 방법을 정리한 문서입니다.
모든 키는 `.env` 한 곳에서만 관리합니다. 템플릿은 `.env.example`입니다.

> 기준일: 2026-10-14. 외부 콘솔의 화면과 메뉴 이름은 바뀔 수 있으니 각 절의 공식 문서 링크를 함께 확인하세요.

---

## 1. 한눈에 보기

| 구분 | 변수 | 발급처 | 사용 위치 | 필수 |
|---|---|---|---|---|
| DB | `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`, `DB_ROOT_PASSWORD` | 직접 정함 | docker compose MySQL, Spring datasource | ✅ |
| TMAP | `TMAP_APPKEY` | SK open API | 서버: `RouteServiceImpl` (대중교통 경로) / 브라우저: `home`·`route` 템플릿 지도 SDK, `map.html`(`/module/config.js`) POI·자동차 경로 | ✅ |
| 카카오 | `KAKAO_JS_APPKEY` | Kakao Developers | 브라우저: `home` 템플릿 카카오 지도 SDK | ✅ |
| 포트원 | `PORTONE_STORE_ID` | 포트원 관리자콘솔 | 결제창 호출 (`PaymentController` → `payment.js`) | ✅ |
| 포트원 | `PORTONE_CHANNEL_KEY` | 포트원 관리자콘솔 | 결제창 호출 | ✅ |
| 포트원 | `PORTONE_APPKEY` | 포트원 관리자콘솔 (V2 API Secret) | 아직 미사용 (서버 결제 검증용으로 v2에서 사용 예정) | ⬜ |
| 공공데이터 | `BUS_API_KEY` | 공공데이터포털 | 서버: `TerminalService` (`/api/terminals`, 시외버스 터미널 조회) | ⬜ |
| 공공데이터 | `BUS_API_URL_CTYCODE`, `BUS_API_URL_TERMINAL`, `BUS_API_URL_SCHEDULE` | 고정 URL | 위와 동일 | `.env.example` 값 그대로 |

나머지 변수(`SPRING_*`, `JPA_*`, `LOGGING_*` 등)는 `.env.example`의 값을 그대로 두면 됩니다.

### 작성 규칙
- 값 뒤에 주석(`KEY=value   # 설명`)을 붙이지 마세요. 파서에 따라 주석까지 값으로 읽힙니다. 주석은 윗줄에 씁니다.
- 따옴표 없이 `KEY=value` 형식으로 씁니다.

---

## 2. 데이터베이스 (6개)

로컬 Docker의 MySQL 컨테이너를 씁니다. 외부에서 발급받는 값은 없고 직접 정합니다.

| 변수 | 권장 값 | 설명 |
|---|---|---|
| `DB_HOST` | `db` | docker compose 의 MySQL 서비스 이름. 앱 컨테이너가 이 이름으로 접속 |
| `DB_PORT` | `3306` | MySQL 기본 포트 |
| `DB_NAME` | `MobilLaxDB` | 컨테이너 최초 기동 시 자동 생성되는 DB |
| `DB_USERNAME` | `mobillax` | 앱이 쓰는 일반 계정 (최초 기동 시 자동 생성) |
| `DB_PASSWORD` | 무작위 문자열 | 위 계정의 비밀번호 |
| `DB_ROOT_PASSWORD` | 무작위 문자열 | MySQL root 비밀번호 (헬스체크·관리용) |

비밀번호 생성 예시 (PowerShell):

```powershell
[Convert]::ToBase64String((1..18 | ForEach-Object { Get-Random -Max 256 }))
```

> ⚠️ **비밀번호를 바꿀 때 주의**
> MySQL 이미지는 **볼륨이 비어 있는 최초 기동 때만** 계정과 비밀번호를 만듭니다. 이미 DB가 만들어진 뒤에 `.env`의 `DB_PASSWORD`만 바꾸면 앱이 접속하지 못합니다.
> 로컬 데이터가 없어져도 된다면 볼륨을 지우고 다시 만드세요.
> ```powershell
> docker compose down -v   # mysql-data 볼륨 삭제 (DB 데이터 전부 삭제)
> docker compose up -d --build
> ```

MySQL Workbench로 접속하려면 `127.0.0.1:3306`, 사용자 `DB_USERNAME`(또는 `root`)으로 접속합니다.

---

## 3. TMAP (SK open API) — `TMAP_APPKEY`

- 사이트: https://openapi.sk.com
- 대중교통 API 이용 절차: https://transit.tmapmobility.com/guide/procedure
- 사용하는 API
  - 대중교통 경로: `POST https://apis.openapi.sk.com/transit/routes`
  - 자동차 경로, POI 검색, 지도 JS SDK(`/tmap/jsv2`)

### 발급 절차
1. **회원가입/로그인:** openapi.sk.com에서 T아이디로 로그인하거나 회원가입합니다. 개인회원을 선택합니다.
2. **앱 만들기:** 우측 상단 **대시보드** → 좌측 **[앱]** → **[앱 만들기]**에서 앱 이름(예: `MobilLax`)을 입력합니다.
3. **상품 신청:** 아래 **두 상품을 모두** 같은 앱에 신청합니다. 각 상품 상세 → **[API 사용 요금]** → **[사용하기]**로 들어가 FREE 요금제를 고르고, 약관에 동의한 뒤 **[사용 신청하기]**를 누릅니다.
   - **TMAP:** 지도 SDK, POI 검색, 자동차 경로
   - **TMAP 대중교통:** 대중교통 경로(`transit/routes`). **별도 상품이라 TMAP만 신청하면 대중교통 호출이 403 `INVALID_API_KEY`로 거절됩니다.**
   - ⚠️ TMAP 대중교통 FREE 요금제는 **하루 10건**이고, 넘으면 자동으로 차단됩니다.
   - 신청 결과는 **대시보드 → 요금 조회 → 사용상품관리**에서 확인합니다.
   - 무료 제공량과 초과 과금은 상품 상세의 요금표에서 확인하세요. 대중교통 API는 무료 호출량이 적은 편이니 개발할 때 반복 호출을 조심하세요.
4. **appKey 확인:** **대시보드 → [앱] → 만든 앱**의 상세 화면에서 appKey를 복사합니다.

### `.env` 입력
```
TMAP_APPKEY=<발급받은 appKey>
```
- 키 하나로 서버 호출과 브라우저 지도 SDK를 모두 처리합니다.
- 배포(v3) 때 브라우저용 키를 도메인 제한이나 호출량 분리 목적으로 따로 두어야 하면 그때 나눕니다.

---

## 4. 카카오 지도 — `KAKAO_JS_APPKEY`

- 사이트: https://developers.kakao.com
- 공식 문서: [카카오맵 시작하기](https://developers.kakao.com/docs/ko/kakaomap/common), [앱 설정 > 앱 키·JavaScript SDK 도메인](https://developers.kakao.com/docs/ko/app-setting/app)

### 발급 절차
1. **로그인:** developers.kakao.com에 카카오 계정으로 로그인합니다. 처음이면 개발자 등록 약관에 동의합니다.
2. **앱 생성:** 상단 **[내 애플리케이션]**(앱 관리 페이지 https://developers.kakao.com/console/app)에서 **[애플리케이션 추가하기]**를 눌러 앱 이름과 회사명(개인이면 본인 이름)을 입력합니다.
3. **카카오맵 사용 설정 (필수):** 앱 관리 페이지에서 **[카카오맵] → [사용 설정]**으로 들어가 **[상태]를 ON**으로 켭니다. 켜지 않으면 키가 있어도 지도가 뜨지 않습니다.
4. **JavaScript 키 확인:** **[앱] → [플랫폼 키] → [JavaScript 키]**에서 키를 복사합니다.
   - REST API 키나 네이티브 앱 키가 아니라 **JavaScript 키**여야 합니다. 지도 SDK에 다른 키를 넣으면 에러가 납니다.
5. **JavaScript SDK 도메인 등록:** **[앱] → [플랫폼 키] → [JavaScript 키] → [JavaScript SDK 도메인]**에 `http://localhost:8080`을 등록합니다.
   - 등록되지 않은 도메인에서 오는 요청은 거절됩니다.
   - 최대 10개까지 등록할 수 있고, `http`와 `https` 중 하나만 등록해도 둘 다 허용됩니다.
   - v3에서 배포할 때 운영 도메인을 추가합니다.

### 무료 쿼터 주의
- 무료 쿼터는 개발자 계정 기준 **첫 번째로 활성화한 앱에만** 제공됩니다. 두 번째 이후 앱은 비즈월렛을 연결하고 유료 API 사용 설정을 해야 합니다.
- 무료 쿼터가 적용된 앱에는 [카카오맵 무료 쿼터] 뱃지가 표시됩니다. 사용량은 **[통계] → [쿼터]**에서 볼 수 있고, 쿼터를 넘기면 429 에러가 납니다.
- 2026-07-21부터 사용 방법과 무료 쿼터 제공 방식이 바뀌었습니다. 이미 팀 시절 앱이 있다면 새 앱을 만들기 전에 이 조건을 확인하세요.

### `.env` 입력
```
KAKAO_JS_APPKEY=<JavaScript 키>
```

---

## 5. 포트원 V2 결제 — `PORTONE_STORE_ID`, `PORTONE_CHANNEL_KEY`, `PORTONE_APPKEY`

- 관리자콘솔: https://admin.portone.io
- 공식 문서: [연동 정보(상점 아이디·채널·API Keys)](https://developers.portone.io/opi/ko/console/guide/channel-manage?v=v2), [결제 연동 퀵 가이드](https://developers.portone.io/opi/ko/quick-guide/payment?v=v2)
- 이 프로젝트는 브라우저 SDK `https://cdn.portone.io/v2/browser-sdk.js`로 `PortOne.requestPayment({ storeId, channelKey, payMethod: "CARD" })`를 호출합니다.

### 5-1. 상점 아이디 → `PORTONE_STORE_ID`
1. admin.portone.io에 가입하고 로그인합니다. 가입하면 대표 상점이 자동으로 만들어집니다.
2. **결제 연동 → 연동 관리** 화면에서 **상점 아이디**(`store-...` 형식)를 복사합니다.

### 5-2. 테스트 채널 → `PORTONE_CHANNEL_KEY`
1. **결제 연동 → 연동 관리 → 채널 관리**로 이동합니다.
2. 연동 모드를 **테스트**로 바꿉니다. 실제 돈이 나가지 않습니다.
3. 결제대행사를 고릅니다. 공용 테스트 MID를 주는 PG(예: KG이니시스, 토스페이먼츠)를 고르면 계약 없이 바로 테스트할 수 있습니다.
4. 결제 방식과 결제 수단으로 **일반결제 / 카드**에 맞는 결제 모듈을 고르고 **[다음]**을 누릅니다. 코드가 `payMethod: "CARD"`를 씁니다.
5. 채널 이름을 입력하고 채널 속성은 **결제**를 고릅니다.
6. 테스트용 상점 아이디(MID)는 제공되는 **공용 MID**를 고르고, PG별 필수 항목과 과세 구분을 입력한 뒤 **[저장]**을 누릅니다.
7. 채널 목록에서 방금 만든 채널의 **채널 키**(`channel-key-...` 형식)를 복사합니다.

### 5-3. V2 API Secret → `PORTONE_APPKEY` (선택)
1. **결제 연동 → 연동 관리 → 식별코드·API Keys**에서 **[V2 API]** 탭을 엽니다. Owner나 Admin 권한이 필요합니다.
2. **[새로운 API Secret 발급]**을 누르고 이름과 만료 기한을 정해 **[생성]**합니다.
3. Secret은 **발급 직후 한 번만** 보이니 바로 복사해서 `.env`에 넣으세요. 잃어버리면 새로 발급해야 합니다.
   - 채널 설정에 입력하는 **PG사 시크릿과는 다른 값**입니다. 헷갈리면 API 호출 때 401 에러가 납니다.
   - 서버에서만 써야 하는 민감 정보입니다. 브라우저에 절대 내려보내지 마세요.
   - 현재 코드에서는 아직 쓰지 않습니다. v2에서 서버 측 결제 검증(결제 금액 위변조 확인)에 쓸 예정입니다.

### `.env` 입력
```
PORTONE_STORE_ID=store-xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx
PORTONE_CHANNEL_KEY=channel-key-xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx
PORTONE_APPKEY=<V2 API Secret>
```

---

## 6. 공공데이터포털 시외버스 — `BUS_API_KEY` (선택)

- 데이터: [국토교통부_(TAGO)_시외버스정보](https://www.data.go.kr/data/15098541/openapi.do) (제공: 국토교통부)
- 활용신청은 **자동승인**이고, 개발계정 트래픽은 **10,000**입니다.
- 이 키가 없어도 앱은 뜹니다. 시외버스 터미널 조회(`/api/terminals`)만 동작하지 않습니다.

### 발급 절차
1. https://www.data.go.kr에 회원가입하고 로그인합니다.
2. 위 데이터 페이지에서 **[활용신청]**을 누르고, 활용 목적(예: 개인 학습용 웹 서비스 개발)을 적은 뒤 신청합니다.
3. **마이페이지 → 데이터활용 → Open API → 활용신청 현황**에서 해당 API를 열고, **일반 인증키(Encoding)** 값을 복사합니다.
   - 이 코드는 키를 URL에 그대로 붙이므로(`?serviceKey=%s`) **이미 인코딩된 Encoding 키**를 써야 합니다.
   - 승인 직후에는 키가 반영되지 않아 `SERVICE_KEY_IS_NOT_REGISTERED_ERROR`가 날 수 있습니다. 잠시 뒤 다시 시도하세요.

### `.env` 입력
```
BUS_API_KEY=<일반 인증키(Encoding)>
BUS_API_URL_CTYCODE=http://apis.data.go.kr/1613000/SuburbsBusInfoService/getCtyCodeList
BUS_API_URL_TERMINAL=http://apis.data.go.kr/1613000/SuburbsBusInfoService/getSuberbsBusTrminlList
BUS_API_URL_SCHEDULE=http://apis.data.go.kr/1613000/SuburbsBusInfoService/getStrtpntAlocFndSuberbsBusInfo
```

---

## 7. 적용과 확인

`.env`만 바꿨을 때는 다시 빌드할 필요 없이 앱 컨테이너만 다시 만들면 됩니다.

```powershell
docker compose up -d --force-recreate app
```

| 확인 항목 | 방법 |
|---|---|
| 앱 기동 | `docker compose logs app` 에 `Started MobilLaxApplication` |
| TMAP 브라우저 키 | http://localhost:8080/module/config.js 에 새 키가 보이는지 |
| 카카오 지도 | http://localhost:8080/home 에서 지도 표시, 브라우저 개발자도구 콘솔에 에러가 없는지 |
| TMAP 경로 | 로그인 → 출발지/도착지 검색 → 경로 결과 표시 |
| 포트원 | 경로 상세 또는 장바구니에서 결제 → 테스트 결제창이 뜨는지 |
| 시외버스 | http://localhost:8080/api/terminals?name=동서울 (로그인 불필요) |

---

## 8. 브라우저에 노출되는 키에 대해

`TMAP_APPKEY`(지도 SDK용으로 내려가는 값)와 `KAKAO_JS_APPKEY`는 구조상 사용자 브라우저에 그대로 보입니다. `.env`로 옮긴 것은 **저장소에 키가 커밋되지 않게** 하려는 것이고, 브라우저 노출까지 막지는 않습니다.

- **카카오 JavaScript 키:** 원래 공개를 전제로 한 키입니다. **JavaScript SDK 도메인 등록**으로 다른 사이트에서 쓰지 못하게 막습니다.
- **TMAP 키:** 현재 브라우저 JS(`carRoute.js`, `LocationSearch.js` 등)가 REST API를 직접 호출하고 있어서 키가 노출됩니다. v2에서 이 호출을 서버로 옮겨(백엔드 프록시) REST 키는 서버에만 두고, 브라우저에는 지도 SDK 키만 남길 예정입니다.
- **포트원 `storeId`·`channelKey`:** 공개해도 되는 식별자입니다. `PORTONE_APPKEY`(API Secret)는 절대 브라우저로 보내면 안 됩니다.
