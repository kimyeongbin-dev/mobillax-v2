/**
 * payment.js
 *
 * @description 경로 선택 시 즉시 결제 및 장바구니 그룹 결제를 처리하는 스크립트입니다.
 * @author
 * @lastModified 2025-06-09
 */
document.addEventListener("DOMContentLoaded", () => {
  // ✅ 바로 결제 버튼 (상세 경로 페이지용)
  document.querySelector(".btn-direct-pay")?.addEventListener("click", handleDirectPayment);

  // ✅ 장바구니 전체 결제 버튼
  document.querySelector(".btn-cart-pay")?.addEventListener("click", goToPayment);

  // ✅ 장바구니 그룹 결제 버튼 (동적 이벤트 위임 방식)
  document.querySelector(".cart-list")?.addEventListener("click", async (event) => {
    const groupId = event.target.dataset.groupid;
    if (event.target.classList.contains("group-pay-button") && groupId) {
      const result = await processGroupPayment(groupId);
      if (!result) alert("결제를 중단했습니다.");
    }
  });
});

/**
 * 선택된 경로를 즉시 결제하는 함수
 *
 * @async
 */
async function handleDirectPayment() {
  const selectedElements = [...document.querySelectorAll(".route-step.selected")];

  if (selectedElements.length === 0) {
    showNoRoutePopup();
    return;
  }

  // 사용자가 선택한 경로 중 유효한 구간(leg)을 필터링
  const selectedLegs = [...document.querySelectorAll(".route-step.selected")]
    .map(el => {
      try {
        return JSON.parse(el.dataset.leg);
      } catch {
        return null;
      }
    })
    .filter(leg =>
      leg &&
      leg.routePayment > 0 &&
      leg.start?.name && leg.end?.name
    )
    .map(leg => ({
      mode: leg.mode,
      route: leg.route,
      routeId: leg.routeId,
      routePayment: leg.routePayment,
      startName: leg.start.name,
      endName: leg.end.name
    }));

  if (selectedLegs.length === 0) {
    alert("요금이 있는 경로만 결제할 수 있습니다.");
    return;
  }

  try {
    const response = await fetch("/payment/direct-sdk-ready", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "Accept": "application/json"
      },
      body: JSON.stringify({ selectedLegs })
    });

    if (!response.ok) {
      let errorMessage = "결제 준비 실패";
      try {
        const errorBody = await response.json();
        errorMessage = errorBody?.error || errorMessage;
      } catch (_) {
        // JSON 파싱 실패 시 기본 메시지 유지
      }
      alert(errorMessage);
      if (response.status === 401) location.href = "/login";
      return;
    }

    const paymentDataMap = await response.json();

    for (const [transportType, paymentData] of Object.entries(paymentDataMap)) {
      const { storeId, channelKey, paymentId, orderName, amount, groupId } = paymentData;

      const paymentResponse = await PortOne.requestPayment({
        storeId,
        channelKey,
        paymentId,
        orderName,
        totalAmount: amount, // 총 결제 금액(amount)
        currency: "CURRENCY_KRW",
        payMethod: "CARD"
      });

      if (paymentResponse.code === "USER_CANCEL") {
        alert("결제가 취소되었습니다.");
        break;
      }

      if (paymentResponse.code !== undefined) {
        alert(`${transportType} 결제가 실패했습니다: ${paymentResponse.message}`);
        alert("일부 결제가 실패했습니다. 마이페이지에서 확인해주세요.");
        break;
      }

      await fetch("/payment/complete", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          paymentId,
          groupId,
          amount,
          transportType
        })
      });

      alert(`${transportType} 결제가 완료되었습니다.`);
    }

  } catch (error) {
    alert("결제 중 오류가 발생했습니다: " + error.message);
  }
}

/**
 * 그룹 ID로 장바구니 그룹을 결제하는 함수
 *
 * @param {string} groupId - 결제할 그룹 ID
 * @returns {Promise<boolean>} 결제 성공 여부 반환
 */
async function processGroupPayment(groupId) {
  try {
    const response = await fetch(`/payment/sdk-ready/${groupId}`, { method: "POST" });
    const paymentDataMap = await response.json();

    for (const [transportType, paymentData] of Object.entries(paymentDataMap)) {
      const { storeId, channelKey, paymentId, orderName, amount } = paymentData;

      const paymentResponse = await PortOne.requestPayment({
        storeId,
        channelKey,
        paymentId,
        orderName,
        totalAmount: amount, // 총 결제 금액(amount)
        currency: "CURRENCY_KRW",
        payMethod: "CARD"
      });

      if (paymentResponse.code === "USER_CANCEL") {
        alert("사용자가 결제를 취소했습니다.");
        return false;
      }

      if (paymentResponse.code !== undefined) {
        await fetch("/payment/fail", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ paymentId, groupId, amount, transportType })
        });
        alert(`${transportType} 결제 실패: ${paymentResponse.message}`);
        return false;
      }

      await fetch("/payment/complete", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ paymentId, groupId, amount, transportType })
      });

      alert(`${transportType} 결제가 완료되었습니다.`);
    }

    await loadCart(); // 장바구니 갱신
    return true;

  } catch (err) {
    alert("결제 처리 중 오류: " + err.message);
    return false;
  }
}

/**
 * 장바구니의 모든 그룹에 대해 결제를 순차적으로 수행하는 함수
 *
 * @async
 */
async function goToPayment() {
  try {
    const response = await fetch("/api/cart/list");
    if (!response.ok) throw new Error("장바구니 불러오기 실패");

    const groupedCartItems = await response.json();
    const groupIds = Object.keys(groupedCartItems);

    if (groupIds.length === 0) {
      alert("결제할 경로가 없습니다.");
      return;
    }

    for (const groupId of groupIds) {
      const result = await processGroupPayment(groupId);
      if (!result) {
        alert("결제를 중단했습니다.");
        return;
      }
    }

  } catch (err) {
    alert("전체 결제 중 오류 발생: " + err.message);
  }
}
