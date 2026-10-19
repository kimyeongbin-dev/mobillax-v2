/* global html, raw, formatDepartAt -- safe-html.js, transport.js */
/* global readLastSearch, compareItineraries, sortComparisons, formatMinutes, COMPARE_KEYS -- itinerary.js */
/* exported compareDifference */
/**
 * 요금비교 화면
 * - 마지막 검색 결과(sessionStorage)만 쓴다. TMAP 을 다시 부르지 않는다 (하루 호출 한도)
 * - 상세 화면은 같은 결과에서 고른 경로(index)를 보여 준다
 */

document.addEventListener("DOMContentLoaded", () => {
  const search = readLastSearch();
  if (!search) {
    document.getElementById("compareEmpty").hidden = false;
    return;
  }

  const target = document.getElementById("compareTarget");
  target.textContent = `${search.from.name} → ${search.to.name} · ${formatDepartAt(search.departAt)} 출발`;
  target.hidden = false;

  const rows = compareItineraries(search.itineraries);
  const tabs = document.getElementById("compareSort");
  tabs.hidden = false;
  document.getElementById("compareNote").hidden = false;

  const render = (key) => {
    tabs.querySelectorAll("button").forEach((b) => b.setAttribute("aria-pressed", String(b.dataset.sort === key)));
    renderComparisons(sortComparisons(rows, key), key);
  };
  tabs.addEventListener("click", (event) => {
    const button = event.target.closest("button[data-sort]");
    if (button) render(button.dataset.sort);
  });
  render("fare");
});

/**
 * 정렬 기준 값이 가장 좋은 경로보다 얼마나 더 드는지 ("+3,200원", "+25분", "+1회", 가장 좋으면 "")
 * @param {object} row - compareItineraries 의 한 줄
 * @param {object} best - 같은 기준으로 가장 앞선 줄
 * @param {"fare"|"time"|"transfers"} key
 * @returns {string}
 */
function compareDifference(row, best, key) {
  const field = (COMPARE_KEYS[key] || COMPARE_KEYS.fare)[0];
  const diff = row[field] - best[field];
  if (diff <= 0) return "";
  if (field === "fare") return `+${diff.toLocaleString("ko-KR")}원`;
  if (field === "minutes") return `+${formatMinutes(diff)}`;
  return `+${diff}회`;
}

/**
 * 비교 목록 (값은 html`` 이 이스케이프한다, 보안 규칙 S10)
 * @param {Array} rows - 정렬된 목록
 * @param {string} key - 정렬 기준
 */
function renderComparisons(rows, key) {
  const list = document.getElementById("compareList");
  const best = rows.find((r) => r.operating) || rows[0];
  list.innerHTML = html`${rows.map((row, rank) => {
    // 운행하는 경로 중 기준 값이 가장 좋은 경로(같은 값 포함)는 "가장 좋음", 나머지는 차이
    const diff = row.operating ? compareDifference(row, best, key) || "가장 좋음" : "";
    return html`
      <li class="${row.operating ? "compare-row" : "compare-row not-operating"}">
        <a href="/route?index=${row.index}">
          <span class="compare-rank">${rank + 1}</span>
          <span class="compare-body">
            <span class="compare-figures">
              <strong class="compare-fare">${row.fare.toLocaleString("ko-KR")}원</strong>
              <span>${formatMinutes(row.minutes)}</span>
              <span>환승 ${row.transfers}회</span>
            </span>
            <span class="compare-summary">${row.summary}</span>
            <span class="compare-badges">
              ${row.payable ? raw('<span class="badge bookable">예매 가능 구간 있음</span>') : ""}
              ${row.operating ? "" : raw('<span class="badge off">검색 시각에 운행하지 않는 구간 있음</span>')}
            </span>
          </span>
          <span class="compare-diff">${diff}</span>
        </a>
      </li>
    `;
  })}`;
}
