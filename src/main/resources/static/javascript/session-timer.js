/**
 * 로그인 세션 남은 시간 표시 (v2.5) — 공통 레이아웃(fragments/layout.html)이 모든 로그인 화면에 넣는다
 *
 * - 서버가 <meta name="session-timeout" content="초"> 로 세션 유지 시간을 알려 준다 (로그인하지 않았으면 0 → 표시 안 함)
 * - 서버 세션은 요청이 올 때마다 다시 연장되므로, 화면 열기와 같은 사이트 fetch 를 "마지막 활동"으로 보고 다시 센다
 * - 여러 탭: 마지막 활동 시각을 localStorage 로 공유해 가장 최근 활동 기준으로 센다
 * - 남은 시간을 확인하려고 서버를 부르지 않는다 (부르면 그 자체로 세션이 연장된다). 연장은 사용자가 버튼으로만
 * - 5분 전부터 경고 표시, 0이 되면 "만료" 표시와 로그인 링크
 */
(() => {
  const meta = document.querySelector('meta[name="session-timeout"]');
  const box = document.querySelector("[data-session-timer]");
  const timeoutMs = Number(meta?.content || 0) * 1000;
  if (!box || !(timeoutMs > 0)) return;

  const KEY = "mobillax.session.lastActivity";
  const WARN_MS = 5 * 60 * 1000;
  const remainingText = box.querySelector("[data-remaining]");
  const extendButton = box.querySelector("[data-extend]");
  let lastActivity = Date.now();
  let expired = false;

  const store = (time) => {
    try {
      localStorage.setItem(KEY, String(time));
    } catch {
      /* 저장소를 못 쓰면 이 탭 기준으로만 센다 */
    }
  };
  const latestActivity = () => {
    try {
      return Math.max(lastActivity, Number(localStorage.getItem(KEY)) || 0);
    } catch {
      return lastActivity;
    }
  };
  const touch = () => {
    lastActivity = Date.now();
    store(lastActivity);
  };

  const format = (ms) => {
    const total = Math.ceil(ms / 1000);
    const m = Math.floor(total / 60);
    const s = total % 60;
    return `${m}:${String(s).padStart(2, "0")}`;
  };

  const markExpired = () => {
    expired = true;
    box.classList.remove("warning");
    box.classList.add("expired");
    remainingText.textContent = "로그인 만료";
    extendButton.hidden = true;
    let link = box.querySelector("[data-login]");
    if (!link) {
      link = document.createElement("a");
      link.href = "/login";
      link.dataset.login = "";
      link.textContent = "다시 로그인";
      box.appendChild(link);
    }
  };

  const render = () => {
    if (expired) return;
    const remaining = timeoutMs - (Date.now() - latestActivity());
    if (remaining <= 0) {
      markExpired();
      return;
    }
    remainingText.textContent = `로그인 유지 ${format(remaining)}`;
    box.classList.toggle("warning", remaining <= WARN_MS);
  };

  // 같은 사이트로 보낸 요청이 성공하면 서버 세션도 연장된 것이므로 다시 센다. 401 이면 이미 만료
  const originalFetch = window.fetch.bind(window);
  window.fetch = (input, init) =>
    originalFetch(input, init).then((response) => {
      const url = new URL(input instanceof Request ? input.url : String(input), window.location.href);
      if (url.origin === window.location.origin) {
        if (response.status === 401) markExpired();
        else touch();
      }
      return response;
    });

  extendButton.addEventListener("click", async () => {
    const response = await fetch("/api/session").catch(() => null);
    if (!response || !response.ok) markExpired();
    render();
  });

  touch(); // 화면을 연 것 자체가 서버 요청이었다
  render();
  setInterval(render, 1000);
})();
