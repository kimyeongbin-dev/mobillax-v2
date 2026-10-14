export async function getTmapRoute(fromX, fromY, toX, toY) {
  const url = 'https://apis.openapi.sk.com/transit/routes';

  const headers = {
    'Content-Type': 'application/json',
    'Accept': 'application/json',
    //'appKey': '<TMAP_APPKEY>'
  };

  const body = {
    startX: fromX,
    startY: fromY,
    endX: toX,
    endY: toY,
    count: 10,
    lang: 0,
    format: 'json'
  };

  try {
    const response = await fetch(url, {
      method: 'POST',
      headers: headers,
      body: JSON.stringify(body)
    });

    const data = await response.json();
    console.log("Tmap API 응답:", data);

    if (!response.ok) {
      throw new Error(`HTTP 오류 상태: ${response.status}`);
    }
    console.log("🔥 전체 Tmap API 응답 결과:", JSON.stringify(data, null, 2)); //api 확인용
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

// Tmap API 응답을 요약 형태로 변환 (시간/요금/환승/요약)
export function formatRouteData(itinerary) {
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
export function getTransferCount(legs) {
  let count = 0;
  for (let i = 1; i < legs.length; i++) {
    if (legs[i].mode !== "WALK" && legs[i].mode !== legs[i - 1].mode) {
      count++;
    }
  }
  return count;
}

// 모드 + 노선명 요약 라벨 생성
export function getModeLabel(leg) {
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
export function getPathSummary(path) {
  if (!path.legs || path.legs.length === 0) return "경로 없음";
  const parts = path.legs.filter(leg => leg.mode !== "WALK").map(leg => getModeLabel(leg));
  return parts.join(" → ");
}