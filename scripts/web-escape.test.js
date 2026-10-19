// 보안 회귀 테스트 S10 (화면 출력): node scripts/web-escape.test.js — docs/SECURITY_RULES.md
// 이 테스트를 지우거나 약하게 바꾸지 않는다. 규칙을 바꿔야 하면 문서와 함께 리뷰를 거친다.
//
// 1. safe-html.js 의 html`` 이 끼워 넣는 값을 모두 이스케이프하는지
// 2. 앞으로 추가하는 화면 스크립트도 HTML 을 넣을 때 html`` 을 거치는지 (빠뜨리면 실패)
//    - innerHTML·outerHTML 대입, insertAdjacentHTML, document.write 의 값은
//      html`...` 이거나 값을 끼워 넣지 않은 고정 문자열이어야 한다
// 3. 인라인 이벤트 문자열(onclick="...${값}...")에 값을 넣지 않는지
// 4. 화면이 safe-html.js 를 다른 화면 스크립트보다 먼저 불러오는지
// 5. 템플릿에 인라인 이벤트 속성(onclick 등)과 본문이 있는 <script> 가 없는지 (v2.8)
//    이벤트는 화면 스크립트에서 addEventListener 로, 제출 전 확인은 <form data-confirm> (confirm-form.js)
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");

const root = path.join(__dirname, "..", "src", "main", "resources");
const dir = path.join(root, "static", "javascript");
const failures = [];
const check = (ok, message) => {
  if (!ok) failures.push(message);
};

// 1. html``·raw()·escapeHtml 동작
const context = {};
vm.createContext(context);
vm.runInContext(
  `${fs.readFileSync(path.join(dir, "safe-html.js"), "utf8")}\nthis.api = { html, raw, escapeHtml };`,
  context,
);
const { html, raw, escapeHtml } = context.api;
// html`` 이라는 이름이면 Prettier 가 안의 HTML 을 고쳐 써서(</a>, <br /> 추가) 입력이 바뀐다. 별칭으로 부른다
const h = html;
const attack = "<img src=x onerror=alert(1)>";
const escaped = "&lt;img src=x onerror=alert(1)&gt;";
const cases = [
  [String(h`<b>${attack}</b>`), `<b>${escaped}</b>`, "값 이스케이프"],
  [String(h`<a title="${'" onmouseover="x'}">`), '<a title="&quot; onmouseover=&quot;x">', "속성 큰따옴표"],
  [String(h`${"' onclick='x"}`), "&#39; onclick=&#39;x", "작은따옴표"],
  [String(h`${"`x`"}`), "&#96;x&#96;", "백틱"],
  [String(h`<p>${h`<i>${attack}</i>`}</p>`), `<p><i>${escaped}</i></p>`, "중첩 html 은 한 번만 이스케이프"],
  [String(h`${raw('<span class="ok">가능</span>')}`), '<span class="ok">가능</span>', "고정 마크업 raw"],
  [String(h`${[attack, h`<br>`]}`), `${escaped}<br>`, "배열"],
  [String(h`${null}${undefined}${false}${0}`), "0", "빈 값"],
  [escapeHtml("A&B"), "A&amp;B", "escapeHtml"],
];
for (const [actual, expected, label] of cases) {
  check(actual === expected, `${label}: ${JSON.stringify(actual)} (기대 ${JSON.stringify(expected)})`);
}

/** 대입·호출 뒤에 오는 값이 안전한 형태인지: html`...`, 또는 ${ } 없는 고정 문자열 */
function isSafeValue(rest) {
  const value = rest.trimStart();
  if (value.startsWith("html`")) return true;
  const quote = value[0];
  if (quote === '"' || quote === "'") {
    // 일반 문자열 리터럴: 뒤에 + 로 값을 이어 붙이지 않았는지
    const end = value.indexOf(quote, 1);
    return end > 0 && !/^\s*\+/.test(value.slice(end + 1));
  }
  if (quote === "`") {
    const end = value.indexOf("`", 1);
    return end > 0 && !value.slice(0, end).includes("${");
  }
  return false; // 변수·함수 결과 등은 html`` 로 감싸야 한다
}

// 2·3. 화면 스크립트 전체 검사
for (const file of fs.readdirSync(dir).filter((f) => f.endsWith(".js") && f !== "safe-html.js")) {
  const source = fs.readFileSync(path.join(dir, file), "utf8");
  const lineOf = (index) => source.slice(0, index).split("\n").length;

  for (const m of source.matchAll(/\.(innerHTML|outerHTML)\s*\+?=(?!=)/g)) {
    check(
      isSafeValue(source.slice(m.index + m[0].length)),
      `${file}:${lineOf(m.index)} ${m[1]} 에 html\`\` 이 아닌 값을 넣는다`,
    );
  }
  for (const m of source.matchAll(/(insertAdjacentHTML\([^,]+,|document\.write(?:ln)?\()/g)) {
    check(
      isSafeValue(source.slice(m.index + m[0].length)),
      `${file}:${lineOf(m.index)} ${m[1].split("(")[0]} 에 html\`\` 이 아닌 값을 넣는다`,
    );
  }
  const inline = source.match(/\son[a-z]+=("[^"]*\$\{|'[^']*\$\{)/g) || [];
  check(inline.length === 0, `${file}: 인라인 이벤트 속성에 값을 끼워 넣는다 (${inline.length}곳)`);
}

// 4. 화면 스크립트를 쓰는 템플릿은 safe-html.js 를 먼저 불러온다
//    직접 불러오거나, 공통 head 조각(fragments/layout :: head, csrf.js → safe-html.js 포함)을 먼저 넣는다
const templates = path.join(root, "templates");
const COMMON = /javascript\/(?:csrf|safe-html|confirm-form|session-timer)\.js/;
const layout = fs.readFileSync(path.join(templates, "fragments", "layout.html"), "utf8");
const headFragment = layout.slice(layout.indexOf('th:fragment="head"'), layout.indexOf("</th:block>"));
check(
  headFragment.indexOf("csrf.js") >= 0 && headFragment.indexOf("csrf.js") < headFragment.indexOf("safe-html.js"),
  "fragments/layout.html head 조각이 csrf.js → safe-html.js 순서로 불러오지 않는다",
);

const htmlFiles = (base) =>
  fs.readdirSync(base, { withFileTypes: true }).flatMap((e) => {
    const full = path.join(base, e.name);
    return e.isDirectory() ? htmlFiles(full) : e.name.endsWith(".html") ? [full] : [];
  });
for (const file of htmlFiles(templates).filter((f) => !f.includes(`${path.sep}fragments${path.sep}`))) {
  const page = path.relative(templates, file);
  const source = fs.readFileSync(file, "utf8");
  const inlineEvents = source.match(/<[^>]*\son[a-z]+\s*=/gi) || [];
  check(
    inlineEvents.length === 0,
    `${page}: 인라인 이벤트 속성이 있다 (${inlineEvents.length}곳, addEventListener 를 쓴다)`,
  );
  const inlineScripts = source.match(/<script(?![^>]*\s(?:th:)?src=)[^>]*>\s*\S/gi) || [];
  check(inlineScripts.length === 0, `${page}: 본문이 있는 <script> 가 있다 (화면 스크립트 파일로 옮긴다)`);
  const screens = [...source.matchAll(/javascript\/[\w-]+\.js/g)].filter((m) => !COMMON.test(m[0])).map((m) => m.index);
  if (screens.length === 0) continue;
  const safe = [source.indexOf("javascript/safe-html.js"), source.indexOf("fragments/layout :: head")]
    .filter((i) => i >= 0)
    .reduce((a, b) => Math.min(a, b), Infinity);
  check(
    screens.every((i) => i > safe),
    `${page}: safe-html.js 를 화면 스크립트보다 먼저 불러오지 않는다`,
  );
}

if (failures.length) {
  console.error(`web-escape: ${failures.length}건 실패`);
  failures.forEach((f) => console.error(`  ✗ ${f}`));
  process.exit(1);
}
console.log(
  "web-escape: 통과 (html`` 자동 이스케이프, HTML 대입은 html`` 또는 고정 문자열, 템플릿·스크립트에 인라인 이벤트 없음)",
);
