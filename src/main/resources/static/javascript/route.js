/* global html, raw, PAYABLE_MODES, isOperatingLeg, isPayableLeg, hasPayableLeg, getTransportLabel, buildRouteRequest, isBookableDate, formatDepartAt, preferOperating, nowStamp, formatHm, TRANSFER_OPTIONS, getTransferMinutes, setTransferMinutes, addMinutes, connectionReference, pickTripIndex, describeFareTotal, getIncludeLocalFare, setIncludeLocalFare, collectBookingLegs -- transport.js */
/**
 * 상세 경로 페이지 스크립트
 * - 서버가 받아 온 TMAP 대중교통 경로로 요약·단계를 표시하고, 카카오맵에 경로를 그린다 (TMAP 키는 서버 전용, 보안 규칙 S9)
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

  map = new kakao.maps.Map(document.getElementById("map_div"), {
    center: new kakao.maps.LatLng(37.5652045, 126.98702028),
    level: 4,
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
    renderBookingFooter(itinerary, departAt);
    drawRoute(itinerary.legs);
    await renderPlaceMatches(itinerary, departAt);
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
    throw new Error(
      data?.result?.message ||
        data?.error?.message ||
        (typeof data?.error === "string" ? data.error : "") ||
        "경로 정보를 불러올 수 없습니다.",
    );
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
  // 장거리 구간이 운행하지 않는 경로는 다른 경로가 있으면 고르지 않는다 (홈 script.js 와 같은 기준)
  const all = data.metaData.plan.itineraries;
  const allowed = new Set(preferOperating(all));
  const itineraries = all.map((it) => (allowed.has(it) ? it : null));
  let bestIndex = all.findIndex((it) => allowed.has(it));
  if (type === "shortestDistance") {
    let minDistance = Infinity;
    itineraries.forEach((itinerary, index) => {
      if (itinerary && itinerary.totalDistance < minDistance) {
        minDistance = itinerary.totalDistance;
        bestIndex = index;
      }
    });
  } else if (type === "shortestTime") {
    let minTime = Infinity;
    itineraries.forEach((itinerary, index) => {
      if (itinerary && itinerary.totalTime < minTime) {
        minTime = itinerary.totalTime;
        bestIndex = index;
      }
    });
  } else if (type === "leastTransfer") {
    let minTransfer = Infinity;
    itineraries.forEach((itinerary, index) => {
      if (itinerary && itinerary.transferCount < minTransfer) {
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
  const routeBounds = new kakao.maps.LatLngBounds();
  for (const leg of legs) {
    const startLatLng = new kakao.maps.LatLng(Number(leg.start.lat), Number(leg.start.lon));
    const endLatLng = new kakao.maps.LatLng(Number(leg.end.lat), Number(leg.end.lon));
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
  map.setBounds(routeBounds);
}

/**
 * 폴리라인 그리기
 * @param {Array} latlngs - 좌표 배열
 * @param {string} color - 선 색상
 */
function drawLine(latlngs, color) {
  const polyline = new kakao.maps.Polyline({
    path: latlngs,
    strokeColor: color,
    strokeWeight: 6,
    strokeOpacity: 0.9,
  });
  polyline.setMap(map);
  resultDrawArr.push(polyline);
}

/**
 * LineString 파싱
 * @param {string} line - 좌표 문자열
 * @returns {Array} kakao.maps.LatLng 배열 (TMAP 은 WGS84 경도,위도 순서)
 */
function parseLineString(line) {
  return line
    .trim()
    .split(" ")
    .map((pair) => {
      const [lon, lat] = pair.split(",").map(Number);
      return new kakao.maps.LatLng(lat, lon);
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

  summaryBox.innerHTML = html`
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

    // 예매 대상(기차·고속·시외버스·항공)은 편을 고르면 선택되고, 나머지는 안내 전용
    if (isPayableLeg(leg)) {
      stepElement.classList.add("trip-leg");
      stepElement.title = bookable
        ? "편을 고르면 예매할 구간으로 선택됩니다. 구간을 눌러 빼거나 넣을 수 있습니다."
        : "예매는 당일 출발편만 가능합니다. 편 목록은 조회만 할 수 있습니다.";
      stepElement.addEventListener("click", (event) => {
        if (event.target.closest("select")) return;
        if (stepElement.dataset.tripBookable !== "true") return;
        stepElement.classList.toggle("selected");
        refreshFareTotal();
      });
    } else {
      stepElement.classList.add("disabled");
      stepElement.title =
        PAYABLE_MODES.includes(mode) && !isOperatingLeg(leg)
          ? "검색 시각에 운행하지 않는 구간입니다."
          : "안내 전용 구간입니다. 요금은 현장에서 냅니다.";
    }

    stepElement.innerHTML = html`
      <div class="step-content">
        <h4>${index + 1}. ${startName} → ${endName} (index)</h4>
        <p>${getTransportLabel(leg)} (${mode})</p>
        <p class="step-desc">${stepDescription}</p>
        ${PAYABLE_MODES.includes(mode) && !isOperatingLeg(leg) ? raw('<p class="step-warn">검색 시각에 운행하지 않는 구간입니다</p>') : ""}
        ${PAYABLE_MODES.includes(mode) ? raw('<p class="step-match">역·터미널 확인 중…</p>') : ""}
        ${isPayableLeg(leg) ? raw('<div class="step-trip"></div>') : ""}
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
 * 예매 안내 문구와 장바구니 담기·결제 버튼 표시
 * @param {string} text - 안내 문구
 * @param {boolean} showButtons - 버튼을 보일지
 */
function setBookingNotice(text, showButtons) {
  const footer = document.querySelector(".sidebar-footer");
  if (!footer) return;
  let notice = document.querySelector(".booking-notice");
  if (!notice) {
    notice = document.createElement("p");
    notice.classList.add("booking-notice");
  }
  notice.textContent = text;
  footer.querySelectorAll("button").forEach((button) => (button.style.display = showButtons ? "" : "none"));
  if (showButtons) footer.before(notice);
  else footer.prepend(notice);
}

/**
 * 처음 안내: 예매할 구간이 있으면 편을 불러오는 동안 버튼을 숨겨 둔다 (편을 고른 뒤 refreshBookingFooter)
 * @param {object} itinerary - 여정 객체
 * @param {string} departAt - 출발 일시 yyyyMMddHHmm
 */
function renderBookingFooter(itinerary, departAt) {
  if (hasPayableLeg(itinerary) && !isBookableDate(departAt)) {
    setBookingNotice(
      `${formatDepartAt(departAt)} 출발 경로는 조회만 할 수 있습니다. 예매는 당일 출발편만 가능합니다.`,
      false,
    );
    return;
  }
  if (hasPayableLeg(itinerary)) {
    setBookingNotice("장거리 구간의 편 정보를 불러오는 중입니다…", false);
    return;
  }
  const hasLongDistance = itinerary.legs.some((leg) => PAYABLE_MODES.includes(leg.mode));
  setBookingNotice(
    hasLongDistance
      ? "이 경로의 장거리 구간은 검색 시각에 운행하지 않아 예매할 수 없습니다. 다른 경로나 시각을 골라 주세요."
      : "이 경로는 시내 교통(버스·지하철 등)만 있어 예매할 구간이 없습니다. 요금은 현장에서 냅니다.",
    false,
  );
}

/**
 * 편을 불러온 뒤: 예매할 수 있는 편을 고른 구간이 있으면 버튼을 보인다
 * @param {string} departAt - 출발 일시 yyyyMMddHHmm
 */
function refreshBookingFooter(departAt) {
  if (!isBookableDate(departAt) || !document.querySelector(".trip-leg")) return;
  const bookableLegs = document.querySelectorAll('.trip-leg[data-trip-bookable="true"]').length;
  if (bookableLegs > 0) {
    setBookingNotice("고른 편의 운임으로 예매합니다. 구간을 눌러 빼거나 넣을 수 있습니다.", true);
  } else if (document.querySelector('.trip-leg[data-trip-error="true"]')) {
    setBookingNotice("편 정보를 불러오지 못해 지금은 예매할 수 없습니다. 잠시 후 다시 시도해 주세요.", false);
  } else {
    setBookingNotice("오늘 예매할 수 있는 편이 없습니다. 편 정보를 찾지 못했거나 오늘 출발편이 끝났습니다.", false);
  }
}

/**
 * 장거리 구간의 출발·도착 장소를 공공데이터(TAGO) 역·터미널·공항과 맞춰 보여 준다 (v1.6 편 조회의 기준)
 * @param {object} itinerary - 여정 객체
 * @param {string} departAt - 출발 일시 yyyyMMddHHmm
 */
async function renderPlaceMatches(itinerary, departAt) {
  const steps = [...document.querySelectorAll(".route-step")];
  const targets = itinerary.legs
    .map((leg, index) => ({ leg, element: steps[index], index }))
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
    targets.filter(({ leg }) => isPayableLeg(leg)).forEach(({ element, leg }) => markOnsite(element, leg));
    refreshBookingFooter(departAt);
    return;
  }

  const describe = (r) => {
    if (r?.result?.status !== "MATCHED") return "찾지 못함";
    const how = { ADDRESS: "주소 일치", DISTANCE: `${r.result.distanceMeters}m 거리`, NAME: "이름 일치" };
    return `${r.result.tagoName}${how[r.result.method] ? ` (${how[r.result.method]})` : ""}`;
  };
  // 기본 편: 출발 시각(또는 앞 구간에서 고른 편의 도착 + 환승 여유)에 그 사이 구간 이동 시간을 더한 뒤 첫 편
  const start = departAt || nowStamp();
  tripChain.length = 0;
  for (let i = 0; i < targets.length; i++) {
    const { leg, element, index } = targets[i];
    const [from, to] = [results[i * 2], results[i * 2 + 1]];
    setMatchText(element, `${from?.kindLabel || "역·터미널"}: ${describe(from)} → ${describe(to)}`);
    if (!isPayableLeg(leg)) continue;
    if (from?.result?.status !== "MATCHED" || to?.result?.status !== "MATCHED" || from.kind !== to.kind) {
      markOnsite(element, leg);
      continue;
    }
    const previous = lastChainedTrip(tripChain.length);
    const reference = previous
      ? connectionReference(
          previous.trip.arriveAt,
          betweenSeconds(itinerary, previous.index, index),
          getTransferMinutes(),
        )
      : addMinutes(start, Math.ceil(betweenSeconds(itinerary, -1, index) / 60));
    const entry = await loadTrips(element, from.kind, from.result.tagoId, to.result.tagoId, reference, departAt);
    if (entry) tripChain.push({ ...entry, index, itinerary });
  }
  refreshBookingFooter(departAt);
  renderTransferOption();
  renderFareTotal(itinerary);
}

/**
 * 편을 고른 장거리 구간들 (출발 순). 앞 편을 바꾸면 뒤 편을 다시 확인하는 데 쓴다
 * 항목: { index(구간 번호), itinerary, element, trips, select, bookableDate, setTrip(i), trip() }
 */
const tripChain = [];

/** chain 의 position 앞에서 편이 정해진 가장 가까운 구간 */
function lastChainedTrip(position) {
  for (let k = position - 1; k >= 0; k--) {
    const trip = tripChain[k].trip();
    if (trip) return { index: tripChain[k].index, trip };
  }
  return null;
}

/** 두 구간 사이(fromIndex 다음부터 toIndex 앞까지) 구간의 이동 시간 합(초) */
function betweenSeconds(itinerary, fromIndex, toIndex) {
  return itinerary.legs.slice(fromIndex + 1, toIndex).reduce((sum, l) => sum + Number(l.sectionTime || 0), 0);
}

/** chain 의 position 구간을 탈 수 있는 가장 이른 시각 (앞 편이 없으면 null) */
function chainReference(position) {
  const entry = tripChain[position];
  const previous = lastChainedTrip(position);
  if (!previous) return null;
  return connectionReference(
    previous.trip.arriveAt,
    betweenSeconds(entry.itinerary, previous.index, entry.index),
    getTransferMinutes(),
  );
}

/**
 * 앞 편을 바꾼 뒤: 뒤 구간마다 이어 탈 수 있는지 확인한다 (v2.6)
 * - 뒤 편이 앞 편 도착 + 이동 시간 + 환승 여유보다 이르면, 그 뒤 첫 편으로 다시 고르고 알린다
 * - 이어 탈 편이 없으면 그 구간을 예매에서 빼고 알린다
 * - 뒤 편을 그대로 탈 수 있으면 사용자가 고른 편을 바꾸지 않는다
 * @param {number} position - 바꾼 구간의 chain 위치
 */
function repickFollowingTrips(position) {
  for (let k = position + 1; k < tripChain.length; k++) {
    const entry = tripChain[k];
    const reference = chainReference(k);
    const current = entry.trip();
    if (!reference || !current || current.departAt >= reference) {
      if (current) setChainNote(entry.element, "");
      continue;
    }
    const next = pickTripIndex(entry.trips, reference, entry.bookableDate);
    if (next >= 0) {
      entry.setTrip(next);
      setChainNote(
        entry.element,
        `앞 편이 바뀌어 ${formatHm(entry.trips[next].departAt)} 출발 편으로 다시 골랐습니다.`,
      );
    } else {
      entry.element.classList.remove("selected");
      setChainNote(
        entry.element,
        `${formatHm(reference)} 이후에 탈 수 있는 편이 없어 예매에서 뺐습니다. 앞 편을 다시 골라 주세요.`,
      );
    }
  }
}

/**
 * 사용자가 직접 고른 편이 앞 편과 이어지지 않으면 알린다 (고른 편은 바꾸지 않는다)
 * @param {number} position - 고른 구간의 chain 위치
 */
function checkConnection(position) {
  const entry = tripChain[position];
  const reference = chainReference(position);
  const current = entry?.trip();
  if (!reference || !current) return;
  setChainNote(
    entry.element,
    current.departAt >= reference
      ? ""
      : `앞 편 도착 뒤 이동 시간과 환승 여유(${getTransferMinutes()}분)를 더하면 ${formatHm(reference)}입니다. 이 편은 놓칠 수 있습니다.`,
  );
}

function setChainNote(stepElement, text) {
  let note = stepElement.querySelector(".chain-note");
  if (!note) {
    note = document.createElement("p");
    note.classList.add("chain-note");
    stepElement.querySelector(".step-trip")?.appendChild(note);
  }
  note.textContent = text;
  note.hidden = !text;
}

/**
 * 예매 정보(TAGO 편)가 없는 장거리 구간: 공항 리무진·일부 심야 시외버스 등. 예매하지 않고 현장 결제로 안내한다
 */
function markOnsite(element, leg) {
  element.dataset.onsite = "true";
  const fare = Number(leg.routePayment) > 0 ? ` (예상 ${Number(leg.routePayment).toLocaleString()}원)` : "";
  setTripMessage(element, `예매 정보가 없는 구간입니다. 탑승할 때 현장에서 결제하세요${fare}`);
}

/** 금액 줄을 그린 경로 (편·선택이 바뀌면 다시 계산) */
let fareItinerary = null;

/**
 * 금액 줄과 "시내 교통비 포함" 선택 (v2.6)
 * - 예매 금액은 고른 편의 운임 합
 * - 현장 결제 예상은 시내 교통(경로 전체 요금 - 장거리 구간 요금 합) + 예매 정보가 없는 장거리 구간
 */
function renderFareTotal(itinerary) {
  fareItinerary = itinerary;
  const box = document.createElement("div");
  box.classList.add("fare-total");
  const line = document.createElement("p");
  line.classList.add("fare-total-line");
  const toggle = document.createElement("label");
  toggle.classList.add("fare-toggle");
  const checkbox = document.createElement("input");
  checkbox.type = "checkbox";
  checkbox.checked = getIncludeLocalFare();
  checkbox.addEventListener("change", () => {
    setIncludeLocalFare(checkbox.checked);
    refreshFareTotal();
  });
  toggle.append(checkbox, " 시내 교통비 포함");
  box.append(line, toggle);
  document.querySelector(".summary-box")?.after(box);
  refreshFareTotal();
}

function refreshFareTotal() {
  const line = document.querySelector(".fare-total-line");
  if (!line || !fareItinerary) return;
  const steps = [...document.querySelectorAll(".route-step")];
  const longFare = fareItinerary.legs
    .filter((leg) => PAYABLE_MODES.includes(leg.mode))
    .reduce((sum, leg) => sum + Number(leg.routePayment || 0), 0);
  const localFare = Math.max(0, Number(fareItinerary.fare?.regular?.totalFare || 0) - longFare);
  const onsiteLongFare = fareItinerary.legs.reduce(
    (sum, leg, i) => sum + (steps[i]?.dataset.onsite === "true" ? Number(leg.routePayment || 0) : 0),
    0,
  );
  const bookedFare = collectBookingLegs().reduce((sum, leg) => sum + leg.routePayment, 0);
  const toggle = document.querySelector(".fare-toggle");
  const includeLocal = toggle.querySelector("input").checked;
  line.textContent = describeFareTotal(bookedFare, localFare, onsiteLongFare, includeLocal);
  toggle.hidden = localFare + onsiteLongFare <= 0;
}

/**
 * 장거리 구간이 둘 이상이면 환승 여유(10·20·30분)를 고를 수 있게 한다. 바꾸면 기본 편을 다시 고른다
 */
function renderTransferOption() {
  if (document.querySelectorAll(".trip-leg .trip-select").length < 2) return;
  const wrapper = document.createElement("label");
  wrapper.classList.add("transfer-option");
  wrapper.textContent = "환승 여유 ";
  const select = document.createElement("select");
  TRANSFER_OPTIONS.forEach((minutes) => {
    const option = document.createElement("option");
    option.value = String(minutes);
    option.textContent = `${minutes}분`;
    select.appendChild(option);
  });
  select.value = String(getTransferMinutes());
  select.addEventListener("change", () => {
    setTransferMinutes(Number(select.value));
    // 경로는 sessionStorage 에서 다시 읽으므로 TMAP 을 다시 부르지 않는다
    window.location.reload();
  });
  wrapper.appendChild(select);
  document.querySelector(".summary-box")?.after(wrapper);
}

/**
 * 구간의 TAGO 편 목록을 불러와 고를 수 있게 한다 (조회는 API 가 준 그대로)
 * - 기본 선택: reference(검색 시각 또는 앞 구간 도착 뒤) 이후 첫 편
 * - 당일 출발이고 아직 출발하지 않은 편만 예매할 수 있다
 * - 사용자가 편을 바꾸면 뒤 구간이 이어지는지 확인한다 (repickFollowingTrips)
 * @returns {Promise<object|null>} chain 항목 { element, trips, select, bookableDate, setTrip, trip }
 */
async function loadTrips(element, kind, depId, arrId, reference, departAt) {
  const date = reference.slice(0, 8);
  let data;
  try {
    const response = await fetch("/api/trips", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ kind, depId, arrId, date }),
    });
    data = await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(data?.message || `HTTP ${response.status}`);
  } catch (error) {
    console.error("편 조회 실패:", error);
    element.dataset.tripError = "true";
    setTripMessage(element, "편 정보를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요");
    return null;
  }

  const trips = data.trips || [];
  if (trips.length === 0) {
    markOnsite(element, JSON.parse(element.dataset.leg));
    return null;
  }

  const select = document.createElement("select");
  select.classList.add("trip-select");
  trips.forEach((trip, i) => {
    const option = document.createElement("option");
    option.value = String(i);
    option.textContent = `${formatHm(trip.departAt)} → ${formatHm(trip.arriveAt)} ${trip.grade} ${trip.fare.toLocaleString()}원`;
    if (data.bookableDate && !trip.bookable) option.disabled = true;
    select.appendChild(option);
  });
  const first = pickTripIndex(trips, reference, data.bookableDate);
  select.value = String(first >= 0 ? first : 0);

  const fareText = document.createElement("p");
  fareText.classList.add("trip-fare");
  const apply = () => {
    const trip = trips[Number(select.value)];
    // 편을 조회한 조건도 함께 둔다: 담기·결제 때 서버가 이 조건으로 운임을 다시 확인한다
    element.dataset.trip = JSON.stringify({ ...trip, kind, depId, arrId });
    element.dataset.tripBookable = String(Boolean(trip.bookable));
    element.classList.toggle("selected", Boolean(trip.bookable));
    fareText.textContent = trip.bookable
      ? `선택한 편 ${formatHm(trip.departAt)} 출발 · ${trip.fare.toLocaleString()}원`
      : `${formatHm(trip.departAt)} 출발 · ${trip.fare.toLocaleString()}원 (조회 전용)`;
    refreshBookingFooter(departAt);
    refreshFareTotal();
  };
  select.addEventListener("change", () => {
    apply();
    const position = tripChain.findIndex((entry) => entry.element === element);
    if (position < 0) return;
    checkConnection(position);
    repickFollowingTrips(position);
    refreshFareTotal();
  });

  const container = element.querySelector(".step-trip");
  container.replaceChildren(select, fareText);
  apply();
  return {
    element,
    trips,
    select,
    bookableDate: Boolean(data.bookableDate),
    setTrip: (i) => {
      select.value = String(i);
      apply();
    },
    // 예매에서 뺀 구간(선택 해제)은 다음 구간의 기준에서 뺀다
    trip: () => (element.classList.contains("selected") ? trips[Number(select.value)] : null),
  };
}

function setTripMessage(stepElement, text) {
  const target = stepElement?.querySelector(".step-trip");
  if (target) target.textContent = text;
}

function setMatchText(stepElement, text) {
  const target = stepElement?.querySelector(".step-match");
  if (target) target.textContent = text;
}
