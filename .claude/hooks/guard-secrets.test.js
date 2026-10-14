// guard-secrets.js 회귀 테스트: node .claude/hooks/guard-secrets.test.js
const { spawnSync } = require("node:child_process");
const path = require("node:path");

const hook = path.join(__dirname, "guard-secrets.js");
const bash = (command) => ({ tool_name: "Bash", tool_input: { command } });

const cases = [
  // [기대, 입력]
  ["DENY", { tool_name: "Read", tool_input: { file_path: "E:\\Project\\Personal_Project\\MobilLax\\.env" } }],
  ["ALLOW", { tool_name: "Read", tool_input: { file_path: "E:/Project/Personal_Project/MobilLax/.env.example" } }],
  ["DENY", { tool_name: "Edit", tool_input: { file_path: "/x/.env.local" } }],
  ["DENY", { tool_name: "Grep", tool_input: { pattern: "KEY", path: ".env" } }],
  ["DENY", bash("cat .env")],
  ["DENY", bash("K=$(grep ^TMAP .env | cut -d= -f2)")],
  ["DENY", bash("sed -i s/a/b/ ./.env")],
  ["DENY", bash('cat "E:/Project/Personal_Project/MobilLax/.env"')],
  ["DENY", bash("cp .env.example .env")],
  ["ALLOW", bash("git add .env.example docs/ENV_SETUP.md")],
  ["ALLOW", bash('cd x; git commit -q -m "docs: .env 와 docker compose config 설명"')],
  ["ALLOW", bash('gh pr create --title "x" --body ".env 설명"')],
  [
    "ALLOW",
    bash("cd x; git commit -q -F - <<'EOF'\nfix: .env 설명\n\ndocker compose config 언급\nEOF\ngit log --oneline -1"),
  ],
  // 메시지 예외를 이용한 우회는 막혀야 함
  ["DENY", bash('git commit -m "x"; cat .env')],
  ["DENY", bash("git commit -q -F - <<'EOF'\nfix: x\nEOF\ncat .env")],
  ["DENY", bash('gh pr create --body "x" && docker compose config')],
  ["DENY", bash("docker compose config")],
  ["DENY", bash("docker compose exec -T db printenv MYSQL_PASSWORD")],
  ["DENY", bash("docker inspect mobillax-app-1")],
  ["ALLOW", bash("docker compose up -d --build")],
  ["ALLOW", bash("grep -n env_file docker-compose.yml")],
  ["DENY", { tool_name: "PowerShell", tool_input: { command: "Get-Content .env" } }],
  ["DENY", "not-json"],
];

let failed = 0;
for (const [expected, input] of cases) {
  const r = spawnSync("node", [hook], { input: typeof input === "string" ? input : JSON.stringify(input) });
  const actual = r.status === 2 || String(r.stdout).includes('"deny"') ? "DENY" : "ALLOW";
  const ok = actual === expected;
  if (!ok) failed++;
  console.log(`${ok ? "✓" : "✗"} ${actual.padEnd(5)} ${JSON.stringify(input).slice(0, 110)}`);
}
console.log(failed ? `\n${failed}건 실패` : `\n${cases.length}건 모두 통과`);
process.exit(failed ? 1 : 0);
