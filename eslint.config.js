// ESLint 설정 (flat config)
// 화면 스크립트는 여러 <script> 파일이 전역 함수를 공유한다. 다른 파일의 함수는 파일 머리의
// /* global ... */ 로, 다른 파일이 쓰는 함수는 /* exported ... */ 로 밝힌다.
// v2.8: 템플릿의 인라인 이벤트를 모두 없애 쓰지 않는 변수·정의되지 않은 이름(no-unused-vars, no-undef)을 오류로 막는다.
const js = require("@eslint/js");
const globals = require("globals");

module.exports = [
  {
    ignores: ["**/jquery-*.js", "build/**", "node_modules/**", "out/**"],
  },
  js.configs.recommended,
  {
    files: ["src/main/resources/static/**/*.js"],
    languageOptions: {
      ecmaVersion: 2022,
      sourceType: "script",
      globals: {
        ...globals.browser,
        kakao: "readonly",
        PortOne: "readonly",
      },
    },
    rules: {
      "no-unused-vars": "error",
      "no-undef": "error",
    },
  },
  {
    files: [".claude/hooks/**/*.js", "scripts/**/*.js", "eslint.config.js"],
    languageOptions: {
      ecmaVersion: 2022,
      sourceType: "commonjs",
      globals: globals.node,
    },
  },
];
