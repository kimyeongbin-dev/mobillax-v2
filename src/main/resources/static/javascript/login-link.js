/**
 * 로그인 링크가 지금 화면으로 돌아오게 한다 (v2.9, 공통 head 조각이 모든 화면에 넣는다)
 * - <a data-login-next> 의 주소를 /login?next=<지금 화면> 으로 바꾼다
 * - 서버(LoginRedirect)가 정해 둔 내부 화면만 받고, 나머지는 홈으로 보낸다
 */
document.addEventListener("DOMContentLoaded", () => {
  const next = encodeURIComponent(window.location.pathname + window.location.search);
  document.querySelectorAll("a[data-login-next]").forEach((link) => {
    link.href = `/login?next=${next}`;
  });
});
