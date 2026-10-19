// 예매 화면 규칙 테스트 (v2.6): node scripts/web-booking.test.js
// 1. 앞 편을 바꾸면 뒤 편을 다시 고를 때 쓰는 계산 (transport.js: connectionReference, pickTripIndex)
// 2. 상세 화면 금액 줄과 시내 교통비 포함 선택 (transport.js: describeFareTotal)
// 3. 장바구니에서 고른 묶음의 개수·합계·결제창 수 (cart.js: summarizeSelection)
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");

const dir = path.join(__dirname, "..", "src", "main", "resources", "static", "javascript");
const failures = [];
const same = (actual, expected, message) => {
  const [a, e] = [JSON.stringify(actual), JSON.stringify(expected)];
  if (a !== e) failures.push(`${message}: ${a} (기대 ${e})`);
};

// 화면 스크립트를 DOM 없이 불러온다 (DOMContentLoaded 등록만 받아 두고 실행하지 않는다)
const context = { document: { addEventListener() {} }, localStorage: undefined };
vm.createContext(context);
for (const file of ["safe-html.js", "transport.js", "cart.js"]) {
  vm.runInContext(fs.readFileSync(path.join(dir, file), "utf8"), context, { filename: file });
}
vm.runInContext("this.api = { connectionReference, pickTripIndex, describeFareTotal, summarizeSelection };", context);
const { connectionReference, pickTripIndex, describeFareTotal, summarizeSelection } = context.api;

// 1. 이어 탈 수 있는 시각 = 앞 편 도착 + 사이 구간 이동(올림) + 환승 여유
same(connectionReference("202610190950", 600, 20), "202610191020", "도착 09:50 + 이동 10분 + 여유 20분");
same(connectionReference("202610190950", 61, 10), "202610191002", "이동 시간은 분 단위로 올림");
same(connectionReference("202610192350", 0, 20), "202610200010", "자정을 넘기면 날짜도 바뀜");

const trips = [
  { departAt: "202610190900", bookable: false },
  { departAt: "202610191000", bookable: true },
  { departAt: "202610191030", bookable: false },
  { departAt: "202610191100", bookable: true },
];
same(pickTripIndex(trips, "202610191020", true), 3, "예매 가능한 날은 기준 뒤 예매 가능한 첫 편");
same(pickTripIndex(trips, "202610191020", false), 2, "조회 전용 날은 기준 뒤 첫 편");
same(pickTripIndex(trips, "202610191000", true), 1, "기준 시각과 같은 편도 탈 수 있음");
same(pickTripIndex(trips, "202610191200", true), -1, "이어 탈 편이 없으면 -1");

// 2. 금액 줄
same(describeFareTotal(23700, 0, 0, true), "예매 금액 23,700원", "현장 결제가 없으면 예매 금액만");
same(
  describeFareTotal(23700, 2900, 0, true),
  "예매 금액 23,700원 + 현장 결제 예상 2,900원 (시내 교통 2,900원) = 총 26,600원",
  "시내 교통비 포함",
);
same(describeFareTotal(23700, 2900, 0, false), "예매 금액 23,700원", "시내 교통비를 숨기면 예매 금액만");
same(
  describeFareTotal(0, 1400, 15000, true),
  "예매 금액 0원 + 현장 결제 예상 16,400원 (시내 교통 1,400원 + 예매 정보 없는 구간 15,000원) = 총 16,400원",
  "예매 정보 없는 장거리 구간도 현장 결제 예상에",
);

// 3. 고른 묶음 요약: 결제할 수 없는 묶음은 골라도 세지 않는다
const groups = [
  { groupId: "a", payable: true, totalFare: 49100, payments: [{}, {}] },
  { groupId: "b", payable: true, totalFare: 4800, payments: [{}] },
  { groupId: "c", payable: false, totalFare: 9999, payments: [{}] },
];
same(summarizeSelection(groups, ["a", "b"]), { count: 2, total: 53900, windows: 3 }, "두 묶음, 결제창 3번");
same(summarizeSelection(groups, ["b", "c"]), { count: 1, total: 4800, windows: 1 }, "막힌 묶음은 빼고");
same(summarizeSelection(groups, []), { count: 0, total: 0, windows: 0 }, "고른 것이 없으면 0");

if (failures.length) {
  console.error(`예매 화면 규칙 테스트 실패 ${failures.length}건`);
  failures.forEach((f) => console.error(` - ${f}`));
  process.exit(1);
}
console.log("예매 화면 규칙 테스트 통과");
