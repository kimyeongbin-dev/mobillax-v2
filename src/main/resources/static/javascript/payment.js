/* global collectBookingLegs, loadCartList, selectedGroupIds, showNoRoutePopup -- transport.js, cart.js */
/**
 * payment.js
 *
 * @description 상세 화면의 바로 결제와 장바구니에서 고른 묶음 결제를 처리하는 스크립트입니다.
 * @author
 * @lastModified 2025-06-09
 */
document.addEventListener("DOMContentLoaded", () => {
  // 바로 결제 버튼 (상세 경로 화면)
  document.querySelector(".btn-direct-pay")?.addEventListener("click", handleDirectPayment);

  // 장바구니에서 고른 묶음 결제 (v2.6: 묶음별 결제·전체 결제 버튼을 하나로)
  document.querySelector(".btn-cart-pay")?.addEventListener("click", paySelectedGroups);
});

/**
 * 사용자가 결제창을 닫았거나 취소했는지 (PortOne V2: pgCode·message 에 PAY_PROCESS_CANCELED, V1 호환: USER_CANCEL)
 * @param {object} response - PortOne.requestPayment 결과
 * @returns {boolean}
 */
function isUserCancel(response) {
  const text = `${response?.code || ""} ${response?.pgCode || ""} ${response?.message || ""}`;
  return /PAY_PROCESS_CANCELED|USER_CANCEL/.test(text);
}

/**
 * 결제 준비 결과(교통수단별)를 차례로 결제한다
 * - 취소: 기록하지 않고 멈춤 / 실패: 실패로 기록하고 멈춤 / 성공: 완료로 기록
 * @param {object} paymentDataMap - 서버의 결제 준비 응답 { 교통수단: { storeId, channelKey, paymentId, orderName, amount, groupId } }
 * @returns {Promise<{status: "PAID"|"CANCELED"|"FAILED", message: string}>}
 */
async function payAll(paymentDataMap) {
  const paid = [];
  for (const [transportType, paymentData] of Object.entries(paymentDataMap)) {
    const { storeId, channelKey, paymentId, orderName, amount } = paymentData;
    const paymentResponse = await PortOne.requestPayment({
      storeId,
      channelKey,
      paymentId,
      orderName,
      totalAmount: amount,
      currency: "CURRENCY_KRW",
      payMethod: "CARD",
    });
    // 금액·묶음은 서버가 결제 준비 때 기록했으므로 결제 번호만 보낸다 (서버가 PortOne 에서 확인)
    const record = { paymentId };

    if (paymentResponse?.code !== undefined && isUserCancel(paymentResponse)) {
      return {
        status: "CANCELED",
        message: paid.length ? `${paid.join(", ")} 결제 후 나머지는 취소했습니다.` : "결제를 취소했습니다.",
      };
    }
    if (paymentResponse?.code !== undefined) {
      await postJson("/payment/fail", record);
      return { status: "FAILED", message: `${transportType} 결제에 실패했습니다: ${paymentResponse.message}` };
    }
    const done = await postJson("/payment/complete", record);
    if (!done.ok) {
      const data = await done.json().catch(() => ({}));
      return {
        status: "FAILED",
        message: `${transportType} 결제를 확인하지 못했습니다: ${data?.error || "마이페이지에서 확인해 주세요."}`,
      };
    }
    paid.push(transportType);
  }
  return { status: "PAID", message: "결제가 완료되었습니다." };
}

function postJson(url, body) {
  return fetch(url, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body) });
}

/** 결제 준비 요청. 실패하면 서버 안내 문구로 예외 */
async function prepare(url, options) {
  const response = await fetch(url, { method: "POST", ...options });
  const data = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(data?.error || data?.message || "결제를 준비하지 못했습니다.");
  return data;
}

/**
 * 상세 화면에서 선택한 구간을 바로 결제
 */
async function handleDirectPayment() {
  if (document.querySelectorAll(".route-step.selected").length === 0) {
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
    const paymentDataMap = await prepare("/payment/direct-sdk-ready", {
      headers: { "Content-Type": "application/json", Accept: "application/json" },
      body: JSON.stringify({ selectedLegs }),
    });
    const result = await payAll(paymentDataMap);
    alert(result.message);
  } catch (error) {
    alert(error.message);
  }
}

/**
 * 장바구니 묶음 하나를 결제
 * @param {string} groupId
 * @returns {Promise<{status: string, message: string}>}
 */
async function processGroupPayment(groupId) {
  try {
    const paymentDataMap = await prepare(`/payment/sdk-ready/${groupId}`);
    return await payAll(paymentDataMap);
  } catch (error) {
    return { status: "FAILED", message: error.message };
  }
}

/**
 * 장바구니에서 고른 묶음을 차례로 결제한다. 취소·실패하면 멈추고, 끝나면 목록을 다시 불러온다
 * - 묶음마다 교통수단별 결제창이 열린다 (서버가 묶음·교통수단마다 결제 기록을 따로 만든다)
 */
async function paySelectedGroups() {
  const groupIds = selectedGroupIds();
  if (groupIds.length === 0) {
    alert("결제할 묶음을 골라 주세요.");
    return;
  }
  const button = document.querySelector(".btn-cart-pay");
  button.disabled = true;
  let paidCount = 0;
  let message = "";
  for (const groupId of groupIds) {
    const result = await processGroupPayment(groupId);
    if (result.status !== "PAID") {
      message = paidCount ? `${paidCount}개 묶음을 결제했습니다. ${result.message}` : result.message;
      break;
    }
    paidCount++;
  }
  await loadCartList();
  alert(message || (groupIds.length === 1 ? "결제가 완료되었습니다." : `${paidCount}개 묶음을 모두 결제했습니다.`));
}
