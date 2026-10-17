/* global escapeHtml, getTransportLabel, collectBookingLegs, nowStamp -- transport.js */
/**
 * cart.js
 *
 * @description 장바구니에 경로 추가, 목록 조회, 그룹 삭제, 팝업 제어를 담당하는 스크립트입니다.
 * @lastModified 2025-06-09
 */

document.addEventListener("DOMContentLoaded", async () => {
  // 상세 화면(route.html)도 담기 기능 때문에 이 파일을 불러오므로, 장바구니 목록은 장바구니 화면에서만 그린다
  if (!document.querySelector(".cart-list")) return;
  await loadCartList();

  // 장바구니 전체 비우기 버튼
  document.getElementById("clearCartBtn")?.addEventListener("click", async () => {
    const confirmClear = confirm("정말로 장바구니를 비우시겠습니까?");
    if (confirmClear) {
      await fetch("/api/cart/clear", { method: "DELETE" });
      await loadCartList();
    }
  });

  // 장바구니 그룹별 삭제 처리
  document.querySelector(".cart-list")?.addEventListener("click", async (event) => {
    const groupId = event.target.dataset.groupid;
    if (!groupId) return;

    if (event.target.classList.contains("group-delete-button")) {
      const confirmDelete = confirm("이 경로 묶음을 삭제하시겠습니까?");
      if (confirmDelete) {
        try {
          await fetch(`/api/cart/group/${groupId}`, { method: "DELETE" });
          await loadCartList();
        } catch (err) {
          alert("삭제 실패: " + err.message);
        }
      }
    }
  });
});

/**
 * 장바구니 목록을 불러와 화면에 렌더링합니다.
 */
async function loadCartList() {
  const container = document.querySelector(".cart-list");
  const actionButtons = document.querySelector(".cart-actions");
  container.innerHTML = "";

  try {
    const response = await fetch("/api/cart/list");
    if (!response.ok) throw new Error("서버 오류");

    const groupedCart = await response.json();
    const groupIds = Object.keys(groupedCart);

    if (groupIds.length === 0) {
      container.innerHTML = `
        <div class="cart-empty">
          <p>장바구니가 비어 있습니다</p>
          <button class="find-route-button" onclick="location.href='/home'">경로 찾기</button>
        </div>`;
      actionButtons.style.display = "none";
      return;
    }

    actionButtons.style.display = "flex";

    const now = nowStamp();
    groupIds.forEach((groupId) => {
      const items = groupedCart[groupId];
      let totalFare = 0;
      // 출발 일시가 없거나(편을 고르기 전 방식의 예전 항목) 이미 지난 편이 있으면 결제할 수 없다
      const departed = items.some((item) => !/^\d{12}$/.test(item.departureTime || "") || item.departureTime <= now);

      const groupElement = document.createElement("div");
      groupElement.className = "cart-group";
      groupElement.innerHTML = `
        <h3>
          경로 묶음
          <div>
            <button class="group-pay-button" data-groupid="${escapeHtml(groupId)}" ${departed ? 'disabled title="출발 시각이 지났거나 알 수 없는 편이 있습니다"' : ""}>결제하기</button>
            <button class="group-delete-button" data-groupid="${escapeHtml(groupId)}">삭제</button>
          </div>
        </h3>
      `;

      items.forEach((item) => {
        const fare = Number(item.routePayment) || 0;
        totalFare += fare; // 요금(routePayment)
        const dep = /^\d{12}$/.test(item.departureTime || "") ? item.departureTime : "";
        const itemElement = document.createElement("div");
        itemElement.className = "cart-item";
        // 서버 응답이라도 문자열은 이스케이프해서 넣는다 (S10)
        itemElement.innerHTML = `
          <div><strong>${escapeHtml(item.startName)} → ${escapeHtml(item.endName)}</strong></div>
          <div>${escapeHtml(getTransportLabel(item))} / ${escapeHtml(item.route)}</div>
          <div>(요금: ￦${fare.toLocaleString()}원)${dep ? ` · ${Number(dep.slice(4, 6))}월 ${Number(dep.slice(6, 8))}일 ${dep.slice(8, 10)}:${dep.slice(10, 12)} 출발` : ""}</div>
        `;
        groupElement.appendChild(itemElement);
      });

      if (departed) {
        const warn = document.createElement("p");
        warn.className = "departed-warning";
        warn.textContent =
          "출발 시각이 지났거나 알 수 없는 편이 있어 결제할 수 없습니다. 묶음을 삭제하고 편을 다시 골라 주세요.";
        groupElement.appendChild(warn);
      }

      const totalElement = document.createElement("div");
      totalElement.className = "total-fare";
      totalElement.innerHTML = `<strong>합계: ￦${totalFare.toLocaleString()}원</strong>`;
      groupElement.appendChild(totalElement);

      container.appendChild(groupElement);
    });
  } catch (error) {
    console.error("장바구니 불러오기 실패", error);
    container.innerHTML = "<p>장바구니 정보를 불러올 수 없습니다.</p>";
    actionButtons.style.display = "none";
  }
}

/**
 * 장바구니 담기 버튼 이벤트 등록
 */
document.addEventListener("DOMContentLoaded", () => {
  const cartButton = document.querySelector(".btn-cart");
  cartButton?.addEventListener("click", handleAddToCart);
});

/**
 * 사용자가 선택한 경로를 장바구니에 담는 함수
 */
async function handleAddToCart() {
  const selectedElements = [...document.querySelectorAll(".route-step.selected")];
  if (selectedElements.length === 0) {
    showNoRoutePopup();
    return;
  }

  // 요금이 있는 경로만 필터링하여 변환
  // 편을 고른 장거리 구간만, 고른 편의 운임으로 (transport.js)
  const selectedLegs = collectBookingLegs();

  if (selectedLegs.length === 0) {
    alert("예매할 편을 고른 구간이 없습니다.");
    return;
  }

  try {
    const response = await fetch("/api/cart/add", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ selectedLegs }),
    });

    if (!response.ok) {
      const data = await response.json().catch(() => ({}));
      throw new Error(data?.error || "서버 응답 실패");
    }

    const totalFare = await response.json(); // 총 결제 금액(totalFare)
    console.log("총 결제 금액:", totalFare);
    showCartPopup();
  } catch (error) {
    alert("장바구니 담기 실패: " + error.message);
  }
}

/**
 * 장바구니 팝업 표시
 */
function showCartPopup() {
  document.getElementById("cartPopup")?.classList.remove("hidden");
}

/**
 * 장바구니 팝업 닫기
 */
function closeCartPopup() {
  document.getElementById("cartPopup")?.classList.add("hidden");
}

/**
 * 선택 안됨 알림 팝업 표시
 */
function showNoRoutePopup() {
  document.getElementById("noRoutePopup")?.classList.remove("hidden");
}

/**
 * 선택 안됨 알림 팝업 닫기
 */
function closeNoRoutePopup() {
  document.getElementById("noRoutePopup")?.classList.add("hidden");
}

/**
 * 장바구니로 이동
 */
function goToCart() {
  location.href = "/cart";
}
