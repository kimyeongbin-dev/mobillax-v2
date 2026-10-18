/* global html, raw, hasPayableLeg, buildRouteRequest, isBookableDate, preferOperating -- transport.js */
let selectedFrom = null; //출발지 선택 상태 저장용 변수
let selectedTo = null; //도착지 선택 상태 저장용 변수
let currentFocusType = null; //현재 선택 상태 저장용 변수

//검색창 (출발지/도착지) 상단 변경
function updateSuggestHeader() {
  const header = document.querySelector(".suggest-header");
  if (header) {
    header.innerText = currentFocusType === "from" ? "출발지 검색" : "도착지 검색";
  }
}

//input에 focus했을때 updateSuggestHeader() 쓰기
document.addEventListener("DOMContentLoaded", () => {
  initDepartInput();
  document.getElementById("fromInput").addEventListener("focus", () => {
    currentFocusType = "from";
    updateSuggestHeader();
  });
  document.getElementById("toInput").addEventListener("focus", () => {
    currentFocusType = "to";
    updateSuggestHeader();
  });
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

// 출발지-도착지 입력값 바꾸기
function swapInputs() {
  const fromInput = document.getElementById("fromInput");
  const toInput = document.getElementById("toInput");
  const temp = fromInput.value;
  fromInput.value = toInput.value;
  toInput.value = temp;
}

// Tmap API 응답을 요약 형태로 변환 (시간/요금/환승/요약)
function formatRouteData(itinerary) {
  // 시간 계산 (초 → 분)
  let totalMinutes = Math.round(Number(itinerary.totalTime) / 60);
  let h = 0;
  let m = totalMinutes;

  // for문으로 시간(h) 계산
  for (; m >= 60; m -= 60) {
    h++;
  }

  const timeText = `${h}시간 ${m}`;

  // 요금 계산: 모든 leg의 totalFare 합산
  let totalFare = 0;

  if (itinerary.fare?.regular?.totalFare) {
    totalFare = itinerary.fare.regular.totalFare;
  } else {
    for (const leg of itinerary.legs) {
      const fare = leg.fare?.regular?.totalFare;
      if (typeof fare === "number") {
        totalFare += fare;
      }
    }
  }
  const fareText = totalFare.toLocaleString("ko-KR");

  // 환승 횟수
  const transfers = itinerary.transferCount ?? getTransferCount(itinerary.legs);

  // 교통수단 요약
  const summary = getPathSummary({ legs: itinerary.legs });

  return {
    time: timeText,
    cost: fareText,
    transfers,
    summary,
    legs: itinerary.legs,
  };
}

// 환승 횟수 계산 (WALK 제외, 모드 변경 기준)
function getTransferCount(legs) {
  let count = 0;
  for (let i = 1; i < legs.length; i++) {
    if (legs[i].mode !== "WALK" && legs[i].mode !== legs[i - 1].mode) {
      count++;
    }
  }
  return count;
}

// 모드 + 노선명 요약 라벨 생성
function getModeLabel(leg) {
  const name = leg.route || leg.routeName || leg.airline || "";
  const cleanName = name.replace(/undefined|null/gi, "").trim();
  switch (leg.mode) {
    case "WALK":
      return "🚶 도보";
    case "BUS":
      return `${cleanName || "버스"}`;
    case "SUBWAY":
      return `${cleanName || "지하철"}`;
    case "TRAIN":
      return `${cleanName || "기차"}`;
    case "AIRPLANE":
      return `${cleanName || "항공편"}`;
    case "EXPRESSBUS":
      return `${cleanName || "고속버스"}`;
    default:
      return `${leg.mode}`;
  }
}

// 도보 제외 요약 경로 표시용 (아이콘/노선명)
function getPathSummary(path) {
  if (!path.legs || path.legs.length === 0) return "경로 없음";
  const parts = path.legs.filter((leg) => leg.mode !== "WALK").map((leg) => getModeLabel(leg));
  return parts.join(" → ");
}

//중복 제거 함수 (시간, 거리, 환승수, legs가 동일한 경우)
function removeDuplicateItineraries(itineraries) {
  const seen = new Set();
  const unique = [];

  for (const route of itineraries) {
    const pathKey = route.legs
      .filter((leg) => leg.mode !== "WALK")
      .map((leg) => `${leg.mode}:${leg.route || leg.routeName || leg.busNo || ""}`)
      .join("→");

    const key = `${Math.round(route.totalTime / 60)}|${route.transferCount}|${pathKey}`;

    if (!seen.has(key)) {
      seen.add(key);
      unique.push(route);
    }
  }

  return unique;
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

  //좌표 확인용 (지워도 상관없음)
  console.log("출발지 좌표:", selectedFrom);
  console.log("도착지 좌표:", selectedTo);
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

  // 디버깅용 로그
  //여기서
  console.log("전체 경로 itineraries:");
  console.table(
    itineraries.map((route, idx) => ({
      경로: `경로 ${idx + 1}`,
      총시간_분: Math.round(route.totalTime / 60),
      총거리_km: (route.totalDistance / 1000).toFixed(1),
      환승횟수: route.transferCount,
      요금_원: `${(route.fare?.regular?.totalFare ?? getTotalFareFromLegs(route.legs)).toLocaleString()}원`,
      이동수단: getPathSummary({ legs: route.legs }),
    })),
  );

  console.log("첫 번째 경로의 상세 legs 정보:");
  console.table(
    itineraries[0].legs.map((leg, idx) => ({
      순번: idx + 1,
      이동수단: leg.mode,
      출발지: leg.start?.name,
      도착지: leg.end?.name,
      소요시간_분: Math.round(leg.sectionTime / 60),
      요금: leg.fare?.regular?.totalFare ?? 0,
    })),
  );
  //여기까지

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
            <span class="time">⏱ ${route.time}분</span>
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
// 결과는 상세 페이지(route.js)가 다시 호출하지 않도록 sessionStorage 에 저장
const TRANSIT_ROUTE_CACHE_KEY = "transitRouteResult";

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

  sessionStorage.setItem(TRANSIT_ROUTE_CACHE_KEY, JSON.stringify({ request, data }));
  return data;
}

// TMAP 오류 응답({result: {message}}, {error: {message}})에서 안내 문구 추출
function getRouteErrorMessage(data) {
  return data?.result?.message || data?.error?.message || (typeof data?.error === "string" ? data.error : "");
}

function alertComingSoon() {
  alert("해당 기능은 준비 중입니다.");
}
