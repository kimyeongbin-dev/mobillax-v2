/**
 * 상세 경로 페이지 스크립트
 * - Tmap API를 이용하여 경로 요약, 경로 단계 표시 및 지도에 경로 렌더링
 * @lastModified 2025-06-09
 */

let resultDrawArr = [];
let map;

document.addEventListener("DOMContentLoaded", async function () {
  const type = new URLSearchParams(window.location.search).get("type") || "shortestTime";
  const titleMap = {
    shortestTime: "최단시간 상세경로",
    shortestDistance: "최단거리 상세경로",
    leastTransfer: "최소환승 상세경로",
  };

  const titleElement = document.querySelector("#routeTitle");
  if (titleElement && titleMap[type]) {
    titleElement.textContent = titleMap[type];
    document.title = `MobilLax - ${titleMap[type]}`;
  }

  map = new Tmapv2.Map("map_div", {
    center: new Tmapv2.LatLng(37.5652045, 126.98702028),
    width: "100%",
    height: "100%",
    zoom: 16,
  });

  const headers = {
    accept: "application/json",
    "Content-Type": "application/json",
    appKey: "your-tmap-appkey",
  };

  const selectedFrom = JSON.parse(localStorage.getItem("selectedFrom")) || {};
  const selectedTo = JSON.parse(localStorage.getItem("selectedTo")) || {};
  const params = {
    startX: Number(selectedFrom.x),
    startY: Number(selectedFrom.y),
    endX: Number(selectedTo.x),
    endY: Number(selectedTo.y),
    lang: 0,
    format: "json",
    count: 10,
    searchDttm: "202505221000",
  };

  try {
    const response = await fetch("https://apis.openapi.sk.com/transit/routes/", {
      method: "POST",
      headers,
      body: JSON.stringify(params),
    });
    const data = await response.json();
    const bestIndex = getBestItineraryIndex(type, data);
    const itinerary = data.metaData.plan.itineraries[bestIndex];
    renderSummary(data);
    renderSteps(itinerary);
    drawRoute(itinerary.legs);
  } catch (error) {
    try {
      const fallback = await fetch("https://3173cb8e-d014-4dcb-8da9-2e53ad672e15.mock.pstmn.io");
      const text = await fallback.text();
      const data = JSON.parse(text);
      const bestIndex = getBestItineraryIndex(type, data);
      const itinerary = data.metaData.plan.itineraries[bestIndex];
      renderSummary(data);
      renderSteps(itinerary);
      drawRoute(itinerary.legs);
    } catch (fallbackError) {
      document.querySelector(".sidebar-content").innerHTML = "<p>경로 정보를 불러올 수 없습니다.</p>";
    }
  }
});

/**
 * 최적 경로 인덱스를 구하는 함수
 * @param {string} type - 경로 기준 타입
 * @param {object} data - API 응답 데이터
 * @returns {number} bestIndex - 최적 경로 인덱스
 */
function getBestItineraryIndex(type, data) {
  const itineraries = data.metaData.plan.itineraries;
  let bestIndex = 0;
  if (type === "shortestDistance") {
    let minDistance = Infinity;
    itineraries.forEach((itinerary, index) => {
      if (itinerary.totalDistance < minDistance) {
        minDistance = itinerary.totalDistance;
        bestIndex = index;
      }
    });
  } else if (type === "shortestTime") {
    let minTime = Infinity;
    itineraries.forEach((itinerary, index) => {
      if (itinerary.totalTime < minTime) {
        minTime = itinerary.totalTime;
        bestIndex = index;
      }
    });
  } else if (type === "leastTransfer") {
    let minTransfer = Infinity;
    itineraries.forEach((itinerary, index) => {
      if (itinerary.transferCount < minTransfer) {
        minTransfer = itinerary.transferCount;
        bestIndex = index;
      }
    });
  }
  return bestIndex;
}

/**
 * 경로 그리기
 * @param {Array} legs - 경로 구간 배열
 */
function drawRoute(legs) {
  const routeBounds = new Tmapv2.LatLngBounds();
  for (const leg of legs) {
    const startLatLng = new Tmapv2.LatLng(Number(leg.start.lat), Number(leg.start.lon));
    const endLatLng = new Tmapv2.LatLng(Number(leg.end.lat), Number(leg.end.lon));
    routeBounds.extend(startLatLng);
    routeBounds.extend(endLatLng);

    if (leg.mode === "WALK" && leg.steps) {
      for (const step of leg.steps) {
        const points = parseLineString(step.linestring);
        drawLine(points, "#888888");
      }
    } else if (leg.passShape?.linestring) {
      const color = `#${leg.routeColor || "0068B7"}`;
      const points = parseLineString(leg.passShape.linestring);
      drawLine(points, color);
    }
  }
  map.panToBounds(routeBounds);
}

/**
 * 폴리라인 그리기
 * @param {Array} latlngs - 좌표 배열
 * @param {string} color - 선 색상
 */
function drawLine(latlngs, color) {
  const polyline = new Tmapv2.Polyline({
    path: latlngs,
    strokeColor: color,
    strokeWeight: 6,
    map: map,
  });
  resultDrawArr.push(polyline);
}

/**
 * LineString 파싱
 * @param {string} line - 좌표 문자열
 * @returns {Array} Tmapv2.LatLng 배열
 */
function parseLineString(line) {
  return line.trim().split(" ").map((pair) => {
    const [lon, lat] = pair.split(",").map(Number);
    return new Tmapv2.LatLng(lat, lon);
  });
}
/**
 * 경로 요약 정보 렌더링
 * @param {object} data - API 응답 데이터
 */
function renderSummary(data) {
  const itinerary = data.metaData.plan.itineraries[0];
  const summaryBox = document.querySelector(".summary-box");

  const totalTimeMin = Math.round(itinerary.totalTime / 60); // 총 소요 시간(분)
  const totalDistanceKm = (itinerary.totalDistance / 1000).toFixed(1); // 총 거리(km)
  const transferCount = itinerary.transferCount; // 환승 횟수

  summaryBox.innerHTML = `
    <div class="summary-item">
      <span>총 소요 시간</span>
      <strong>${Math.floor(totalTimeMin / 60)}시간 ${totalTimeMin % 60}분</strong>
    </div>
    <div class="summary-item">
      <span>총 거리</span>
      <strong>${totalDistanceKm}km</strong>
    </div>
    <div class="summary-item">
      <span>환승 횟수</span>
      <strong>${transferCount}회</strong>
    </div>
  `;
}

/**
 * 상세 경로 단계 렌더링
 * @param {object} itinerary - 여정 객체
 */
function renderSteps(itinerary) {
  const container = document.querySelector(".sidebar-content");
  container.innerHTML = "";

  itinerary.legs.forEach((leg, index) => {
    const mode = leg.mode || "UNKNOWN";
    const iconClass = getIcon(mode); // 아이콘용 클래스 (단, 출력은 제거됨)
    const rawRoute = leg.route || "노선 정보 없음";
    const stepDescription = getStepDescription(leg);
    const startName = leg.start?.name || "출발지 없음";
    const endName = leg.end?.name || "도착지 없음";

    const stepElement = document.createElement("div");
    stepElement.classList.add("route-step");
    stepElement.dataset.index = index;
    stepElement.dataset.leg = JSON.stringify(leg);  // ← 여기에 실제 leg 정보 저장

    if (!leg.routePayment || leg.routePayment <= 0) {
      stepElement.classList.add("disabled");
      stepElement.title = "요금이 없는 구간은 선택할 수 없습니다.";
    } else {
      stepElement.addEventListener("click", () => {
        stepElement.classList.toggle("selected");
      });
    }


    stepElement.innerHTML = `
      <div class="step-content">
        <h4>${index + 1}. ${startName} → ${endName} (index)</h4>
        <p>${getModeKorean(mode)} (${mode})</p>
        <p class="step-desc">${stepDescription}</p>
      </div>
    `;
    container.appendChild(stepElement);

    if (index < itinerary.legs.length - 1) {
      const arrow = document.createElement("div");
      arrow.classList.add("step-arrow");
      arrow.innerHTML = `<div>↓</div>`;
      container.appendChild(arrow);
    }
  });
}

/**
 * 교통 수단에 따른 아이콘 클래스명 반환 (사용자 표시용)
 * @param {string} mode - 교통 수단 코드
 * @returns {string} 아이콘 클래스명
 */
function getIcon(mode) {
  const icons = {
    BUS: "bus",
    SUBWAY: "subway",
    WALK: "walking",
    TAXI: "car",
  };
  return icons[mode] || "map-marker-alt";
}

/**
 * 교통 수단을 한글로 반환
 * @param {string} mode - 교통 수단 코드
 * @returns {string} 한글 명칭
 */
function getModeKorean(mode) {
  const map = {
    BUS: "버스",
    SUBWAY: "지하철",
    TRAIN: "기차",
    EXPRESSBUS: "고속버스",
    WALK: "도보",
    TAXI: "택시",
  };
  return map[mode] || "이동수단";
}

/**
 * 각 구간에 대한 설명 생성
 * @param {object} leg - 구간 객체
 * @returns {string} 설명 텍스트
 */
function getStepDescription(leg) {
  const mode = leg.mode;
  const sectionTimeMin = leg.sectionTime ? `${Math.round(leg.sectionTime / 60)}분` : null;
  const distanceMeters = leg.distance ? `${Math.floor(leg.distance)}m` : null;
  const paymentWon = leg.routePayment ? `${leg.routePayment.toLocaleString()}원` : null;

  const timeText = sectionTimeMin ? `약 ${sectionTimeMin}` : null;

  if (mode === "BUS") {
    const stopCount = leg.passStopList?.stationList?.length || 0;
    const countText = stopCount > 0 ? `${stopCount}개 정류장 이동 (stopCount)` : null;
    return [timeText, countText].filter(Boolean).join(", ") || "정보 없음";
  }

  if (mode === "EXPRESSBUS" || mode === "TRAIN") {
    const fareText = paymentWon ? `요금 약 ${paymentWon}` : null;
    return [timeText, fareText].filter(Boolean).join(", ") || "정보 없음";
  }

  if (mode === "SUBWAY") {
    const stationCount = leg.passStopList?.stationList?.length || 0;
    const countText = stationCount > 0 ? `${stationCount}개 역 이동 (stationCount)` : null;
    return [timeText, countText].filter(Boolean).join(", ") || "정보 없음";
  }

  if (mode === "WALK") {
    const distText = distanceMeters ? `${distanceMeters} 이동` : null;
    return [timeText, distText].filter(Boolean).join(", ") || "정보 없음";
  }

  return timeText || "정보 없음";
}
