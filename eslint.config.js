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
        Tmapv2: "readonly",
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
    // 팀 프로젝트에서 물려받은 지도 모듈: var 재선언 등 기존 오류를 v2 정리 전까지 경고로 둠
    files: ["src/main/resources/static/module/carRoute.js", "src/main/resources/static/module/init.js"],
    rules: {
      "no-redeclare": "warn",
      "no-useless-assignment": "warn",
      "no-empty": "warn",
      "no-unassigned-vars": "warn",
    },
  },
  {
    // import/export 를 쓰는 파일
    files: [
      "src/main/resources/static/javascript/exscript.js",
      "src/main/resources/static/javascript/service/TmapService.js",
    ],
    languageOptions: { sourceType: "module" },
  },
  {
    files: [".claude/hooks/**/*.js", "eslint.config.js"],
    languageOptions: {
      ecmaVersion: 2022,
      sourceType: "commonjs",
      globals: globals.node,
    },
  },
];
