/**
 * 교통수단 공통 규칙 (홈·상세 페이지 공용)
 * - 예매(장바구니·결제) 대상: 기차, 고속버스, 시외버스, 항공
 * - 안내 전용: 시내버스, 지하철, 도보 등 (현장 결제)
 * 규칙 문서: docs/PUBLIC_DATA_API.md
 */
/* exported escapeHtml, TRANSFER_OPTIONS, getTransferMinutes, setTransferMinutes, PAYABLE_MODES, isOperatingLeg, isPayableLeg, preferOperating, nowStamp, formatHm, collectBookingLegs, hasPayableLeg, getTransportLabel, buildRouteRequest, isBookableDate, formatDepartAt */

const PAYABLE_MODES = ["TRAIN", "EXPRESSBUS", "AIRPLANE"];

const HTML_ESCAPES = { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;", "`": "&#96;" };

/**
 * HTML 이스케이프 (보안 규칙 S10)
 * - 서버·외부 API(TMAP·카카오)·사용자에게서 온 문자열을 innerHTML 템플릿에 넣을 때는 반드시 이 함수를 거친다
 * - 이름에 꺾쇠·따옴표가 섞여 있어도 글자로만 보이고 태그·스크립트로 실행되지 않는다
 * @param {*} value
 * @returns {string}
 */
function escapeHtml(value) {
  return String(value ?? "").replace(/[&<>"'`]/g, (c) => HTML_ESCAPES[c]);
}

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
