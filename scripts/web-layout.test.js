// 공통 레이아웃 회귀 테스트: node scripts/web-layout.test.js (v2.5)
// 이 테스트를 지우거나 약하게 바꾸지 않는다. 규칙을 바꿔야 하면 문서와 함께 리뷰를 거친다.
//
// 모든 화면(templates/**/*.html, fragments 제외)이
// 1. 공통 head 조각(fragments/layout :: head)을 넣는다: CSRF·출력 이스케이프·세션 타이머 스크립트가 빠지지 않게
// 2. 공통 메뉴(header 또는 compactNav 조각)를 넣는다: 장바구니·예매 내역이 어느 화면에서든 한 번에 닿게
// 3. 헤더를 직접 쓰지 않는다: 메뉴가 화면마다 달라지지 않게
const fs = require("node:fs");
const path = require("node:path");

const templates = path.join(__dirname, "..", "src", "main", "resources", "templates");
const failures = [];
const check = (ok, message) => {
  if (!ok) failures.push(message);
};

const htmlFiles = (base) =>
  fs.readdirSync(base, { withFileTypes: true }).flatMap((e) => {
    const full = path.join(base, e.name);
    return e.isDirectory() ? htmlFiles(full) : e.name.endsWith(".html") ? [full] : [];
  });

const pages = htmlFiles(templates).filter((f) => !f.includes(`${path.sep}fragments${path.sep}`));
check(pages.length >= 8, `검사한 화면이 너무 적다 (${pages.length}개)`);

for (const file of pages) {
  const page = path.relative(templates, file);
  const source = fs.readFileSync(file, "utf8");
  check(source.includes("~{fragments/layout :: head}"), `${page}: 공통 head 조각(fragments/layout :: head)이 없다`);
  check(
    /~\{fragments\/layout :: (header\(|compactNav)/.test(source),
    `${page}: 공통 메뉴(fragments/layout :: header 또는 compactNav)가 없다`,
  );
  const handWritten = [...source.matchAll(/<header(?![^>]*th:replace)/g)].length;
  check(handWritten === 0, `${page}: 헤더를 직접 쓴다 (공통 header 조각을 쓴다)`);
}

// 공통 메뉴에서 장바구니·예매 내역으로 바로 갈 수 있는지 (접근 깊이 1)
const layout = fs.readFileSync(path.join(templates, "fragments", "layout.html"), "utf8");
for (const fragment of ['th:fragment="header(active)"', 'th:fragment="compactNav"']) {
  const start = layout.indexOf(fragment);
  const end = layout.indexOf("</", layout.indexOf(fragment.includes("header") ? "</header>" : "</nav>", start));
  const body = layout.slice(start, end);
  for (const target of ["@{/cart}", "@{/mypage/payments}", "@{/mypage}", "data-session-timer"]) {
    check(body.includes(target), `${fragment} 에 ${target} 가 없다`);
  }
}

if (failures.length) {
  console.error(`web-layout: ${failures.length}건 실패`);
  failures.forEach((f) => console.error(`  ✗ ${f}`));
  process.exit(1);
}
console.log(`web-layout: 통과 (화면 ${pages.length}개 모두 공통 head·메뉴, 장바구니·예매 내역 한 번에 이동)`);
