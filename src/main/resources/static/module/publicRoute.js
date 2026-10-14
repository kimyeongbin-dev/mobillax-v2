/**
 * 대중교통 경로를 Tmap API를 통해 가져와 지도에 렌더링합니다.
 */
async function publicRoute() {
  const headers = {
    accept: 'application/json',
    "Content-Type": "application/json",
    "appKey": window.TMAP_APPKEY
  };

  const { s_lat, s_lon, e_lat, e_lon } = JSON.parse(localStorage.getItem('info'));
  const params = {
    startX: Number(s_lon),
    startY: Number(s_lat),
    endX: Number(e_lon),
    endY: Number(e_lat),
    lang: 0,
    format: 'json',
    count: 10,
    searchDttm: '202505221000'
  };

  try {
    const response = await fetch("https://apis.openapi.sk.com/transit/routes/", {
      method: "POST",
      headers: headers,
      body: JSON.stringify(params)
    });

    const data = await response.json();
    const type = getQueryParam("type"); // 예: "distance", "time", "transfer"
    const index = getBestItineraryIndex(type || "time", data); // 최적 경로 인덱스 (index)

    const itinerary = data.metaData.plan.itineraries[index];
    const legs = itinerary.legs;

    const totalTime = timeFormat(itinerary.totalTime);
    const totalFare = itinerary.fare.regular.totalFare.toLocaleString() + "원";
    const totalTransfer = itinerary.transferCount + "회";

    // 요약 박스에 데이터 표시
    document.querySelector('.summary-item:nth-child(1) strong').textContent = totalTime;
    document.querySelector('.summary-item:nth-child(2) strong').textContent = totalFare;
    document.querySelector('.summary-item:nth-child(3) strong').textContent = totalTransfer;

    renderSummary(data);      // 요약 박스 표시
    renderSteps(itinerary);   // 세부 구간 리스트 렌더링

    // 지도에 경로 렌더링
    const routeBounds = new Tmapv2.LatLngBounds();
    for (const leg of legs) {
      const start = new Tmapv2.LatLng(Number(leg.start.lat), Number(leg.start.lon));
      const end = new Tmapv2.LatLng(Number(leg.end.lat), Number(leg.end.lon));
      routeBounds.extend(start);
      routeBounds.extend(end);

      if (leg.mode === "WALK" && leg.steps) {
        for (const step of leg.steps) {
          const points = parseLineString(step.linestring);
          drawLine(points, "#888888"); // 도보 경로
        }
      } else if (leg.passShape?.linestring) {
        const color = `#${leg.routeColor || "0068B7"}`;
        const points = parseLineString(leg.passShape.linestring);
        drawLine(points, color); // 대중교통 경로
      }
    }

    map.panToBounds(routeBounds);
  } catch (error) {
    console.error("API 호출 중 오류 발생:", error);
  }
}

/**
 * 선의 좌표 배열을 지도에 그립니다.
 * @param {Tmapv2.LatLng[]} latlngs - 경로 좌표 배열
 * @param {string} color - 선 색상
 */
function drawLine(latlngs, color) {
  const polyline = new Tmapv2.Polyline({
    path: latlngs,
    strokeColor: color,
    strokeWeight: 6,
    map: map
  });
  resultdrawArr.push(polyline);
}

/**
 * 지도에서 기존 경로들을 모두 제거합니다.
 */
function clearRoutes() {
  resultdrawArr.forEach(polyline => polyline.setMap(null));
  resultdrawArr = [];
}

/**
 * linestring 형식의 문자열을 Tmap 좌표 객체 배열로 변환합니다.
 * @param {string} line - linestring 형식 문자열
 * @returns {Tmapv2.LatLng[]}
 */
function parseLineString(line) {
  return line.trim().split(" ").map(pair => {
    const [lon, lat] = pair.split(",").map(Number);
    return new Tmapv2.LatLng(lat, lon);
  });
}

/**
 * 가장 최적인 경로의 인덱스를 반환합니다.
 * @param {string} type - 비교 기준 ("distance", "time", "transfer")
 * @param {object} data - Tmap API 응답 데이터
 * @returns {number}
 */
function getBestItineraryIndex(type, data) {
  const itineraries = data.metaData.plan.itineraries;
  let bestIndex = 0;

  if (type === "distance") {
    let minDistance = Infinity;
    itineraries.forEach((it, i) => {
      if (it.totalDistance < minDistance) {
        minDistance = it.totalDistance;
        bestIndex = i;
      }
    });
  } else if (type === "time") {
    let minTime = Infinity;
    itineraries.forEach((it, i) => {
      if (it.totalTime < minTime) {
        minTime = it.totalTime;
        bestIndex = i;
      }
    });
  } else if (type === "transfer") {
    let minTransfer = Infinity;
    itineraries.forEach((it, i) => {
      if (it.transferCount < minTransfer) {
        minTransfer = it.transferCount;
        bestIndex = i;
      }
    });
  }

  return bestIndex;
}

/**
 * URL 쿼리 문자열에서 특정 파라미터 값을 가져옵니다.
 * @param {string} key - 파라미터 이름
 * @returns {string|null}
 */
function getQueryParam(key) {
  return new URLSearchParams(window.location.search).get(key);
}

/**
 * 초 단위 시간을 "시간 분" 형식으로 변환합니다.
 * @param {number} seconds - 초 단위 시간
 * @returns {string}
 */
function timeFormat(seconds) {
  const minutes = seconds / 60;
  if (Math.floor(minutes / 60)) {
    return `${Math.floor(minutes / 60)}시간 ${Math.round(minutes % 60)}분`;
  } else {
    return `${Math.round(minutes)}분`;
  }
}
