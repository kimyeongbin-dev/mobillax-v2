/**
 * 교통수단 공통 규칙 (홈·상세 페이지 공용)
 * - 예매(장바구니·결제) 대상: 기차, 고속버스, 시외버스, 항공
 * - 안내 전용: 시내버스, 지하철, 도보 등 (현장 결제)
 * 규칙 문서: docs/PUBLIC_DATA_API.md
 */
/* exported TRANSFER_OPTIONS, getTransferMinutes, setTransferMinutes, PAYABLE_MODES, isOperatingLeg, isPayableLeg, preferOperating, nowStamp, formatHm, collectBookingLegs, hasPayableLeg, getTransportLabel, buildRouteRequest, isBookableDate, formatDepartAt, addMinutes, connectionReference, pickTripIndex, describeFareTotal, getIncludeLocalFare, setIncludeLocalFare */

const PAYABLE_MODES = ["TRAIN", "EXPRESSBUS", "AIRPLANE"];

// TMAP 은 고속버스와 시외버스를 모두 EXPRESSBUS 로 주고 type 으로 구분한다 (23 고속, 22 시외)
const EXPRESSBUS_TYPE_LABELS = { 22: "시외버스", 23: "고속버스" };

/**
 * 검색 시각에 운행하는 구간인지 (TMAP service: 1 운행, 0 운행하지 않음)
 * - 열차는 TMAP 의 열차 종류·운행 여부를 믿지 않는다: TMAP 은 서울역 → 부산역 직통을 "SRT:서울-부산"(service 0)으로 주지만
 *   같은 역 쌍에 KTX·ITX·무궁화가 있다. 열차는 역 쌍으로 TAGO 편을 조회해 판단한다
 * - 항공·고속·시외버스는 TMAP 값을 따른다 (밤에 검색하면 막차가 끝난 구간이 0)
 * @param {object} leg - TMAP 구간
 * @returns {boolean}
 */
function isOperatingLeg(leg) {
  return leg?.mode === "TRAIN" || leg?.service !== 0;
}

/**
 * 예매할 수 있는 구간인지 (장거리 교통수단이고, 구간 요금이 있고, 검색 시각에 운행하는 경우)
 * @param {object} leg - TMAP 구간
 * @returns {boolean}
 */
function isPayableLeg(leg) {
  return PAYABLE_MODES.includes(leg?.mode) && Number(leg.routePayment) > 0 && isOperatingLeg(leg);
}

/**
 * 경로에 예매할 수 있는 구간이 하나라도 있는지
 * @param {object} itinerary - TMAP 경로
 * @returns {boolean}
 */
function hasPayableLeg(itinerary) {
  return (itinerary?.legs || []).some(isPayableLeg);
}

/**
 * 구간의 교통수단 이름 (고속버스·시외버스 구분)
 * @param {object} leg - TMAP 구간
 * @returns {string}
 */
function getTransportLabel(leg) {
  if (leg?.mode === "EXPRESSBUS") {
    const prefix = String(leg.route || "").split(":")[0];
    return (
      EXPRESSBUS_TYPE_LABELS[leg.type] || (prefix === "시외버스" || prefix === "고속버스" ? prefix : "고속·시외버스")
    );
  }
  const labels = {
    BUS: "버스",
    SUBWAY: "지하철",
    TRAIN: "기차",
    AIRPLANE: "항공",
    WALK: "도보",
    TAXI: "택시",
  };
  return labels[leg?.mode] || "이동수단";
}

/**
 * 경로 검색 요청 본문 (홈과 상세가 같은 모양으로 만들어야 sessionStorage 캐시가 맞는다)
 * @param {object} from - 출발지 {x, y}
 * @param {object} to - 도착지 {x, y}
 * @param {string} departAt - 출발 일시 yyyyMMddHHmm, 비어 있으면 현재 시각
 * @returns {object}
 */
function buildRouteRequest(from, to, departAt) {
  const request = { startX: Number(from.x), startY: Number(from.y), endX: Number(to.x), endY: Number(to.y) };
  if (departAt) request.departAt = departAt;
  return request;
}

/**
 * 예매할 수 있는 날짜인지 (예매는 당일 출발편만, 출발 일시가 없으면 지금 출발)
 * @param {string} departAt - yyyyMMddHHmm
 * @returns {boolean}
 */
function isBookableDate(departAt) {
  if (!departAt) return true;
  const now = new Date();
  const today = `${now.getFullYear()}${String(now.getMonth() + 1).padStart(2, "0")}${String(now.getDate()).padStart(2, "0")}`;
  return departAt.slice(0, 8) === today;
}

/**
 * 출발 일시 표시용 문자열 (yyyyMMddHHmm → "10월 22일 14:30")
 * @param {string} departAt
 * @returns {string}
 */
function formatDepartAt(departAt) {
  if (!departAt) return "지금";
  return `${Number(departAt.slice(4, 6))}월 ${Number(departAt.slice(6, 8))}일 ${departAt.slice(8, 10)}:${departAt.slice(10, 12)}`;
}

/**
 * 장거리 구간이 모두 운행하는 경로만 (없으면 전체). 최단시간·최소환승 등을 고를 때 운행하지 않는 경로를 피한다
 * @param {Array} itineraries - TMAP 경로 목록
 * @returns {Array}
 */
function preferOperating(itineraries) {
  const operating = itineraries.filter((it) =>
    it.legs.every((leg) => !PAYABLE_MODES.includes(leg.mode) || isOperatingLeg(leg)),
  );
  return operating.length > 0 ? operating : itineraries;
}

/**
 * 지금 시각 yyyyMMddHHmm (브라우저 시간)
 * @returns {string}
 */
function nowStamp() {
  const d = new Date();
  const p = (n) => String(n).padStart(2, "0");
  return `${d.getFullYear()}${p(d.getMonth() + 1)}${p(d.getDate())}${p(d.getHours())}${p(d.getMinutes())}`;
}

/**
 * yyyyMMddHHmm → HH:mm
 * @param {string} stamp
 * @returns {string}
 */
function formatHm(stamp) {
  return stamp ? `${stamp.slice(8, 10)}:${stamp.slice(10, 12)}` : "";
}

/**
 * 선택한 구간 중 예매할 편을 고른 구간만 장바구니·결제 요청 형식으로 (금액은 고른 편의 운임)
 * @returns {Array}
 */
function collectBookingLegs() {
  return [...document.querySelectorAll(".route-step.selected")]
    .map((el) => {
      try {
        return { leg: JSON.parse(el.dataset.leg), trip: JSON.parse(el.dataset.trip || "null") };
      } catch {
        return null;
      }
    })
    .filter((x) => x && x.trip && x.trip.bookable && x.trip.fare > 0)
    .map(({ leg, trip }) => ({
      mode: leg.mode,
      route: `${getTransportLabel(leg)}:${leg.start.name}-${leg.end.name} · ${trip.grade} ${formatHm(trip.departAt)} 출발`,
      routeId: trip.no,
      routePayment: trip.fare,
      startName: leg.start.name,
      endName: leg.end.name,
      departureTime: trip.departAt,
      // 서버가 같은 조건으로 TAGO 를 다시 조회해 운임을 확인한다 (보안 규칙 S1)
      kind: trip.kind,
      depId: trip.depId,
      arrId: trip.arrId,
    }));
}

/** 장거리 구간 사이 환승 여유(분) 선택지와 기본값 */
const TRANSFER_OPTIONS = [10, 20, 30];
const DEFAULT_TRANSFER_MINUTES = 20;

/**
 * 사용자가 고른 환승 여유(분). 브라우저에 저장, 없거나 읽을 수 없으면 20분
 * @returns {number}
 */
function getTransferMinutes() {
  try {
    const saved = Number(localStorage.getItem("transferMinutes"));
    return TRANSFER_OPTIONS.includes(saved) ? saved : DEFAULT_TRANSFER_MINUTES;
  } catch {
    return DEFAULT_TRANSFER_MINUTES;
  }
}

function setTransferMinutes(minutes) {
  try {
    localStorage.setItem("transferMinutes", String(minutes));
  } catch {
    // 저장할 수 없으면 이번 화면에서만 적용
  }
}

/** yyyyMMddHHmm 에 분을 더한다 */
function addMinutes(stamp, minutes) {
  const d = new Date(
    Number(stamp.slice(0, 4)),
    Number(stamp.slice(4, 6)) - 1,
    Number(stamp.slice(6, 8)),
    Number(stamp.slice(8, 10)),
    Number(stamp.slice(10, 12)) + minutes,
  );
  const p = (n) => String(n).padStart(2, "0");
  return `${d.getFullYear()}${p(d.getMonth() + 1)}${p(d.getDate())}${p(d.getHours())}${p(d.getMinutes())}`;
}

/**
 * 앞 편을 타고 내린 뒤 다음 편을 탈 수 있는 가장 이른 시각
 * = 앞 편 도착 + 그 사이 구간(도보·시내 교통) 이동 시간 + 환승 여유
 * @param {string} arriveAt - 앞 편 도착 yyyyMMddHHmm
 * @param {number} betweenSeconds - 사이 구간 이동 시간(초)
 * @param {number} transferMinutes - 환승 여유(분)
 * @returns {string} yyyyMMddHHmm
 */
function connectionReference(arriveAt, betweenSeconds, transferMinutes) {
  return addMinutes(arriveAt, Math.ceil(betweenSeconds / 60) + transferMinutes);
}

/**
 * reference 이후 첫 편 (예매할 수 있는 날이면 예매 가능한 편만). 없으면 -1
 * @param {Array} trips - 편 목록 (출발 순)
 * @param {string} reference - yyyyMMddHHmm
 * @param {boolean} bookableDate - 예매할 수 있는 날짜인지
 * @returns {number}
 */
function pickTripIndex(trips, reference, bookableDate) {
  return trips.findIndex((t) => t.departAt >= reference && (!bookableDate || t.bookable));
}

/**
 * 상세 화면의 금액 줄
 * - 예매 금액: 고른 편의 운임 합 (결제할 금액)
 * - 현장 결제 예상: 시내 교통 + 예매 정보가 없는 장거리 구간 (TMAP 요금, 결제하지 않음)
 * - 사용자가 시내 교통비를 숨기면 예매 금액만 보인다
 * @returns {string}
 */
function describeFareTotal(bookedFare, localFare, onsiteLongFare, includeLocal) {
  const won = (n) => `${n.toLocaleString("ko-KR")}원`;
  const onsite = localFare + onsiteLongFare;
  if (!includeLocal || onsite <= 0) return `예매 금액 ${won(bookedFare)}`;
  const parts = [];
  if (localFare > 0) parts.push(`시내 교통 ${won(localFare)}`);
  if (onsiteLongFare > 0) parts.push(`예매 정보 없는 구간 ${won(onsiteLongFare)}`);
  return `예매 금액 ${won(bookedFare)} + 현장 결제 예상 ${won(onsite)} (${parts.join(" + ")}) = 총 ${won(bookedFare + onsite)}`;
}

const INCLUDE_LOCAL_FARE_KEY = "includeLocalFare";

/** 시내 교통비를 금액 줄에 함께 보일지 (브라우저에 저장, 기본은 보임) */
function getIncludeLocalFare() {
  try {
    return localStorage.getItem(INCLUDE_LOCAL_FARE_KEY) !== "false";
  } catch {
    return true;
  }
}

function setIncludeLocalFare(include) {
  try {
    localStorage.setItem(INCLUDE_LOCAL_FARE_KEY, String(include));
  } catch {
    // 저장할 수 없으면 이번 화면에서만 적용
  }
}
