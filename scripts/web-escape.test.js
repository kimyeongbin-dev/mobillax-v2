// 보안 회귀 테스트 S10 (화면 출력): node scripts/web-escape.test.js — docs/SECURITY_RULES.md
// 이 테스트를 지우거나 약하게 바꾸지 않는다. 규칙을 바꿔야 하면 문서와 함께 리뷰를 거친다.
// 서버·외부 API·사용자 문자열은 innerHTML 에 넣기 전에 escapeHtml 을 거치고, 인라인 이벤트 문자열에는 넣지 않는다.
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");

const dir = path.join(__dirname, "..", "src", "main", "resources", "static", "javascript");
const failures = [];
const check = (ok, message) => {
  if (!ok) failures.push(message);
};

// 1. escapeHtml 이 태그·속성·스크립트 문자열을 글자로 바꾼다
const context = {};
vm.createContext(context);
vm.runInContext(fs.readFileSync(path.join(dir, "transport.js"), "utf8"), context);
const escapeHtml = context.escapeHtml;
check(typeof escapeHtml === "function", "transport.js 에 escapeHtml 이 없다");
if (typeof escapeHtml === "function") {
  const cases = [
    ["<script>alert(1)</script>", "&lt;script&gt;alert(1)&lt;/script&gt;"],
    ['" onmouseover="x', "&quot; onmouseover=&quot;x"],
    ["' onclick='x", "&#39; onclick=&#39;x"],
    ["`${x}`", "&#96;${x}&#96;"],
    ["A&B", "A&amp;B"],
    [null, ""],
    [undefined, ""],
    [8400, "8400"],
  ];
  for (const [input, expected] of cases) {
    check(
      escapeHtml(input) === expected,
      `escapeHtml(${JSON.stringify(input)}) 결과가 ${JSON.stringify(expected)} 가 아니다`,
    );
  }
}

// 2. 화면 스크립트: 인라인 이벤트(onclick 등) 문자열에 값을 끼워 넣지 않는다
//    (따옴표가 든 이름이 스크립트로 실행된다. 이벤트는 addEventListener 로 단다)
for (const file of fs.readdirSync(dir).filter((f) => f.endsWith(".js"))) {
  const source = fs.readFileSync(path.join(dir, file), "utf8");
  const inline = source.match(/\son[a-z]+=("[^"]*\$\{|'[^']*\$\{)/g) || [];
  check(inline.length === 0, `${file}: 인라인 이벤트 속성에 값을 끼워 넣는다 (${inline.length}곳)`);
}

if (failures.length) {
  console.error(`web-escape: ${failures.length}건 실패`);
  failures.forEach((f) => console.error(`  ✗ ${f}`));
  process.exit(1);
}
console.log("web-escape: 통과 (escapeHtml, 인라인 이벤트에 값 없음)");
