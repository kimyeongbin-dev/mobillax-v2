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
| 필수 | [국토교통부_(TAGO)_열차정보](https://www.data.go.kr/data/15098552/openapi.do) | 15098552 | `https://apis.data.go.kr/1613000/TrainInfo` | 신청됨 |
| 필수 | [국토교통부_(TAGO)_고속버스정보](https://www.data.go.kr/data/15098522/openapi.do) | 15098522 | `https://apis.data.go.kr/1613000/ExpBusInfo` | 신청됨 |
| 필수 | [국토교통부_(TAGO)_시외버스정보](https://www.data.go.kr/data/15098541/openapi.do) | 15098541 | `https://apis.data.go.kr/1613000/SuburbsBusInfo` | 신청됨 (터미널 조회에 사용 중) |
| 필수 | [국토교통부_(TAGO)_국내항공운항정보](https://www.data.go.kr/data/15098526/openapi.do) | 15098526 | `https://apis.data.go.kr/1613000/DmstcFlightNvgInfo` | 신청됨 |

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
- SRT(수서 출발)도 조회됩니다. 다만 등급명(`traingradename`)이 `SRT`가 아니라 `KTX`로 옵니다.

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

> ⚠️ 데이터 설명에는 "현재 당일 배차정보만 제공"이라고 되어 있지만, 실제 호출에서는 **모레까지** 조회됐습니다. → [8장](#8-날짜-범위-정책)

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
| `TRAIN` | 11 | `SRT:서울-부산` | SRT | 열차정보 (등급명은 `KTX`로 옴) |
| `EXPRESSBUS` | 23 | `고속버스:서울경부-진주개양` | **고속버스** | 고속버스정보 |
| `EXPRESSBUS` | 22 | `시외버스:진주(개양)-부산심야(부산역)` | **시외버스** | 시외버스정보 |
| `AIRPLANE` | 없음 | `항공:김포-김해` | 항공 | 국내항공운항정보 |

- TMAP은 고속버스와 시외버스를 같은 `EXPRESSBUS`로 보내므로 **`type`(23 고속, 22 시외) 또는 `route` 앞부분으로 구분**합니다.
- TMAP 구간의 `start.name`·`end.name`(역·터미널·공항 이름)으로 목록 API에서 ID를 찾은 뒤 시간표를 조회합니다. 이름이 정확히 같지 않을 수 있으므로(예: `서울고속버스터미널` ↔ `서울경부`) 이름 매칭 규칙은 구현할 때 정합니다.
- 경로 전체 요금(`fare.regular.totalFare`) = 장거리 구간 `routePayment`의 합 + 시내 교통 요금(환승 할인이 적용된 합계). 그래서 **시내 교통 예상 요금 = 전체 요금 − 장거리 구간 요금의 합**입니다.

## 8. 날짜 범위 정책

결정일: 2026-10-15

### 원칙: 예매는 당일 출발편만, 조회는 다른 날도 가능
| 구분 | 범위 |
|---|---|
| **예매** (장바구니 담기·바로 결제) | 모든 교통수단 **당일 출발편만** |
| **조회** (경로·시간표·운임 보기) | 경로 검색은 오늘부터 14일 안의 원하는 일시로 가능 (TMAP `searchDttm`, v1.4에서 구현). 편·운임은 아래 API 조회 범위 안에서 가능 |

- 교통수단마다 조회 범위가 달라 예매 가능 날짜를 맞추면 규칙이 복잡해집니다. 그래서 예매는 **당일**로 고정합니다.
- 조회는 **API가 돌려준 정보만** 보여 줍니다. 다른 날짜의 시간표로 추정하거나 채워 넣지 않습니다. 조회 범위 밖의 날짜는 편·운임 없이 TMAP 경로(소요 시간, 구간 요금)만 보여 줍니다.
- 실제 예매·발권은 운송사(코레일·SR·버스 통합예매·항공사)와 제휴해야 합니다. 이 프로젝트는 **제휴가 된다고 가정**합니다. 제휴 이후에는 운송사 예매 시스템이 미래 날짜의 시간표·좌석을 주므로 **미래 날짜 예매도 가능**하다고 봅니다.

### API별 실제 조회 범위
2026-10-15(목)에 실제로 호출해 확인했습니다. 결과코드는 모두 `00 NORMAL SERVICE`입니다.

| API | 확인 구간 | 조회 범위 | 측정 결과 |
|---|---|---|---|
| 열차 | 서울 → 부산 | 약 4주 | 11/12까지 하루 75건 안팎, 11/13 23건(일부), 11/14부터 0건 |
| 항공 | 김포 → 김해 | 9일 뒤까지 | 10/24 23건, 10/25부터 0건 |
| 고속버스 | 서울경부 → 부산 | 모레까지 | 10/15 32건, 10/16 35건, 10/17 42건, 10/18부터 0건 |
| 시외버스 | 동서울 → 강릉 | 모레까지 | 10/15~10/17 각 13건, 10/18부터 0건 |

- 날짜별 결과에는 그날의 실제 배차가 담깁니다. 요일마다 편수가 다르므로(고속버스 목 32 · 금 35 · 토 42) 한 날짜의 시간표를 다른 날짜에 그대로 쓰지 않습니다. API에는 운행 요일 필드가 없습니다.
- 시외버스 노선에는 "월요일만 운행", "목~월 운행"처럼 요일별로 운행하는 편이 실제로 있습니다(철원군·공주시 시외버스 시간표, 웹 조사).
- 조회 범위는 운영 기관 사정에 따라 바뀔 수 있습니다. 구현할 때는 결과가 0건이면 "이 날짜의 시간표는 아직 제공되지 않습니다"로 안내합니다.
- 기타 확인 사항: SRT(수서 출발)도 열차정보에서 조회되지만 등급명은 `KTX`로 옵니다. ID 예시는 서울역 `NAT010000`, 부산역 `NAT014445`, 수서 `NATH30000`, 서울경부 `NAEK010`, 부산(고속) `NAEK700`, 동서울 `NAI0511601`, 강릉 `NAI2551901`, 김포 `NAARKSS`, 김해 `NAARKPK`입니다.

### 검토 후 쓰지 않기로 한 대안
- **ODsay LAB**은 시외버스 정기 시간표(운행 요일 포함)를 주지만, 서버 호출에 고정 IP 등록이 필요하고 경로 데이터 체계가 TMAP과 달라 연동을 다시 설계해야 합니다. 무료 요금제도 하루 30건, 비상업적 이용으로 제한됩니다.

## 9. TMAP 장소와 TAGO 장소 맞추기 (v1.5)

TMAP 장거리 구간의 출발·도착 장소(이름·좌표)를 TAGO 역·터미널·공항 ID와 맞춥니다. v1.6에서 이 ID로 TAGO 편·운임을 조회합니다. 서버 API는 `POST /api/place/match`입니다.

### 저장하는 것과 저장하지 않는 것
| 데이터 | 저장 | 이유 |
|---|---|---|
| TAGO 목록 + 도로명주소 API 주소 (`transit_place`) | ✅ | 공공데이터 |
| 일치한 TMAP 장소 ↔ TAGO ID 쌍 (`place_match`) | ✅ | 매칭 캐시. 카카오 데이터는 들어가지 않음 |
| 카카오 좌표→주소, 주소·이름→좌표 결과 | ❌ | 카카오 정책상 좌표·검색 결과 저장 불가 (장소 ID·URL만 저장 가능). 비교할 때만 호출 |

### 매칭 순서
1. **캐시**: 같은 TMAP 장소(이름 + 좌표 소수점 4자리)가 있으면 바로 사용
2. **후보**: TAGO 목록에서 이름이 비슷한 곳 최대 5개 (역·터미널·공항 같은 말을 떼고 비교, 시도를 아는 교통수단은 같은 시도 안에서)
3. **주소 비교**: TMAP 좌표 → 카카오 좌표→주소. 기준표의 도로명(시군구·도로명·건물번호) 또는 지번(시군구·읍면동·번지)이 같으면 일치
4. **거리 비교**: 후보의 주소 좌표와 이름 검색 좌표 중 가까운 쪽이 1km 안이면 일치. 여러 곳이면 이름이 더 비슷한 곳, 같으면 더 가까운 곳
5. **같은 이름**: 주소·좌표를 모두 못 찾는 길가 정류장은, 같은 시도 안에 이름이 정확히 같은 곳이 하나뿐이면 일치
6. 일치하면 캐시에 저장

### 실측 (2026-10-16, 서울 → 부산 TMAP 응답의 장거리 장소 17곳)
- 주소 2, 거리 10, 같은 이름 1, 캐시 3, 찾지 못함 1(부전시장: TAGO 시외버스 목록에 없는 정류장)
- 첫 실행 21초(TAGO 목록 저장 포함), 같은 요청 두 번째는 2초
- 도로명주소 API가 옛 건물을 줄 수 있어(옛 경주역) 거리 비교는 주소 좌표와 이름 검색 좌표를 함께 봅니다
- TAGO 이름과 실제 건물명이 다른 곳이 많습니다: `서울경부` ↔ 강남고속버스터미널, `김포국제공항` ↔ 김포공항

### TMAP `service` 값
구간의 `service`가 0이면 **검색 시각에 운행하지 않는 구간**으로 보고 예매 대상에서 뺍니다.
- 근거: 같은 서울 → 부산 경로에서 항공·고속·시외버스 구간이 밤(21시) 검색에서는 0, 10/22 08:00 검색에서는 1이었습니다. 서울역 출발 SRT는 두 검색 모두 0이었는데, TAGO 열차 조회에서도 서울역 출발 SRT는 없었습니다.
