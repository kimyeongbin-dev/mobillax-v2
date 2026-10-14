let selectedFrom = null; //출발지 선택 상태 저장용 변수
let selectedTo = null; //도착지 선택 상태 저장용 변수
let currentFocusType = null; //현재 선택 상태 저장용 변수

//검색창 (출발지/도착지) 상단 변경
function updateSuggestHeader() {
  const header = document.querySelector(".suggest-header");
  if (header) {
    header.innerText = currentFocusType === 'from' ? '출발지 검색' : '도착지 검색';
  }
}

//input에 focus했을때 updateSuggestHeader() 쓰기
document.addEventListener("DOMContentLoaded", () => {
  document.getElementById("fromInput").addEventListener("focus", () => {
    currentFocusType = 'from';
    updateSuggestHeader();
  });
  document.getElementById("toInput").addEventListener("focus", () => {
    currentFocusType = 'to';
    updateSuggestHeader();
  });
});

//검색창 박스 표시
function showSuggestionBox() {
  const box = document.getElementById("suggestions");
  box.style.display = 'block';
}

//카카오 장소 검색 API로 자동완성 목록 보여주기
function showSuggestions(value) {
  const type = currentFocusType;
  const suggestionsBox = document.getElementById("suggestions");

  if (!value.trim()) {
    suggestionsBox.style.display = 'none';
    return;
  }

  const ps = new kakao.maps.services.Places();
  ps.keywordSearch(value, function (data, status) {
    const header = `<div class="suggest-header">${type === 'from' ? '출발지 검색' : '도착지 검색'}</div>`;

    if (status !== kakao.maps.services.Status.OK) {
        suggestionsBox.innerHTML = `
        <div class="suggest-header">${type === 'from' ? '출발지 검색' : '도착지 검색'}</div>
        <div class="empty-result" id="empty">
            <div class="icon"><img src="https://img.icons8.com/?size=100&id=132&format=png&color=1A1A1A" alt="검색 아이콘" width="100" height="100"></div>
            <div>검색결과가 없습니다.</div>
        </div>
        `;
        suggestionsBox.style.display = 'block';
        return;
    }

    const html = data.slice(0, 5).map(place => `
      <div class="item" onclick="selectSuggestion('${place.place_name}', '${type}', '${place.x}', '${place.y}')">
        <span>${place.place_name}</span>
        <small>${place.address_name}</small>
      </div>
    `).join('');

    suggestionsBox.innerHTML = header + html;
    suggestionsBox.style.display = 'block';
  });
}

//검색 결과 클릭 시 출발지/도착지 값 설정
function selectSuggestion(name, type, x, y) {
  document.getElementById(type + "Input").value = name;
  document.getElementById("suggestions").style.display = 'none';

  if (type === 'from') {
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
    legs: itinerary.legs
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
  const name = leg.route || leg.routeName || leg.airline || '';
  const cleanName = name.replace(/undefined|null/gi, '').trim();
  switch (leg.mode) {
      case "WALK":
        return "🚶 도보";
      case "BUS":
        return `${cleanName || '버스'}`;
      case "SUBWAY":
        return `${cleanName || '지하철'}`;
      case "TRAIN":
        return `${cleanName || '기차'}`;
      case "AIRPLANE":
        return `${cleanName || '항공편'}`;
      case "EXPRESSBUS":
        return `${cleanName || '고속버스'}`;
      default:
        return `${leg.mode}`;
  }
}

// 도보 제외 요약 경로 표시용 (아이콘/노선명)
function getPathSummary(path) {
  if (!path.legs || path.legs.length === 0) return "경로 없음";
  const parts = path.legs.filter(leg => leg.mode !== "WALK").map(leg => getModeLabel(leg));
  return parts.join(" → ");
}

//중복 제거 함수 (시간, 거리, 환승수, legs가 동일한 경우)
function removeDuplicateItineraries(itineraries) {
  const seen = new Set();
  const unique = [];

  for (const route of itineraries) {
    const pathKey = route.legs
        .filter(leg => leg.mode !== "WALK")
        .map(leg => `${leg.mode}:${leg.route || leg.routeName || leg.busNo || ''}`)
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

   const modeItems = document.querySelectorAll('.mode-item');

   // 기존 active 제거
   modeItems.forEach(item => item.classList.remove('active-mode'));

   // 현재 선택된 mode를 기본으로 지정하거나 특정 조건에 따라 선택
   // 여기서는 일단 "대중교통"만 고정으로 선택한다고 가정
   const defaultMode = document.querySelector('.mode-item[data-mode="transit"]');
   if (defaultMode) {
     defaultMode.classList.add('active-mode');
   }

   //좌표 확인용 (지워도 상관없음)
   console.log("출발지 좌표:", selectedFrom);
   console.log("도착지 좌표:", selectedTo);
   localStorage.setItem("selectedFrom", JSON.stringify(selectedFrom));
   localStorage.setItem("selectedTo", JSON.stringify(selectedTo));

   const data = await getTmapRoute(selectedFrom.x, selectedFrom.y, selectedTo.x, selectedTo.y);
       let itineraries = data?.metaData?.plan?.itineraries;

   if (!itineraries || itineraries.length === 0) {
       alert("추천 경로를 찾을 수 없습니다.");
       return;
   }

   itineraries = removeDuplicateItineraries(itineraries);

   // 디버깅용 로그
   //여기서
   console.log("전체 경로 itineraries:");
   console.table(itineraries.map((route, idx) => ({
      경로: `경로 ${idx + 1}`,
      총시간_분: Math.round(route.totalTime / 60),
      총거리_km: (route.totalDistance / 1000).toFixed(1),
      환승횟수: route.transferCount,
      요금_원: `${(route.fare?.regular?.totalFare ?? getTotalFareFromLegs(route.legs)).toLocaleString()}원`,
      이동수단: getPathSummary({ legs: route.legs })
   })));

   console.log("첫 번째 경로의 상세 legs 정보:");
   console.table(itineraries[0].legs.map((leg, idx) => ({
      순번: idx + 1,
      이동수단: leg.mode,
      출발지: leg.start?.name,
      도착지: leg.end?.name,
      소요시간_분: Math.round(leg.sectionTime / 60),
      요금: leg.fare?.regular?.totalFare ?? 0
   })));
   //여기까지

   // 조건별 최적 경로 찾기
   const shortestTime = itineraries.reduce((a, b) => a.totalTime < b.totalTime ? a : b);
   const shortestDistance = itineraries.reduce((a, b) => a.totalDistance < b.totalDistance ? a : b);
   const leastTransfer = itineraries.reduce((a, b) => a.transferCount < b.transferCount ? a : b);

   const routeData = {
     shortestTime: formatRouteData(shortestTime),
     shortestDistance: formatRouteData(shortestDistance),
     leastTransfer: formatRouteData(leastTransfer)
   };

   const card = document.querySelector(".search-card");
   card.classList.add("flat");

   const routeForm = document.getElementById("routeForm");
   document.getElementById("routeList").innerHTML = "";
   routeForm.style.display = "block";

   displayRoutes(routeData);
}

//추천 경로 HTML 렌더링
function displayRoutes(data) {
  const container = document.getElementById("routeList");
  const labels = {
    shortestTime: "최단시간",
    shortestDistance: "최단거리",
    leastTransfer: "최소환승"
  };

  container.innerHTML = "";

  for (const key in data) {
    const route = data[key];
    if (!route || !Array.isArray(route.legs)) continue;

    const li = document.createElement("li");

    li.innerHTML = `
      <a class="route-card" href="/route?type=${key}">
        <div class="route-summary">
          <div class="route-type">${labels[key]}</div>
          <div class="total-info">
            <span class="time">⏱ ${route.time}분</span>
            <span class="price">💸 ${route.cost}원</span>
            <span class="transfer-info">🔁 환승 ${route.transfers}회</span>
          </div>
        </div>
        <div class="route-summary-line">
          ${route.summary}
        </div>
      </a>
    `;

    container.appendChild(li);
  }
}

//Tmap 대중교통 API 호출 함수 (POST)
async function getTmapRoute(fromX, fromY, toX, toY) {
//  const url = 'https://apis.openapi.sk.com/transit/routes';
//
//  const headers = {
//    'Content-Type': 'application/json',
//    'Accept': 'application/json',
//    'appKey': '<TMAP_APPKEY>'
//  };
//
//  const body = {
//    startX: fromX,
//    startY: fromY,
//    endX: toX,
//    endY: toY,
//    count: 10,
//    lang: 0,
//    format: 'json'
//  };

  try {
//    const response = await fetch(url, {
//      method: 'POST',
//      headers: headers,
//      body: JSON.stringify(body)
//    });
//
//    const data = await response.json();
//    console.log("Tmap API 응답:", data);
//
//    if (!response.ok) {
//      throw new Error(`HTTP 오류 상태: ${response.status}`);
//    }
//    console.log("🔥 전체 Tmap API 응답 결과:", JSON.stringify(data, null, 2)); //api 확인용
//    return data;
// ✅ 무조건 Mock JSON만 사용하도록 강제
    const dummy = await fetch('javascript/dummy/route_dummy_data.json');
    const data = await dummy.json();
    return data;
  } catch (err) {
    console.error("Tmap API 호출 실패, 더미 데이터 사용:", err.message || err);
    try {
      const dummy = await fetch('javascript/dummy/route_dummy_data.json');
      const data = await dummy.json();
      return data;
    } catch (jsonErr) {
      console.error("더미 JSON 불러오기 실패:", jsonErr);
      return { metaData: { plan: { itineraries: [] } } };
    }
  }
}

function alertComingSoon() {
  alert("해당 기능은 준비 중입니다.");
}

document.addEventListener("DOMContentLoaded", () => {
  const authBox = document.querySelector(".auth-buttons");

  if (!authBox) return;

  const isLoggedIn = localStorage.getItem("login") === "true"; // 예: 로그인 시 true 저장한다고 가정

  if (isLoggedIn) {
    authBox.innerHTML = `
      <a href="/member/mypage.html">
        <img src="https://img.icons8.com/ios-filled/50/user.png" alt="MyPage">마이페이지
      </a>
      <a href="#" onclick="logout()">
        <img src="https://img.icons8.com/ios-filled/50/logout-rounded-left.png" alt="Logout">로그아웃
      </a>
    `;
  }
});

function logout() {
  localStorage.removeItem("login");
  alert("로그아웃 되었습니다.");
  location.reload();
}