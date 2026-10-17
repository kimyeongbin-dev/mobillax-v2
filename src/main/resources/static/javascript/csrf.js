/**
 * CSRF 보호: 서버가 내려준 XSRF-TOKEN 쿠키 값을 같은 사이트로 보내는 GET 이외 요청의 X-XSRF-TOKEN 헤더에 넣는다
 * (폼은 Thymeleaf 가 _csrf 를 넣으므로 fetch 만 해당) — 규칙: SecurityConfig, SecurityRegressionTests
 */
(() => {
  const originalFetch = window.fetch.bind(window);
  const readToken = () => {
    const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]+)/);
    return match ? decodeURIComponent(match[1]) : null;
  };
  window.fetch = (input, init = {}) => {
    const method = (init.method || (input instanceof Request ? input.method : "GET")).toUpperCase();
    const url = new URL(input instanceof Request ? input.url : String(input), window.location.href);
    const token = readToken();
    if (!["GET", "HEAD", "OPTIONS"].includes(method) && url.origin === window.location.origin && token) {
      const headers = new Headers(init.headers || (input instanceof Request ? input.headers : undefined));
      headers.set("X-XSRF-TOKEN", token);
      return originalFetch(input, { ...init, headers });
    }
    return originalFetch(input, init);
  };
})();
