/* global LAST_SEARCH_KEY, hasPayableLeg, isOperatingItinerary, buildRouteRequest -- transport.js */
/* exported getTransferCount, getPathSummary, removeDuplicateItineraries, itineraryFare, formatMinutes, compareItineraries, sortComparisons, readLastSearch, COMPARE_KEYS */
/**
 * TMAP 경로(itinerary) 요약 규칙 (홈 추천 경로·요금비교 화면 공용)
 * - 요금·시간·환승·교통수단 요약을 한 곳에서 계산해 화면마다 다르게 보이지 않게 한다
 * - 마지막 검색 결과는 브라우저 sessionStorage 에만 있다 (TMAP 결과는 서버에 저장하지 않음). 비교 화면은 TMAP 을 다시 부르지 않는다
 */

/**
 * 환승 횟수 (도보 제외, 교통수단이 바뀔 때마다)
 * @param {Array} legs - TMAP 구간
 * @returns {number}
 */
function getTransferCount(legs) {
  let count = 0;
  for (let i = 1; i < legs.length; i++) {
    if (legs[i].mode !== "WALK" && legs[i].mode !== legs[i - 1].mode) {
      count++;
    }
  }
  return count;
}

/**
 * 구간 하나의 요약 이름 (노선명이 있으면 노선명)
 * @param {object} leg - TMAP 구간
 * @returns {string}
 */
function getModeLabel(leg) {
  const name = leg.route || leg.routeName || leg.airline || "";
  const cleanName = name.replace(/undefined|null/gi, "").trim();
  const fallback = {
    WALK: "도보",
    BUS: "버스",
    SUBWAY: "지하철",
    TRAIN: "기차",
    AIRPLANE: "항공편",
    EXPRESSBUS: "고속버스",
  };
  if (leg.mode === "WALK") return "🚶 도보";
  return cleanName || fallback[leg.mode] || `${leg.mode}`;
}

/**
 * 도보를 뺀 교통수단 요약 ("KTX → 지하철 2호선")
 * @param {object} itinerary - TMAP 경로 ({legs})
 * @returns {string}
 */
function getPathSummary(itinerary) {
  if (!itinerary.legs || itinerary.legs.length === 0) return "경로 없음";
  return itinerary.legs
    .filter((leg) => leg.mode !== "WALK")
    .map(getModeLabel)
    .join(" → ");
}

/**
 * 시간·환승 수·교통수단이 같은 경로를 하나만 남긴다 (처음 나온 것)
 * @param {Array} itineraries
 * @returns {Array}
 */
function removeDuplicateItineraries(itineraries) {
  const seen = new Set();
  return itineraries.filter((route) => {
    const pathKey = route.legs
      .filter((leg) => leg.mode !== "WALK")
      .map((leg) => `${leg.mode}:${leg.route || leg.routeName || leg.busNo || ""}`)
      .join("→");
    const key = `${Math.round(route.totalTime / 60)}|${route.transferCount}|${pathKey}`;
    if (seen.has(key)) return false;
    seen.add(key);
    return true;
  });
}

/**
 * 경로 전체 요금(원): TMAP 합계가 있으면 그 값, 없으면 구간 요금의 합
 * @param {object} itinerary
 * @returns {number}
 */
function itineraryFare(itinerary) {
  const total = itinerary.fare?.regular?.totalFare;
  if (typeof total === "number") return total;
  return (itinerary.legs || []).reduce((sum, leg) => {
    const fare = leg.fare?.regular?.totalFare;
    return typeof fare === "number" ? sum + fare : sum;
  }, 0);
}

/**
 * 분 → "2시간 5분", "45분"
 * @param {number} minutes
 * @returns {string}
 */
function formatMinutes(minutes) {
  const h = Math.floor(minutes / 60);
  const m = minutes % 60;
  return h > 0 ? `${h}시간 ${m}분` : `${m}분`;
}

/**
 * 비교 표의 한 줄씩 (중복 경로는 뺀다). index 는 TMAP 응답에서의 순서라 상세 화면(/route?index=)에 그대로 쓴다
 * @param {Array} itineraries - TMAP 경로 목록
 * @returns {Array<{index:number, minutes:number, fare:number, transfers:number, summary:string, payable:boolean, operating:boolean}>}
 */
function compareItineraries(itineraries) {
  const unique = new Set(removeDuplicateItineraries(itineraries));
  return itineraries.flatMap((it, index) =>
    unique.has(it)
      ? [
          {
            index,
            minutes: Math.round(Number(it.totalTime) / 60),
            fare: itineraryFare(it),
            transfers: it.transferCount ?? getTransferCount(it.legs),
            summary: getPathSummary(it),
            payable: hasPayableLeg(it),
            operating: isOperatingItinerary(it),
          },
        ]
      : [],
  );
}

/** 정렬 기준: 첫 값이 같으면 다음 값으로 */
const COMPARE_KEYS = {
  fare: ["fare", "minutes", "transfers"],
  time: ["minutes", "fare", "transfers"],
  transfers: ["transfers", "minutes", "fare"],
};

/**
 * 기준대로 정렬한 새 목록. 운행하지 않는 구간이 있는 경로는 항상 뒤로
 * @param {Array} rows - compareItineraries 결과
 * @param {"fare"|"time"|"transfers"} key
 * @returns {Array}
 */
function sortComparisons(rows, key) {
  const fields = COMPARE_KEYS[key] || COMPARE_KEYS.fare;
  return [...rows].sort((a, b) => {
    if (a.operating !== b.operating) return a.operating ? -1 : 1;
    for (const field of fields) {
      if (a[field] !== b[field]) return a[field] - b[field];
    }
    return a.index - b.index;
  });
}

/**
 * 마지막 검색 결과. 상세 화면이 같은 결과를 쓰도록, 지금 고른 출발·도착·일시로 만든 요청과 같을 때만 돌려준다
 * (다르면 상세 화면이 TMAP 을 다시 부르게 되므로 비교 화면은 결과가 없다고 보여 준다)
 * @returns {{from: object, to: object, departAt: string, itineraries: Array} | null}
 */
function readLastSearch() {
  try {
    const cached = JSON.parse(sessionStorage.getItem(LAST_SEARCH_KEY) || "null");
    const from = JSON.parse(localStorage.getItem("selectedFrom") || "null");
    const to = JSON.parse(localStorage.getItem("selectedTo") || "null");
    const departAt = localStorage.getItem("selectedDepartAt") || "";
    const itineraries = cached?.data?.metaData?.plan?.itineraries;
    if (!from || !to || !Array.isArray(itineraries) || itineraries.length === 0) return null;
    if (JSON.stringify(cached.request) !== JSON.stringify(buildRouteRequest(from, to, departAt))) return null;
    return { from, to, departAt, itineraries };
  } catch {
    return null;
  }
}
