# 공공데이터포털 API 정리

MobilLax가 결제 대상으로 삼는 장거리 교통수단(**기차, 고속버스, 시외버스, 항공**)의 시간표와 운임을 조회하는 공공데이터포털 API를 정리한 문서입니다. 키 발급 절차는 [ENV_SETUP.md 6장](ENV_SETUP.md#6-공공데이터포털-시외버스--bus_api_key-선택)을 따릅니다.

- 조사일: 2026-10-15. 요청·응답 필드는 각 데이터 페이지의 "활용명세"(Swagger)에서 확인했습니다.
- 응답 필드 이름은 API마다 대소문자 규칙이 다릅니다(열차는 `depplandtime`, 버스·항공은 `depPlandTime`). 그대로 옮겨 적었습니다.

## 1. 결제 대상과 API의 관계

| 구분 | 교통수단 | MobilLax에서 | 데이터 출처 |
|---|---|---|---|
| 결제 대상 | 기차(KTX·SRT·ITX 등), 고속버스, 시외버스, 항공 | 장바구니 담기·바로 결제 (PortOne 테스트 결제) | TMAP 경로 + 아래 TAGO API |
| 안내 전용 | 시내버스, 지하철, 광역버스, 택시, 자전거 | 경로와 예상 요금만 안내. 결제하지 않음 | TMAP 경로 |

- 실제 예매·발권은 운송사와 제휴해야 하므로, 이 프로젝트는 **발권이 가능하다고 가정**하고 PortOne 테스트 결제까지만 합니다.
- TMAP 경로의 장거리 구간에는 구간 요금(`routePayment`)이 이미 들어 있습니다. TAGO API는 **편명·출발 시각 선택, 등급별 운임 확인**처럼 TMAP에 없는 정보를 보완하는 데 씁니다.

## 2. 활용신청 목록

모두 제공기관은 국토교통부(TAGO)이고, 무료이며, 개발·운영 모두 **자동승인**, 개발계정 트래픽은 하루 **10,000건**입니다.

| 우선순위 | 데이터 | 데이터 ID | 서비스 주소 | 상태 |
|---|---|---|---|---|
| 필수 | [국토교통부_(TAGO)_열차정보](https://www.data.go.kr/data/15098552/openapi.do) | 15098552 | `https://apis.data.go.kr/1613000/TrainInfo` | 신청 필요 |
| 필수 | [국토교통부_(TAGO)_고속버스정보](https://www.data.go.kr/data/15098522/openapi.do) | 15098522 | `https://apis.data.go.kr/1613000/ExpBusInfo` | 신청 필요 |
| 필수 | [국토교통부_(TAGO)_시외버스정보](https://www.data.go.kr/data/15098541/openapi.do) | 15098541 | `https://apis.data.go.kr/1613000/SuburbsBusInfo` | 신청됨 (터미널 조회에 사용 중) |
| 필수 | [국토교통부_(TAGO)_국내항공운항정보](https://www.data.go.kr/data/15098526/openapi.do) | 15098526 | `https://apis.data.go.kr/1613000/DmstcFlightNvgInfo` | 신청 필요 |

신청하지 않는 것과 이유:

| 데이터 | 이유 |
|---|---|
| [(TAGO)_고속버스도착정보](https://www.data.go.kr/data/15098516/openapi.do) | 운행 중 위치·도착 예정 정보. 예매·결제 흐름에는 필요 없음. 실시간 정보 기능을 만들 때 다시 검토 |
| [(TAGO)_지하철정보](https://www.data.go.kr/data/15098554/openapi.do) | 지하철은 안내 전용이고, TMAP 경로로 충분 |
| (TAGO)_버스노선·정류소·위치정보 | 시내버스는 안내 전용 |
| 고속·시외버스·도시철도이용 OD | 이용 통계. 서비스 기능과 무관 |

### 인증키
- 공공데이터포털 인증키는 **서비스별이 아니라 회원 계정별로 하나**입니다. 계정에서 API마다 활용신청만 하면, 이미 쓰고 있는 같은 키(`.env`의 `BUS_API_KEY`)로 모든 API를 호출할 수 있습니다.
- 활용신청 직후에는 키가 반영되지 않아 `SERVICE_KEY_IS_NOT_REGISTERED_ERROR`가 날 수 있습니다. 잠시 뒤 다시 호출합니다.

### 공통 요청 파라미터
모든 오퍼레이션에 `serviceKey`(필수), `pageNo`, `numOfRows`, `_type`(`json` 또는 `xml`)이 있습니다. 아래 표에서는 생략합니다. `*`는 필수입니다.

### 공통 오류 코드
| 코드 | 뜻 |
|---|---|
| 10 | 잘못된 요청 파라미터 |
| 12 | 서비스가 없거나 폐기됨 (주소 오류) |
| 20 | 서비스 접근 거부 (활용신청 안 됨) |
| 22 | 일일 트래픽 초과 |
| 23 | 초당 트래픽 초과 |
| 30 | 등록되지 않은 키 |
| 31 | 기한이 만료된 키 |

## 3. 열차정보 — `TrainInfo`

| 오퍼레이션 | 용도 | 요청 | 응답 |
|---|---|---|---|
| `GetCtyCodeList` | 시/도 코드 목록 | 없음 | `citycode`, `cityname` |
| `GetCtyAcctoTrainSttnList` | 시/도별 기차역 목록 | `cityCode*` | `nodeid`(역 ID), `nodename`(역 이름) |
| `GetVhcleKndList` | 차량 종류(KTX, ITX-새마을 등) | 없음 | `vehiclekndid`, `vehiclekndnm` |
| **`GetStrtpntAlocFndTrainInfo`** | 출발역·도착역 기준 열차 시간표와 운임 | `depPlaceId*`, `arrPlaceId*`, `depPlandTime`(YYYYMMDD, 없으면 오늘), `trainGradeCode` | `trainno`, `traingradename`, `depplandtime`·`arrplandtime`(YYYYMMDDHHMISS), `depplacename`, `arrplacename`, `adultcharge`(운임, 원) |

- 어른 운임(`adultcharge`) 하나만 옵니다. 좌석 등급(특실)이나 할인 운임은 없습니다.
- ⚠️ TMAP은 SRT 구간도 돌려줍니다. 이 API에서 SRT 열차가 조회되는지는 실제 호출로 확인이 필요합니다.

## 4. 고속버스정보 — `ExpBusInfo`

| 오퍼레이션 | 용도 | 요청 | 응답 |
|---|---|---|---|
| `GetCtyCodeList` | 도시 코드 목록 | 없음 | `cityCode`, `cityName` |
| `GetExpBusTrminlList` | 고속버스 터미널 목록 | `terminalNm` | `terminalId`, `terminalNm` |
| `GetExpBusGradList` | 고속버스 등급(일반·우등·프리미엄) | 없음 | `gradeId`, `gradeNm` |
| **`GetStrtpntAlocFndExpbusInfo`** | 출발·도착 터미널 기준 배차와 운임 | `depTerminalId*`, `arrTerminalId*`, `depPlandTime`(YYYYMMDD, 없으면 오늘), `busGradeId` | `routeId`, `gradeNm`, `depPlandTime`·`arrPlandTime`(YYYYMMDDHHMI), `depPlaceNm`, `arrPlaceNm`, `charge`(운임, 원) |

## 5. 시외버스정보 — `SuburbsBusInfo`

| 오퍼레이션 | 용도 | 요청 | 응답 |
|---|---|---|---|
| `GetCtyCodeList` | 도시 코드 목록 | 없음 | `cityCode`, `cityName` |
| `GetSuberbsBusTrminlList` | 시외버스 터미널 목록 (현재 `/api/terminals`에서 사용) | `terminalNm`, `cityCode` | `terminalId`, `terminalNm`, `cityName` |
| `GetSuberbsBusGradList` | 시외버스 등급 | 없음 | `gradeId`, `gradeNm` |
| **`GetStrtpntAlocFndSuberbsBusInfo`** | 출발·도착 터미널 기준 배차와 운임 | `depTerminalId*`, `arrTerminalId*`, `depPlandTime`(YYYYMMDD) | `routeId`, `gradeNm`, `depPlandTime`·`arrPlandTime`(YYYYMMDDHHMI), `depPlaceNm`, `arrPlaceNm`, `charge`(운임, 원) |

> ⚠️ **당일 배차만 제공:** 데이터 설명에 "현재 당일 배차정보만 제공하고 있습니다(추후 기간 연장 예정)"라고 되어 있습니다. 요청에 날짜(`depPlandTime`)를 넣을 수는 있지만 오늘 이외의 날짜는 결과가 없을 수 있습니다.
>
> 시외버스는 **요일마다 운행편이 다른 노선이 실제로 있습니다.** 예를 들어 철원군 시외버스 시간표에는 "월요일만 운행", "화·수·목 운행 안 함" 편이 있고, 공주시 시간표에는 "13:20(목~월)"처럼 운행 요일이 표시된 편이 있습니다. 그래서 오늘 배차를 다른 날짜에 그대로 쓰면 틀릴 수 있습니다. 미래 날짜의 시외버스는 TMAP 경로의 정보만 보여 주거나 "당일만 시간 조회 가능"으로 안내해야 합니다.

## 6. 국내항공운항정보 — `DmstcFlightNvgInfo`

| 오퍼레이션 | 용도 | 요청 | 응답 |
|---|---|---|---|
| `GetArprtList` | 공항 목록 | 없음 | `airportId`, `airportNm` |
| `GetAirmanList` | 항공사 목록 | 없음 | `airlineId`, `airlineNm` |
| **`GetFlightOpratInfoList`** | 출발·도착 공항 기준 운항편과 운임 | `depAirportId*`, `arrAirportId*`, `depPlandTime`(YYYYMMDD), `airlineId` | `vihicleId`(편명), `airlineNm`, `depPlandTime`·`arrPlandTime`(YYYYMMDDHHMI), `depAirportNm`, `arrAirportNm`, `economyCharge`(일반석), `prestigeCharge`(비즈니스석) |

- 응답의 편명 필드는 철자가 `vihicleId`입니다(`vehicle`이 아님).

## 7. TMAP 구간과 연결하기

TMAP 대중교통 응답의 장거리 구간(`leg`)에서 아래 값을 꺼내 TAGO 조회에 씁니다. 실제 응답은 2026-10-15 서울역 → 부산역 조회에서 확인했습니다.

| TMAP `mode` | TMAP `type` | `route` 예시 | 구분 | TAGO API |
|---|---|---|---|---|
| `TRAIN` | 1 | `KTX:서울-천안아산` | KTX | 열차정보 |
| `TRAIN` | 11 | `SRT:서울-부산` | SRT | 열차정보 (SRT 조회 여부 확인 필요) |
| `EXPRESSBUS` | 23 | `고속버스:서울경부-진주개양` | **고속버스** | 고속버스정보 |
| `EXPRESSBUS` | 22 | `시외버스:진주(개양)-부산심야(부산역)` | **시외버스** | 시외버스정보 |
| `AIRPLANE` | 없음 | `항공:김포-김해` | 항공 | 국내항공운항정보 |

- TMAP은 고속버스와 시외버스를 같은 `EXPRESSBUS`로 보내므로 **`type`(23 고속, 22 시외) 또는 `route` 앞부분으로 구분**합니다.
- TMAP 구간의 `start.name`·`end.name`(역·터미널·공항 이름)으로 목록 API에서 ID를 찾은 뒤 시간표를 조회합니다. 이름이 정확히 같지 않을 수 있으므로(예: `서울고속버스터미널` ↔ `서울경부`) 이름 매칭 규칙은 구현할 때 정합니다.
- 경로 전체 요금(`fare.regular.totalFare`) = 장거리 구간 `routePayment`의 합 + 시내 교통 요금(환승 할인이 적용된 합계). 그래서 **시내 교통 예상 요금 = 전체 요금 − 장거리 구간 요금의 합**입니다.
