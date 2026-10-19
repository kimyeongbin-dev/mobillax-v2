/* global html -- safe-html.js */
/* exported pickNearbyPlaces */
/**
 * 주변정보 화면
 * - 기준 장소: 홈에서 고른 출발·도착지(sessionStorage nearbyPlaces), 없으면 마지막 검색의 출발·도착지
 * - 장소 검색은 서버(GET /api/nearby)가 카카오 로컬로 한다. 키는 서버에만 있고, 좌표·결과는 어디에도 저장하지 않는다
 * - 같은 기준·분류를 다시 고르면 이번 화면에서 받은 결과를 다시 쓴다 (메모리에만, 화면을 떠나면 사라짐)
 */

/**
 * 기준 장소 후보 {from, to}. 이름과 좌표가 모두 있는 것만
 * @param {object|null} fromHome - 홈에서 넘긴 {from, to}
 * @param {object|null} lastSearch - 마지막 검색 {from, to}
 * @returns {{from: object|null, to: object|null}}
 */
function pickNearbyPlaces(fromHome, lastSearch) {
  const valid = (p) => (p && p.name && Number.isFinite(Number(p.x)) && Number.isFinite(Number(p.y)) ? p : null);
  const source = valid(fromHome?.from) || valid(fromHome?.to) ? fromHome : lastSearch;
  return { from: valid(source?.from), to: valid(source?.to) };
}

function readJson(storage, key) {
  try {
    return JSON.parse(storage.getItem(key) || "null");
  } catch {
    return null;
  }
}

document.addEventListener("DOMContentLoaded", () => {
  const places = pickNearbyPlaces(readJson(sessionStorage, "nearbyPlaces"), {
    from: readJson(localStorage, "selectedFrom"),
    to: readJson(localStorage, "selectedTo"),
  });
  if (!places.from && !places.to) {
    document.getElementById("nearbyEmpty").hidden = false;
    return;
  }

  const targetTabs = document.getElementById("nearbyTarget");
  const categoryTabs = document.getElementById("nearbyCategory");
  const results = new Map();
  let target = places.to ? "to" : "from";
  let category = categoryTabs.querySelector("button")?.dataset.category;

  const press = (group, attr, value) =>
    group.querySelectorAll("button").forEach((b) => b.setAttribute("aria-pressed", String(b.dataset[attr] === value)));

  const refresh = async () => {
    press(targetTabs, "target", target);
    press(categoryTabs, "category", category);
    const place = places[target];
    const name = document.getElementById("nearbyTargetName");
    name.textContent = `${place.name} 주변`;
    name.hidden = false;

    const selected = () => `${target}|${category}`;
    const key = selected();
    if (!results.has(key)) {
      setStatus("불러오는 중입니다.");
      let list;
      try {
        list = await fetchNearby(category, place);
      } catch (error) {
        if (key !== selected()) return;
        renderPlaces([]);
        setStatus(error.message);
        return;
      }
      results.set(key, list);
      // 기다리는 동안 다른 기준·분류를 골랐으면, 늦게 온 응답으로 화면을 덮지 않는다
      if (key !== selected()) return;
    }
    const list = results.get(key);
    renderPlaces(list);
    setStatus(list.length ? "" : "1km 안에 이 분류의 장소가 없습니다.");
  };

  // 두 곳을 모두 골랐을 때만 기준 장소를 바꿀 수 있다
  targetTabs.hidden = !(places.from && places.to);
  categoryTabs.hidden = false;
  targetTabs.addEventListener("click", (event) => {
    const button = event.target.closest("button[data-target]");
    if (button && places[button.dataset.target]) {
      target = button.dataset.target;
      refresh();
    }
  });
  categoryTabs.addEventListener("click", (event) => {
    const button = event.target.closest("button[data-category]");
    if (button) {
      category = button.dataset.category;
      refresh();
    }
  });
  refresh();
});

/**
 * 서버에 주변 장소를 묻는다 (좌표는 서버가 국내 범위인지 확인한다)
 * @returns {Promise<Array>} NearbyPlaceView 목록
 */
async function fetchNearby(category, place) {
  const query = new URLSearchParams({ category, lat: String(place.y), lon: String(place.x) });
  const response = await fetch(`/api/nearby?${query}`);
  const data = await response.json().catch(() => ({}));
  if (!response.ok) {
    throw new Error(data?.error || "주변정보를 불러올 수 없습니다.");
  }
  return data;
}

function setStatus(message) {
  document.getElementById("nearbyStatus").textContent = message;
}

/**
 * 장소 목록 (값은 html`` 이 이스케이프한다, 보안 규칙 S10. 링크는 서버가 만든 카카오맵 주소만)
 * @param {Array} places - NearbyPlaceView 목록
 */
function renderPlaces(places) {
  document.getElementById("nearbyList").innerHTML = html`${places.map(
    (p) => html`
      <li class="nearby-row">
        <span class="nearby-body">
          <strong>${p.name}</strong>
          <span class="nearby-meta">${p.category}${p.phone ? html` · ${p.phone}` : ""}</span>
          <span class="nearby-address">${p.address}</span>
        </span>
        <span class="nearby-side">
          <span class="nearby-distance">${p.distance}</span>
          ${
            p.placeUrl
              ? html`<a href="${p.placeUrl}" target="_blank" rel="noopener noreferrer" title="카카오맵에서 보기 (새 창)"
                  >지도</a
                >`
              : ""
          }
        </span>
      </li>
    `,
  )}`;
}
