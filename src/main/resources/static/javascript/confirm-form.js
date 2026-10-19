/**
 * 되돌리기 어려운 폼 제출 전 확인 (공통 head 조각이 모든 화면에 넣는다)
 * - <form data-confirm="문구"> 를 제출하면 확인 창을 띄우고, 취소하면 보내지 않는다
 * - 화면마다 인라인 onsubmit·onclick 을 쓰지 않는다 (보안 규칙 S10, scripts/web-escape.test.js)
 */
document.addEventListener("submit", (event) => {
  const form = event.target;
  const message = form instanceof HTMLFormElement ? form.dataset.confirm : undefined;
  if (message && !confirm(message)) {
    event.preventDefault();
  }
});
