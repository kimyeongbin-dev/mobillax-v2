/**
 * 안전한 HTML 만들기 (보안 규칙 S10) — 화면 스크립트보다 먼저 불러온다
 *
 * 서버·외부 API(TMAP·카카오)·사용자에게서 온 문자열은 개발자도구나 외부 데이터로 얼마든지 바뀔 수 있다.
 * innerHTML 에 넣을 때는 반드시 html`...` 태그 템플릿을 쓴다. 끼워 넣는 값은 모두 자동으로 이스케이프된다.
 *
 *   element.innerHTML = html`<strong>${item.startName}</strong>`;          // 값은 글자로만
 *   element.innerHTML = html`<p>${ok ? raw('<span class="ok">가능</span>') : ""}</p>`; // 코드에 박힌 고정 마크업만 raw
 *
 * - 다른 html`...` 결과를 끼워 넣으면 다시 이스케이프하지 않는다
 * - raw() 에는 코드에 직접 쓴 고정 문자열만 넣는다. 서버·사용자 값은 절대 넣지 않는다
 * - 이 규칙은 scripts/web-escape.test.js 가 검사한다 (innerHTML 에 html`` 이 아닌 템플릿을 넣으면 실패)
 */
/* exported html, raw, escapeHtml */

const HTML_ESCAPES = { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;", "`": "&#96;" };

/** 이스케이프가 끝난 HTML (html`` 과 raw() 의 결과). innerHTML 에 넣으면 문자열로 바뀐다 */
class SafeHtml {
  constructor(markup) {
    this.markup = markup;
  }

  toString() {
    return this.markup;
  }
}

/**
 * 문자열을 HTML 글자로 바꾼다 (꺾쇠·따옴표·백틱·&)
 * @param {*} value
 * @returns {string}
 */
function escapeHtml(value) {
  return String(value ?? "").replace(/[&<>"'`]/g, (c) => HTML_ESCAPES[c]);
}

/** 값 하나를 HTML 조각으로: SafeHtml 은 그대로, 배열은 이어 붙이고, 나머지는 이스케이프 */
function toHtml(value) {
  if (value instanceof SafeHtml) return value.markup;
  if (Array.isArray(value)) return value.map(toHtml).join("");
  if (value === false || value === null || value === undefined) return "";
  return escapeHtml(value);
}

/**
 * 끼워 넣는 값을 모두 이스케이프하는 태그 템플릿
 * @returns {SafeHtml}
 */
function html(strings, ...values) {
  return new SafeHtml(strings.reduce((out, part, i) => out + part + (i < values.length ? toHtml(values[i]) : ""), ""));
}

/**
 * 코드에 직접 쓴 고정 마크업을 이스케이프 없이 끼워 넣을 때만 쓴다 (서버·사용자 값 금지)
 * @param {string} markup
 * @returns {SafeHtml}
 */
function raw(markup) {
  return new SafeHtml(String(markup));
}
