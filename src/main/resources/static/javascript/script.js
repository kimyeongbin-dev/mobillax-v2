/* global html, raw, LAST_SEARCH_KEY, hasPayableLeg, buildRouteRequest, isBookableDate, preferOperating -- transport.js */
/* global formatMinutes, itineraryFare, getTransferCount, getPathSummary, removeDuplicateItineraries -- itinerary.js */
/**
 * 홈(경로 검색) 화면: 장소 자동완성, 출발 일시, 추천 경로 3개, 주변정보로 장소 넘기기
 * - 이벤트는 모두 여기서 addEventListener 로 건다 (템플릿에 인라인 이벤트 없음, 보안 규칙 S10)
 */
let selectedFrom = null; // 고른 출발지 {name, x, y}
let selectedTo = null; // 고른 도착지 {name, x, y}
let currentFocusType = null; // 지금 입력 중인 칸 ("from" | "to")

// 자동완성 상자 머리글 (출발지/도착지)
function updateSuggestHeader() {
  const header = document.querySelector(".suggest-header");
  if (header) {
    header.innerText = currentFocusType === "from" ? "출발지 검색" : "도착지 검색";
  }
}

document.addEventListener("DOMContentLoaded", () => {
  initDepartInput();
  for (const type of ["from", "to"]) {
    const input = document.getElementById(`${type}Input`);
    input.addEventListener("focus", () => {
      currentFocusType = type;
      updateSuggestHeader();
      showSuggestionBox();
    });
    input.addEventListener("input", () => showSuggestions(input.value));
  }
  document.getElementById("swapButton").addEventListener("click", swapInputs);
  document.getElementById("findRouteButton").addEventListener("click", showRouteResults);
  document.getElementById("nearbyMenu").addEventListener("click", rememberNearbyPlaces);
});

// 출발 일시 입력 범위: 오늘부터 14일 뒤까지 (서버 TransitRouteRequest.MAX_DAYS_AHEAD 와 같음)
const MAX_DAYS_AHEAD = 14;

function initDepartInput() {
  const input = document.getElementById("departInput");
  if (!input) return;
  const toLocalInput = (date) => {
    const local = new Date(date.getTime() - date.getTimezoneOffset() * 60000);
    return local.toISOString().slice(0, 16);
  };
  const now = new Date();
  const max = new Date(now);
  max.setDate(max.getDate() + MAX_DAYS_AHEAD);
  max.setHours(23, 59, 0, 0);
  input.min = toLocalInput(now);
  input.max = toLocalInput(max);
}

// 입력값(2026-10-22T14:30) → TMAP searchDttm 형식(202610221430), 비어 있으면 ""
function getDepartAt() {
  const value = document.getElementById("departInput")?.value || "";
  return value.replace(/[-T:]/g, "");
}

//검색창 박스 표시
function showSuggestionBox() {
  const box = document.getElementById("suggestions");
  box.style.display = "block";
}

//카카오 장소 검색 API로 자동완성 목록 보여주기
function showSuggestions(value) {
  const type = currentFocusType;
  const suggestionsBox = document.getElementById("suggestions");

  if (!value.trim()) {
    suggestionsBox.style.display = "none";
    return;
  }

  const ps = new kakao.maps.services.Places();
  ps.keywordSearch(value, function (data, status) {
    const header = html`<div class="suggest-header">${type === "from" ? "출발지 검색" : "도착지 검색"}</div>`;

    if (status !== kakao.maps.services.Status.OK) {
      suggestionsBox.innerHTML = html`
        <div class="suggest-header">${type === "from" ? "출발지 검색" : "도착지 검색"}</div>
        <div class="empty-result" id="empty">
          <div class="icon">
            <img
              src="https://img.icons8.com/?size=100&id=132&format=png&color=1A1A1A"
              alt="검색 아이콘"
              width="100"
              height="100"
            />
          </div>
          <div>검색결과가 없습니다.</div>
        </div>
      `;
      suggestionsBox.style.display = "block";
      return;
    }

    // 장소 이름은 외부(카카오) 데이터라 HTML·인라인 스크립트 문자열에 넣지 않고 글자로만 넣는다 (S10)
    suggestionsBox.innerHTML = html`${header}`;
    data.slice(0, 5).forEach((place) => {
      const item = document.createElement("div");
      item.className = "item";
      const name = document.createElement("span");
      name.textContent = place.place_name;
      const address = document.createElement("small");
      address.textContent = place.address_name;
      item.append(name, address);
      item.addEventListener("click", () => selectSuggestion(place.place_name, type, place.x, place.y));
      suggestionsBox.appendChild(item);
    });
    suggestionsBox.style.display = "block";
  });
}

//검색 결과 클릭 시 출발지/도착지 값 설정
function selectSuggestion(name, type, x, y) {
  document.getElementById(type + "Input").value = name;
  document.getElementById("suggestions").style.display = "none";

  if (type === "from") {
    selectedFrom = { name, x, y };
  } else {
    selectedTo = { name, x, y };
  }
}

// 출발지·도착지 바꾸기: 입력칸 글자와 고른 장소(좌표)를 함께 바꾼다
function swapInputs() {
  const fromInput = document.getElementById("fromInput");
  const toInput = document.getElementById("toInput");
  [fromInput.value, toInput.value] = [toInput.value, fromInput.value];
  [selectedFrom, selectedTo] = [selectedTo, selectedFrom];
}

// 추천 경로 카드에 보일 요약 (계산 규칙은 itinerary.js)
function formatRouteData(itinerary) {
  return {
    time: formatMinutes(Math.round(Number(itinerary.totalTime) / 60)),
    cost: itineraryFare(itinerary).toLocaleString("ko-KR"),
    transfers: itinerary.transferCount ?? getTransferCount(itinerary.legs),
    summary: getPathSummary(itinerary),
    legs: itinerary.legs,
  };
}

//경로 추천 결과 표시 (최단시간/최단거리/최소환승)
async function showRouteResults() {
  if (!selectedFrom || !selectedTo) {
    alert("출발지와 도착지를 모두 선택해주세요.");
    return;
  }

  const modeItems = document.querySelectorAll(".mode-item");

  // 기존 active 제거
  modeItems.forEach((item) => item.classList.remove("active-mode"));

  // 현재 선택된 mode를 기본으로 지정하거나 특정 조건에 따라 선택
  // 여기서는 일단 "대중교통"만 고정으로 선택한다고 가정
  const defaultMode = document.querySelector('.mode-item[data-mode="transit"]');
  if (defaultMode) {
    defaultMode.classList.add("active-mode");
  }

  localStorage.setItem("selectedFrom", JSON.stringify(selectedFrom));
  localStorage.setItem("selectedTo", JSON.stringify(selectedTo));
  const departAt = getDepartAt();
  localStorage.setItem("selectedDepartAt", departAt);

  let data;
  try {
    data = await getTmapRoute(selectedFrom, selectedTo, departAt);
  } catch (err) {
    alert(err.message);
    return;
  }
  let itineraries = data?.metaData?.plan?.itineraries;

  if (!itineraries || itineraries.length === 0) {
    alert(getRouteErrorMessage(data) || "추천 경로를 찾을 수 없습니다.");
    return;
  }

  itineraries = removeDuplicateItineraries(itineraries);

  // 조건별 최적 경로 찾기
  // 장거리 구간이 운행하지 않는 경로는 다른 경로가 있으면 고르지 않는다 (상세 route.js 와 같은 기준)
  const candidates = preferOperating(itineraries);
  const shortestTime = candidates.reduce((a, b) => (a.totalTime < b.totalTime ? a : b));
  const shortestDistance = candidates.reduce((a, b) => (a.totalDistance < b.totalDistance ? a : b));
  const leastTransfer = candidates.reduce((a, b) => (a.transferCount < b.transferCount ? a : b));

  const routeData = {
    shortestTime: formatRouteData(shortestTime),
    shortestDistance: formatRouteData(shortestDistance),
    leastTransfer: formatRouteData(leastTransfer),
  };

  const card = document.querySelector(".search-card");
  card.classList.add("flat");

  const routeForm = document.getElementById("routeForm");
  document.getElementById("routeList").innerHTML = "";
  routeForm.style.display = "block";

  displayRoutes(routeData, isBookableDate(departAt));
}

//추천 경로 HTML 렌더링
function displayRoutes(data, bookableDate) {
  const container = document.getElementById("routeList");
  const labels = {
    shortestTime: "최단시간",
    shortestDistance: "최단거리",
    leastTransfer: "최소환승",
  };

  container.innerHTML = "";

  for (const key in data) {
    const route = data[key];
    if (!route || !Array.isArray(route.legs)) continue;

    const li = document.createElement("li");

    li.innerHTML = html`
      <a class="route-card" href="/route?type=${key}">
        <div class="route-summary">
          <div class="route-type">${labels[key]}</div>
          <div class="total-info">
            <span class="time">⏱ ${route.time}</span>
            <span class="price">💸 ${route.cost}원</span>
            <span class="transfer-info">🔁 환승 ${route.transfers}회</span>
            ${hasPayableLeg(route) ? (bookableDate ? raw('<span class="bookable">🎫 예매 가능</span>') : raw('<span class="bookable view-only">🗓 조회 전용 (예매는 당일만)</span>')) : ""}
          </div>
        </div>
        <div class="route-summary-line">${route.summary}</div>
      </a>
    `;

    container.appendChild(li);
  }
}

// 대중교통 경로 검색 (서버가 TMAP 대중교통 API를 대신 호출)
// 결과는 상세(route.js)·요금비교(compare.js)가 다시 호출하지 않도록 sessionStorage 에 저장 (transport.js LAST_SEARCH_KEY)

async function getTmapRoute(from, to, departAt) {
  const request = buildRouteRequest(from, to, departAt);

  const response = await fetch("/api/route/transit", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(request),
  });
  const data = await response.json().catch(() => ({}));

  if (response.status === 401) {
    throw new Error("로그인 후 경로를 검색할 수 있습니다.");
  }
  if (!response.ok) {
    throw new Error(getRouteErrorMessage(data) || `경로 검색에 실패했습니다. (${response.status})`);
  }

  sessionStorage.setItem(LAST_SEARCH_KEY, JSON.stringify({ request, data }));
  return data;
}

// TMAP 오류 응답({result: {message}}, {error: {message}})에서 안내 문구 추출
function getRouteErrorMessage(data) {
  return data?.result?.message || data?.error?.message || (typeof data?.error === "string" ? data.error : "");
}

// 주변정보: 이 화면에서 고른 출발·도착지를 넘긴다 (탭을 닫으면 지워지는 sessionStorage, 서버에는 저장하지 않음)
// 메뉴는 /nearby 링크라, 여기서는 넘길 값만 저장하고 이동은 막지 않는다
const NEARBY_PLACES_KEY = "nearbyPlaces";

function rememberNearbyPlaces() {
  try {
    if (selectedFrom || selectedTo) {
      sessionStorage.setItem(NEARBY_PLACES_KEY, JSON.stringify({ from: selectedFrom, to: selectedTo }));
    }
  } catch {
    // 저장할 수 없으면 주변정보 화면이 마지막 검색의 출발·도착지를 쓴다
  }
}
