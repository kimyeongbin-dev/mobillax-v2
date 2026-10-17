// ESLint 설정 (flat config)
// 기존 팀 코드는 여러 <script> 파일이 전역 함수를 공유하는 구조라,
// 파일 간 참조(no-undef)와 HTML onclick 에서만 쓰는 함수(no-unused-vars)는 경고로 둔다.
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
      "no-unused-vars": "warn",
      "no-undef": "warn",
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
