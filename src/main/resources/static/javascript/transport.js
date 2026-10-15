/**
 * 교통수단 공통 규칙 (홈·상세 페이지 공용)
 * - 예매(장바구니·결제) 대상: 기차, 고속버스, 시외버스, 항공
 * - 안내 전용: 시내버스, 지하철, 도보 등 (현장 결제)
 * 규칙 문서: docs/PUBLIC_DATA_API.md
 */
/* exported PAYABLE_MODES, isPayableLeg, hasPayableLeg, getTransportLabel */

const PAYABLE_MODES = ["TRAIN", "EXPRESSBUS", "AIRPLANE"];

// TMAP 은 고속버스와 시외버스를 모두 EXPRESSBUS 로 주고 type 으로 구분한다 (23 고속, 22 시외)
const EXPRESSBUS_TYPE_LABELS = { 22: "시외버스", 23: "고속버스" };

/**
 * 예매할 수 있는 구간인지 (장거리 교통수단이고 구간 요금이 있는 경우)
 * @param {object} leg - TMAP 구간
 * @returns {boolean}
 */
function isPayableLeg(leg) {
  return PAYABLE_MODES.includes(leg?.mode) && Number(leg.routePayment) > 0;
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
