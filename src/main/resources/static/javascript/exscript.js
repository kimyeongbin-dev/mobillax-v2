import {
  getTmapRoute,
  formatRouteData,
  getTransferCount,
  getModeLabel,
  getPathSummary
} from '/javascript/service/TmapService.js';

let currentFocusType = 'from';
let selectedFrom = null;
let selectedTo = null;

window.selectSuggestion = function (name, type, x, y) {
  const input = document.getElementById(type + "Input");
  if (input) input.value = name;

  const selected = { name, x, y };
  if (type === "from") {
    selectedFrom = selected;
    localStorage.setItem("selectedFrom", JSON.stringify(selected));
  } else {
    selectedTo = selected;
    localStorage.setItem("selectedTo", JSON.stringify(selected));
  }

  const box = document.getElementById("suggestions");
  box.innerHTML = "";
  box.style.display = "none";
};

function updateSuggestHeader() {
  const header = document.querySelector(".suggest-header");
  if (header) header.textContent = currentFocusType === "from" ? "출발지 검색" : "도착지 검색";
}

function showSuggestionBox() {
  const box = document.getElementById("suggestions");
  if (box) box.style.display = "block";
}

function showSuggestions(value) {
  if (!value.trim()) {
    document.getElementById("suggestions").innerHTML = "";
    return;
  }

  const ps = new kakao.maps.services.Places();
  ps.keywordSearch(value, function (data, status) {
    if (status !== kakao.maps.services.Status.OK) {
      document.getElementById("suggestions").innerHTML = '<div class="item">검색 결과가 없습니다.</div>';
      return;
    }

    const html = data.slice(0, 5).map(place => `
      <div class="item" data-name="${place.place_name}" data-x="${place.x}" data-y="${place.y}">
        <i class='fas fa-map-marker-alt'></i>
        <span>${place.place_name}</span>
        <small style='margin-left:auto;'>${place.address_name}</small>
      </div>
    `).join('');

    const container = document.getElementById("suggestions");
    container.innerHTML = html;

    container.querySelectorAll(".item").forEach(item => {
      item.addEventListener("click", () => {
        const name = item.getAttribute("data-name");
        const x = item.getAttribute("data-x");
        const y = item.getAttribute("data-y");
        selectSuggestion(name, currentFocusType, x, y);
      });
    });
  });
}

function swapInputs() {
  const from = document.getElementById("fromInput");
  const to = document.getElementById("toInput");

  [from.value, to.value] = [to.value, from.value];
  [selectedFrom, selectedTo] = [selectedTo, selectedFrom];
  localStorage.setItem("selectedFrom", JSON.stringify(selectedFrom));
  localStorage.setItem("selectedTo", JSON.stringify(selectedTo));
}

function showRouteResults() {
  if (!selectedFrom || !selectedTo) {
    alert("출발지와 도착지를 모두 입력하세요.");
    return;
  }

  const fromX = selectedFrom.x;
  const fromY = selectedFrom.y;
  const toX = selectedTo.x;
  const toY = selectedTo.y;

  console.log("출발지 좌표:", selectedFrom);
  console.log("도착지 좌표:", selectedTo);

  getTmapRoute(fromX, fromY, toX, toY).then(data => {
    console.log("🔥 전체 Tmap API 응답 결과:", data);
    const list = document.getElementById("routeList");
    list.innerHTML = "";
    const itineraries = data?.metaData?.plan?.itineraries || [];

    itineraries.forEach((itinerary, i) => {
      const li = document.createElement("li");
      const formatted = formatRouteData(itinerary);
      const labels = {
        time: "최단시간",
        distance: "최단거리",
        transfer: "최소환승"
      };

      li.innerHTML = `
        <a class="route-card" href="/route?type=time">
          <div class="route-summary">
            <div class="route-type">${labels["time"]}</div>
            <div class="total-info">
              <span class="time">⏱ ${formatted.time}</span>
              <span class="price">💸 ${formatted.cost}원</span>
              <span class="transfer-info">🔁 환승 ${formatted.transfers}회</span>
            </div>
          </div>
          <div class="route-summary-line">
            ${formatted.summary}
          </div>
        </a>
      `;
      list.appendChild(li);
    });
  });
}

function alertComingSoon() {
  alert("준비 중인 기능입니다.");
}

function selectMode(mode) {
  document.querySelectorAll(".mode-item").forEach(item => item.classList.remove("active-mode"));
  const active = document.querySelector(`.mode-item[data-mode="${mode}"]`);
  if (active) active.classList.add("active-mode");
}

// ✅ 이벤트 바인딩
window.addEventListener("DOMContentLoaded", () => {
  const fromInput = document.getElementById("fromInput");
  const toInput = document.getElementById("toInput");
  const routeBtn = document.getElementById("findRouteBtn");
  console.log("🔍 routeBtn:", routeBtn);
  const switchBtn = document.getElementById("switchBtn");

  fromInput.addEventListener("focus", () => {
    currentFocusType = 'from';
    updateSuggestHeader();
    showSuggestionBox();
  });

  toInput.addEventListener("focus", () => {
    currentFocusType = 'to';
    updateSuggestHeader();
    showSuggestionBox();
  });

  fromInput.addEventListener("input", () => showSuggestions(fromInput.value));
  toInput.addEventListener("input", () => showSuggestions(toInput.value));

  if (routeBtn) routeBtn.addEventListener("click", showRouteResults);
  if (switchBtn) switchBtn.addEventListener("click", swapInputs);

  // 모드 항목 클릭 이벤트 등록
  document.querySelectorAll(".mode-item").forEach(item => {
    const mode = item.dataset.mode;
    item.addEventListener("click", () => {
      if (mode === "transit") selectMode(mode);
      else alertComingSoon();
    });
  });
});
