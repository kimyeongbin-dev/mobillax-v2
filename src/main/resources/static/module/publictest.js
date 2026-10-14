// 경로 렌더링 결과 보관용 배열
let resultDrawArr = [];

/**
 * 목업 API를 통해 대중교통 경로를 불러와 화면에 표시합니다.
 */
async function publicTest() {
  try {
    const response = await fetch('https://3173cb8e-d014-4dcb-8da9-2e53ad672e15.mock.pstmn.io', {
      method: "GET",
    });

    const text = await response.text();
    const data = JSON.parse(text);

    const type = getQueryParam("type"); // URL에서 전달된 비교 기준
    const bestIndex = getBestItineraryIndex(type || "time", data); // 최적 경로 인덱스 (bestIndex)

    const itinerary = data.metaData.plan.itineraries[bestIndex];
    const legs = itinerary.legs;

    const totalTime = formatTime(itinerary.totalTime); // 총 소요 시간 (초 → 분/시간)
    const totalFare = itinerary.fare.regular.totalFare.toLocaleString() + "원"; // 총 요금
    const transferCount = itinerary.transferCount + "회"; // 환승 횟수

    // 요약 정보 표시
    document.querySelector('.summary-item:nth-child(1) strong').textContent = totalTime;
    document.querySelector('.summary-item:nth-child(2) strong').textContent = totalFare;
    document.querySelector('.summary-item:nth-child(3) strong').textContent = transferCount;

    renderSummary(data);       // 요약 박스 렌더링
    renderSteps(itinerary);    // 상세 경로 렌더링

    const routeBounds = new Tmapv2.LatLngBounds();

    for (const leg of legs) {
      const startCoord = new Tmapv2.LatLng(Number(leg.start.lat), Number(leg.start.lon));
      const endCoord = new Tmapv2.LatLng(Number(leg.end.lat), Number(leg.end.lon));
      routeBounds.extend(startCoord);
      routeBounds.extend(endCoord);

      if (leg.mode === "WALK" && leg.steps) {
        for (const step of leg.steps) {
          const walkPoints = parseLineString(step.linestring);
          drawLine(walkPoints, "#888888"); // 도보는 회색
        }
      } else if (leg.passShape?.linestring) {
        const routeColor = `#${leg.routeColor || "0068B7"}`;
        const routePoints = parseLineString(leg.passShape.linestring);
        drawLine(routePoints, routeColor); // 대중교통 노선
      }
    }

    map.panToBounds(routeBounds);

  } catch (err) {
    console.error("목업 데이터를 불러오는 중 오류:", err);
  }
}

/**
 * 지도에 경로 선을 그립니다.
 * @param {Tmapv2.LatLng[]} latlngs - 선을 구성할 좌표 배열
 * @param {string} color - 선 색상 (hex 코드)
 */
function drawLine(latlngs, color) {
  const polyline = new Tmapv2.Polyline({
    path: latlngs,
    strokeColor: color,
    strokeWeight: 6,
    map: map
  });
  resultDrawArr.push(polyline);
}

/**
 * 지도에서 그려진 모든 경로를 제거합니다.
 */
function clearRoutes() {
  resultDrawArr.forEach(polyline => polyline.setMap(null));
  resultDrawArr = [];
}

/**
 * LineString을 좌표 배열로 변환합니다.
 * @param {string} line - "lon,lat lon,lat ..." 형식의 문자열
 * @returns {Tmapv2.LatLng[]} - 변환된 좌표 배열
 */
function parseLineString(line) {
  return line.trim().split(" ").map(pair => {
    const [lon, lat] = pair.split(",").map(Number);
    return new Tmapv2.LatLng(lat, lon);
  });
}

/**
 * 최적 경로의 인덱스를 계산합니다.
 * @param {"distance"|"time"|"transfer"} type - 비교 기준
 * @param {object} data - API 응답 데이터
 * @returns {number} - 최적 경로의 인덱스
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
 * 쿼리 문자열에서 특정 파라미터 값을 추출합니다.
 * @param {string} key - 파라미터 이름
 * @returns {string|null} - 파라미터 값
 */
function getQueryParam(key) {
  return new URLSearchParams(window.location.search).get(key);
}

/**
 * 초 단위를 "시간 분" 형식 문자열로 변환합니다.
 * @param {number} sec - 초 단위 시간
 * @returns {string} - 변환된 시간 문자열
 */
function formatTime(sec) {
  if (Math.floor((sec / 60) / 60)) {
    return `${Math.floor((sec / 60) / 60)}시간 ${Math.round((sec / 60) % 60)}분`;
  } else {
    return `${Math.round(sec / 60)}분`;
  }
}
