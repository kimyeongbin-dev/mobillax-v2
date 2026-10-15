/**
 * 교통수단 공통 규칙 (홈·상세 페이지 공용)
 * - 예매(장바구니·결제) 대상: 기차, 고속버스, 시외버스, 항공
 * - 안내 전용: 시내버스, 지하철, 도보 등 (현장 결제)
 * 규칙 문서: docs/PUBLIC_DATA_API.md
 */
/* exported PAYABLE_MODES, isOperatingLeg, isPayableLeg, hasPayableLeg, getTransportLabel, buildRouteRequest, isBookableDate, formatDepartAt */

const PAYABLE_MODES = ["TRAIN", "EXPRESSBUS", "AIRPLANE"];

// TMAP 은 고속버스와 시외버스를 모두 EXPRESSBUS 로 주고 type 으로 구분한다 (23 고속, 22 시외)
const EXPRESSBUS_TYPE_LABELS = { 22: "시외버스", 23: "고속버스" };

/**
 * 검색 시각에 운행하는 구간인지 (TMAP service: 1 운행, 0 운행하지 않음)
 * 예) 밤에 검색하면 막차가 끝난 항공·고속버스 구간이 0, 서울역 출발 SRT 처럼 없는 열차도 0 으로 온다
 * @param {object} leg - TMAP 구간
 * @returns {boolean}
 */
function isOperatingLeg(leg) {
  return leg?.service !== 0;
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
