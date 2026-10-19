/* global html, raw, collectBookingLegs -- safe-html.js, transport.js */
/* exported summarizeSelection, selectedGroupIds, closeCartPopup, closeNoRoutePopup, goToCart */
/**
 * cart.js
 *
 * @description 장바구니 화면(묶음 목록·선택·삭제)과 상세 화면의 장바구니 담기·팝업을 담당합니다.
 * - 목록의 표시 값과 결제 가능 여부는 서버가 만든다 (GET /api/cart/list → CartGroupView). 화면은 그리기만 한다
 * - 결제는 payment.js 가 고른 묶음(selectedGroupIds)으로 진행한다
 */

/** 마지막으로 불러온 장바구니 묶음 (서버 응답) */
let cartGroups = [];

document.addEventListener("DOMContentLoaded", async () => {
  // 상세 화면(route.html)도 담기 기능 때문에 이 파일을 불러오므로, 장바구니 목록은 장바구니 화면에서만 그린다
  const list = document.querySelector(".cart-list");
  if (!list) return;
  await loadCartList();

  document.getElementById("clearCartBtn")?.addEventListener("click", async () => {
    if (!confirm("장바구니를 모두 비우시겠습니까?")) return;
    await fetch("/api/cart/clear", { method: "DELETE" });
    await loadCartList();
  });

  document.getElementById("selectAll")?.addEventListener("change", (event) => {
    list.querySelectorAll(".group-select:not(:disabled)").forEach((box) => (box.checked = event.target.checked));
    updatePaybar();
  });

  list.addEventListener("change", (event) => {
    if (event.target.classList.contains("group-select")) updatePaybar();
  });

  list.addEventListener("click", async (event) => {
    const button = event.target.closest(".group-delete-button");
    if (!button) return;
    if (!confirm("이 경로 묶음을 장바구니에서 삭제하시겠습니까?")) return;
    try {
      await fetch(`/api/cart/group/${encodeURIComponent(button.dataset.groupid)}`, { method: "DELETE" });
      await loadCartList();
    } catch (err) {
      alert("삭제 실패: " + err.message);
    }
  });
});

/**
 * 고른 묶음의 개수·합계·결제창 수 (결제창은 묶음마다 교통수단 수만큼 열린다)
 * @param {Array} groups - 서버 응답 CartGroupView 목록
 * @param {Array<string>} selectedIds - 고른 묶음 번호
 * @returns {{count: number, total: number, windows: number}}
 */
function summarizeSelection(groups, selectedIds) {
  const chosen = groups.filter((g) => g.payable && selectedIds.includes(g.groupId));
  return {
    count: chosen.length,
    total: chosen.reduce((sum, g) => sum + g.totalFare, 0),
    windows: chosen.reduce((sum, g) => sum + g.payments.length, 0),
  };
}

/** 체크한 묶음 번호 (결제할 수 있는 묶음만) */
function selectedGroupIds() {
  return [...document.querySelectorAll(".group-select:checked:not(:disabled)")].map((box) => box.value);
}

/** 아래 결제 막대: 고른 개수·합계·결제창 수, 고른 것이 없으면 버튼을 막는다 */
function updatePaybar() {
  const { count, total, windows } = summarizeSelection(cartGroups, selectedGroupIds());
  document.getElementById("selectedCount").textContent = count ? `${count}개 묶음 선택` : "선택한 묶음 없음";
  document.getElementById("selectedTotal").textContent = `${total.toLocaleString()}원`;
  document.getElementById("selectedWindows").textContent = count ? `결제창 ${windows}번` : "";
  document.querySelector(".btn-cart-pay").disabled = count === 0;

  const selectable = document.querySelectorAll(".group-select:not(:disabled)");
  const selectAll = document.getElementById("selectAll");
  selectAll.disabled = selectable.length === 0;
  selectAll.checked = selectable.length > 0 && count === selectable.length;
}

/**
 * 장바구니 목록을 불러와 그린다. 결제할 수 있는 묶음은 처음부터 선택해 둔다
 */
async function loadCartList() {
  const container = document.querySelector(".cart-list");
  const toolbar = document.querySelector(".cart-toolbar");
  const paybar = document.querySelector(".cart-paybar");
  // 다시 불러와도(삭제·결제 뒤) 사용자가 고른 것은 그대로 두고, 처음 보는 묶음만 기본 선택
  const knownIds = new Set(cartGroups.map((g) => g.groupId));
  const checkedIds = new Set(selectedGroupIds());
  container.replaceChildren();

  try {
    const response = await fetch("/api/cart/list");
    if (!response.ok) throw new Error("서버 오류");
    cartGroups = await response.json();
  } catch (error) {
    console.error("장바구니 불러오기 실패", error);
    cartGroups = [];
    container.textContent = "장바구니 정보를 불러올 수 없습니다.";
    toolbar.hidden = paybar.hidden = true;
    return;
  }
  updateCartBadge(cartGroups.length);

  if (cartGroups.length === 0) {
    container.innerHTML = html`
      <div class="cart-empty">
        <p>장바구니가 비어 있습니다</p>
        <a class="find-route-button" href="/home">경로 찾기</a>
      </div>
    `;
    toolbar.hidden = paybar.hidden = true;
    return;
  }

  toolbar.hidden = paybar.hidden = false;
  cartGroups.forEach((group) => {
    const checked = group.payable && (!knownIds.has(group.groupId) || checkedIds.has(group.groupId));
    container.appendChild(renderGroup(group, checked));
  });
  updatePaybar();
}

/**
 * 상단 메뉴의 장바구니 묶음 수 (화면을 그릴 때 서버가 넣은 값을, 삭제·결제 뒤 맞춘다)
 * @param {number} count
 */
function updateCartBadge(count) {
  const link = document.querySelector('.site-nav a[href="/cart"]');
  if (!link) return;
  let badge = link.querySelector(".nav-badge");
  if (count === 0) {
    badge?.remove();
    return;
  }
  if (!badge) {
    badge = document.createElement("span");
    badge.className = "nav-badge";
    link.appendChild(badge);
  }
  badge.textContent = String(count);
}

/**
 * 묶음 카드 하나 (값은 모두 html`` 이 이스케이프한다, 보안 규칙 S10)
 * @param {object} group - CartGroupView
 * @param {boolean} checked - 결제할 묶음으로 골라 둘지
 * @returns {HTMLElement}
 */
function renderGroup(group, checked) {
  const card = document.createElement("article");
  card.className = group.payable ? "cart-group" : "cart-group blocked";
  card.innerHTML = html`
    <div class="group-head">
      <label class="group-check">
        <input
          type="checkbox"
          class="group-select"
          value="${group.groupId}"
          ${group.payable ? (checked ? raw("checked") : "") : raw("disabled")}
        />
        <span class="group-title">${group.title}</span>
      </label>
      <button type="button" class="group-delete-button text-button" data-groupid="${group.groupId}">삭제</button>
    </div>
    ${group.departure ? html`<p class="group-departure">${group.departure} 출발</p>` : ""}
    <ol class="group-legs">
      ${group.items.map(
        (item) => html`
          <li>
            <span class="leg-time">${item.departure || "출발 시각 모름"}</span>
            <span class="leg-body">
              <strong>${item.startName} → ${item.endName}</strong>
              <span class="leg-route">${item.route}</span>
            </span>
            <span class="leg-fare">${item.fare.toLocaleString()}원</span>
          </li>
        `,
      )}
    </ol>
    <div class="group-foot">
      <span class="group-payments">
        결제창 ${group.payments.length}번:
        ${group.payments.map((p, i) => html`${i ? " · " : ""}${p.transport} ${p.amount.toLocaleString()}원`)}
      </span>
      <strong class="total-fare">합계 ${group.totalFare.toLocaleString()}원</strong>
    </div>
    ${group.payable ? "" : html`<p class="departed-warning">${group.blockReason}</p>`}
  `;
  return card;
}

/**
 * 장바구니 담기 버튼 이벤트 등록 (상세 화면)
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
