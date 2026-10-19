// 홈 메뉴 화면 규칙 테스트 (v2.7): node scripts/web-menu.test.js
// 1. 요금비교: 경로 요약(요금·시간·환승), 중복 제거, 정렬, 차이 표시 (itinerary.js, compare.js)
// 2. 요금비교는 마지막 검색과 지금 고른 출발·도착이 같을 때만 결과를 쓴다 (다르면 상세가 TMAP 을 다시 부르므로)
// 3. 주변정보 기준 장소 고르기 (nearby.js)
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");

const dir = path.join(__dirname, "..", "src", "main", "resources", "static", "javascript");
const failures = [];
const same = (actual, expected, message) => {
  const [a, e] = [JSON.stringify(actual), JSON.stringify(expected)];
  if (a !== e) failures.push(`${message}: ${a} (기대 ${e})`);
};

// 브라우저 저장소 흉내 (테스트마다 값을 넣는다)
const memoryStorage = () => {
  const data = new Map();
  return {
    getItem: (k) => (data.has(k) ? data.get(k) : null),
    setItem: (k, v) => data.set(k, String(v)),
    clear: () => data.clear(),
  };
};
const context = {
  document: { addEventListener() {} },
  localStorage: memoryStorage(),
  sessionStorage: memoryStorage(),
};
vm.createContext(context);
for (const file of ["safe-html.js", "transport.js", "itinerary.js", "compare.js", "nearby.js"]) {
  vm.runInContext(fs.readFileSync(path.join(dir, file), "utf8"), context, { filename: file });
}
vm.runInContext(
  "this.api = { compareItineraries, sortComparisons, compareDifference, itineraryFare, formatMinutes, readLastSearch, pickNearbyPlaces, buildRouteRequest };",
  context,
);
const api = context.api;

// TMAP 경로 흉내: 요금(원), 시간(분), 환승, 교통수단
const leg = (mode, route, extra = {}) => ({ mode, route, ...extra });
const itinerary = (fare, minutes, transfers, legs) => ({
  fare: { regular: { totalFare: fare } },
  totalTime: minutes * 60,
  transferCount: transfers,
  legs,
});
const itineraries = [
  itinerary(59800, 160, 1, [leg("WALK"), leg("TRAIN", "KTX", { routePayment: 59800 }), leg("SUBWAY", "1호선")]),
  itinerary(23000, 280, 0, [leg("EXPRESSBUS", "고속버스:서울-부산", { routePayment: 23000, service: 1 })]),
  // 0번과 시간·환승·교통수단이 같은 경로 (TMAP 이 가끔 같은 경로를 두 번 준다)
  itinerary(59800, 160, 1, [leg("TRAIN", "KTX", { routePayment: 59800 }), leg("SUBWAY", "1호선")]),
  itinerary(18000, 300, 2, [leg("EXPRESSBUS", "시외버스:서울-부산", { routePayment: 18000, service: 0 })]),
  itinerary(64000, 130, 1, [leg("AIRPLANE", "대한항공", { routePayment: 64000, service: 1 }), leg("BUS", "307")]),
];

// 1. 요약과 중복 제거: index 는 TMAP 응답 순서 그대로 (상세 화면 /route?index= 가 같은 경로를 연다)
const rows = api.compareItineraries(itineraries);
same(
  rows.map((r) => r.index),
  [0, 1, 3, 4],
  "같은 경로(2번)는 빼고 원래 순서 번호를 유지",
);
same(rows[0].summary, "KTX → 1호선", "요약은 도보를 뺀 교통수단");
same([rows[0].minutes, rows[0].transfers, rows[0].payable, rows[0].operating], [160, 1, true, true], "0번 요약 값");
same(rows[2].operating, false, "운행하지 않는 장거리 구간이 있으면 operating false");

same(
  api.itineraryFare({
    legs: [{ fare: { regular: { totalFare: 1500 } } }, {}, { fare: { regular: { totalFare: 59800 } } }],
  }),
  61300,
  "경로 합계가 없으면 구간 요금의 합",
);
same([api.formatMinutes(45), api.formatMinutes(125)], ["45분", "2시간 5분"], "시간 표시");

// 정렬: 운행하지 않는 경로(3번, 가장 쌈)는 언제나 맨 뒤
same(
  api.sortComparisons(rows, "fare").map((r) => r.index),
  [1, 0, 4, 3],
  "요금 낮은 순",
);
same(
  api.sortComparisons(rows, "time").map((r) => r.index),
  [4, 0, 1, 3],
  "시간 짧은 순",
);
same(
  api.sortComparisons(rows, "transfers").map((r) => r.index),
  [1, 4, 0, 3],
  "환승 적은 순 (같으면 시간 짧은 순)",
);
same(api.sortComparisons(rows, "unknown")[0].index, 1, "모르는 기준은 요금 순");
same(rows[0].index, 0, "정렬은 원래 목록을 바꾸지 않음");

const byFare = api.sortComparisons(rows, "fare");
same(api.compareDifference(byFare[1], byFare[0], "fare"), "+36,800원", "요금 차이");
same(api.compareDifference(byFare[0], byFare[0], "fare"), "", "가장 좋은 경로는 차이 없음");
const byTime = api.sortComparisons(rows, "time");
same(api.compareDifference(byTime[2], byTime[0], "time"), "+2시간 30분", "시간 차이");
same(api.compareDifference(byFare[0], byFare[2], "transfers"), "", "기준 값이 더 좋으면 차이 없음");

// 2. 마지막 검색 결과
const from = { name: "서울역", x: "126.9706", y: "37.5547" };
const to = { name: "부산역", x: "129.0403", y: "35.1151" };
const save = (request) => {
  context.localStorage.clear();
  context.sessionStorage.clear();
  context.localStorage.setItem("selectedFrom", JSON.stringify(from));
  context.localStorage.setItem("selectedTo", JSON.stringify(to));
  context.localStorage.setItem("selectedDepartAt", "");
  context.sessionStorage.setItem(
    "transitRouteResult",
    JSON.stringify({ request, data: { metaData: { plan: { itineraries } } } }),
  );
};
save(api.buildRouteRequest(from, to, ""));
same(api.readLastSearch()?.itineraries.length, 5, "같은 검색이면 결과를 쓴다");
save(api.buildRouteRequest(from, { x: "127.0", y: "37.0" }, ""));
same(api.readLastSearch(), null, "도착지가 바뀌었으면 결과 없음 (상세가 TMAP 을 다시 부르지 않게)");
context.sessionStorage.setItem("transitRouteResult", "{깨진 값");
same(api.readLastSearch(), null, "저장 값이 깨졌으면 결과 없음");

// 3. 주변정보 기준 장소: 홈에서 고른 값이 먼저, 이름·좌표가 없으면 쓰지 않는다
same(
  api.pickNearbyPlaces({ from: null, to }, { from, to: null }),
  { from: null, to },
  "홈에서 고른 값이 있으면 그것만",
);
same(api.pickNearbyPlaces(null, { from, to }), { from, to }, "홈에서 고른 값이 없으면 마지막 검색");
same(
  api.pickNearbyPlaces({ from: { name: "x" }, to: null }, { from: null, to: { name: "부산역", x: "a", y: "b" } }),
  { from: null, to: null },
  "좌표가 숫자가 아니면 쓰지 않음",
);

if (failures.length) {
  console.error(`web-menu: ${failures.length}건 실패`);
  failures.forEach((f) => console.error(` - ${f}`));
  process.exit(1);
}
console.log("web-menu: 통과");
