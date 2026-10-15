/* global PAYABLE_MODES, isOperatingLeg, isPayableLeg, hasPayableLeg, getTransportLabel, buildRouteRequest, isBookableDate, formatDepartAt -- transport.js */
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

  const selectedFrom = JSON.parse(localStorage.getItem("selectedFrom")) || {};
  const selectedTo = JSON.parse(localStorage.getItem("selectedTo")) || {};
  const departAt = localStorage.getItem("selectedDepartAt") || "";
  const bookable = isBookableDate(departAt);

  try {
    const data = await loadTransitRoute(selectedFrom, selectedTo, departAt);
    const itineraries = data?.metaData?.plan?.itineraries;
    if (!itineraries || itineraries.length === 0) {
      throw new Error(data?.result?.message || "경로 정보를 찾을 수 없습니다.");
    }
    const bestIndex = getBestItineraryIndex(type, data);
    const itinerary = itineraries[bestIndex];
    renderSummary(itinerary, departAt);
    renderSteps(itinerary, bookable);
    renderPlaceMatches(itinerary);
    renderBookingFooter(itinerary, departAt);
    drawRoute(itinerary.legs);
  } catch (error) {
    console.error("경로 조회 실패:", error);
    const sidebar = document.querySelector(".sidebar-content");
    sidebar.innerHTML = "";
    const message = document.createElement("p");
    message.textContent = error.message || "경로 정보를 불러올 수 없습니다.";
    sidebar.appendChild(message);
  }
});

/**
 * 홈에서 검색한 결과(sessionStorage)를 재사용하고, 없거나 출발·도착지가 다르면 서버에 다시 요청
 * @param {object} from - 출발지 {x, y}
 * @param {object} to - 도착지 {x, y}
 * @param {string} departAt - 출발 일시 yyyyMMddHHmm, 비어 있으면 현재 시각
 * @returns {Promise<object>} TMAP 대중교통 응답
 */
async function loadTransitRoute(from, to, departAt) {
  const request = buildRouteRequest(from, to, departAt);

  const cached = JSON.parse(sessionStorage.getItem("transitRouteResult") || "null");
  if (cached && JSON.stringify(cached.request) === JSON.stringify(request)) {
    return cached.data;
  }

  const response = await fetch("/api/route/transit", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(request),
  });
  const data = await response.json().catch(() => ({}));
  if (!response.ok) {
    throw new Error(data?.result?.message || data?.error?.message || "경로 정보를 불러올 수 없습니다.");
  }
  sessionStorage.setItem("transitRouteResult", JSON.stringify({ request, data }));
  return data;
}

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
  return line
    .trim()
    .split(" ")
    .map((pair) => {
      const [lon, lat] = pair.split(",").map(Number);
      return new Tmapv2.LatLng(lat, lon);
    });
}
/**
 * 경로 요약 정보 렌더링 (상세로 보여 주는 경로 기준)
 * @param {object} itinerary - 여정 객체
 * @param {string} departAt - 출발 일시 yyyyMMddHHmm
 */
function renderSummary(itinerary, departAt) {
  const summaryBox = document.querySelector(".summary-box");

  const totalTimeMin = Math.round(itinerary.totalTime / 60); // 총 소요 시간(분)
  const totalDistanceKm = (itinerary.totalDistance / 1000).toFixed(1); // 총 거리(km)
  const transferCount = itinerary.transferCount; // 환승 횟수

  summaryBox.innerHTML = `
    <div class="summary-item">
      <span>출발</span>
      <strong>${formatDepartAt(departAt)}</strong>
    </div>
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
 * @param {boolean} bookable - 예매할 수 있는 날짜인지 (당일 출발)
 */
function renderSteps(itinerary, bookable) {
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
    stepElement.dataset.leg = JSON.stringify(leg); // ← 여기에 실제 leg 정보 저장

    // 예매 대상(기차·고속·시외버스·항공)은 처음부터 선택해 두고, 나머지는 안내 전용
    if (isPayableLeg(leg) && bookable) {
      stepElement.classList.add("selected");
      stepElement.title = "클릭해서 예매할 구간을 고르거나 뺄 수 있습니다.";
      stepElement.addEventListener("click", () => {
        stepElement.classList.toggle("selected");
      });
    } else {
      stepElement.classList.add("disabled");
      stepElement.title = isPayableLeg(leg)
        ? "예매는 당일 출발편만 가능합니다."
        : PAYABLE_MODES.includes(mode) && !isOperatingLeg(leg)
          ? "검색 시각에 운행하지 않는 구간입니다."
          : "안내 전용 구간입니다. 요금은 현장에서 냅니다.";
    }

    stepElement.innerHTML = `
      <div class="step-content">
        <h4>${index + 1}. ${startName} → ${endName} (index)</h4>
        <p>${getTransportLabel(leg)} (${mode})</p>
        <p class="step-desc">${stepDescription}</p>
        ${PAYABLE_MODES.includes(mode) && !isOperatingLeg(leg) ? '<p class="step-warn">검색 시각에 운행하지 않는 구간입니다</p>' : ""}
        ${PAYABLE_MODES.includes(mode) ? '<p class="step-match">역·터미널 확인 중…</p>' : ""}
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

  if (PAYABLE_MODES.includes(mode)) {
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

/**
 * 예매할 구간이 있는 경로에만 장바구니 담기·결제 버튼을 보여 주고, 없으면 안내 문구로 바꾼다
 * @param {object} itinerary - 여정 객체
 * @param {string} departAt - 출발 일시 yyyyMMddHHmm
 */
function renderBookingFooter(itinerary, departAt) {
  const footer = document.querySelector(".sidebar-footer");
  if (!footer) return;

  const notice = document.createElement("p");
  notice.classList.add("booking-notice");

  if (hasPayableLeg(itinerary) && !isBookableDate(departAt)) {
    footer.querySelectorAll("button").forEach((button) => (button.style.display = "none"));
    notice.textContent = `${formatDepartAt(departAt)} 출발 경로는 조회만 할 수 있습니다. 예매는 당일 출발편만 가능합니다.`;
    footer.prepend(notice);
    return;
  }

  if (hasPayableLeg(itinerary)) {
    notice.textContent = "기차·고속버스·시외버스·항공 구간을 예매할 수 있습니다. 구간을 눌러 고르거나 뺄 수 있습니다.";
    footer.before(notice);
    return;
  }

  footer.querySelectorAll("button").forEach((button) => (button.style.display = "none"));
  const hasLongDistance = itinerary.legs.some((leg) => PAYABLE_MODES.includes(leg.mode));
  notice.textContent = hasLongDistance
    ? "이 경로의 장거리 구간은 검색 시각에 운행하지 않아 예매할 수 없습니다. 다른 경로나 시각을 골라 주세요."
    : "이 경로는 시내 교통(버스·지하철 등)만 있어 예매할 구간이 없습니다. 요금은 현장에서 냅니다.";
  footer.prepend(notice);
}

/**
 * 장거리 구간의 출발·도착 장소를 공공데이터(TAGO) 역·터미널·공항과 맞춰 보여 준다 (v1.6 편 조회의 기준)
 * @param {object} itinerary - 여정 객체
 */
async function renderPlaceMatches(itinerary) {
  const steps = [...document.querySelectorAll(".route-step")];
  const targets = itinerary.legs
    .map((leg, index) => ({ leg, element: steps[index] }))
    .filter(({ leg }) => PAYABLE_MODES.includes(leg.mode));
  if (targets.length === 0) return;

  const toPoint = (leg, place) => ({
    mode: leg.mode,
    type: leg.type ?? null,
    route: leg.route ?? null,
    name: place.name,
    lat: Number(place.lat),
    lon: Number(place.lon),
  });
  const points = targets.flatMap(({ leg }) => [toPoint(leg, leg.start), toPoint(leg, leg.end)]);

  let results;
  try {
    const response = await fetch("/api/place/match", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ points }),
    });
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    results = await response.json();
  } catch (error) {
    console.error("역·터미널 확인 실패:", error);
    targets.forEach(({ element }) => setMatchText(element, "역·터미널을 확인하지 못했습니다"));
    return;
  }

  const describe = (r) => {
    if (r?.result?.status !== "MATCHED") return "찾지 못함";
    const how = { ADDRESS: "주소 일치", DISTANCE: `${r.result.distanceMeters}m 거리`, NAME: "이름 일치" };
    return `${r.result.tagoName}${how[r.result.method] ? ` (${how[r.result.method]})` : ""}`;
  };
  targets.forEach(({ element }, i) => {
    const [from, to] = [results[i * 2], results[i * 2 + 1]];
    setMatchText(element, `${from?.kindLabel || "역·터미널"}: ${describe(from)} → ${describe(to)}`);
  });
}

function setMatchText(stepElement, text) {
  const target = stepElement?.querySelector(".step-match");
  if (target) target.textContent = text;
}
