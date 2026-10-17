// 보안 회귀 테스트 S8 (컨테이너): node scripts/infra-rules.test.js — docs/SECURITY_RULES.md
// 이 테스트를 지우거나 약하게 바꾸지 않는다. 규칙을 바꿔야 하면 문서와 함께 리뷰를 거친다.
// Dockerfile·docker-compose.yml 은 앱 테스트(Gradle) 컨테이너에 들어가지 않아 여기서 검사한다.
const fs = require("node:fs");
const path = require("node:path");

const root = path.join(__dirname, "..");
const read = (name) => fs.readFileSync(path.join(root, name), "utf8");
const failures = [];
const check = (ok, message) => {
  if (!ok) failures.push(message);
};

// 1. 실행 이미지(마지막 FROM 이후)는 root 가 아닌 사용자로 실행한다
const dockerfile = read("Dockerfile");
const runStage = dockerfile.slice(dockerfile.lastIndexOf("\nFROM "));
const users = [...runStage.matchAll(/^USER\s+(\S+)/gm)].map((m) => m[1]);
check(users.length > 0, "Dockerfile 실행 단계에 USER 지시어가 없다 (root 로 실행됨)");
check(
  users.length === 0 || !["root", "0", "0:0"].includes(users[users.length - 1]),
  "Dockerfile 실행 단계의 마지막 USER 가 root 다",
);
check(runStage.indexOf("USER") < runStage.indexOf("ENTRYPOINT"), "Dockerfile 에서 USER 가 ENTRYPOINT 보다 뒤에 있다");

// 2. docker-compose 헬스체크·명령 인자에 비밀번호를 넣지 않는다 (docker inspect·ps 에 보인다)
const compose = read("docker-compose.yml");
const argLines = compose.split("\n").filter((line) => /^\s*(test|command|entrypoint)\s*:/.test(line));
for (const line of argLines) {
  check(
    !/-p\$\{|-p\$\$|--password[= ]\$|\$\{[A-Z_]*PASSWORD\}/.test(line),
    `docker-compose.yml 명령 인자에 비밀번호가 들어 있다: ${line.trim().slice(0, 40)}...`,
  );
}

// 3. 앱 컨테이너 헬스체크는 로그인 없이 상태만 주는 /health/live 를 쓴다 (S7)
check(/\/health\/live/.test(compose), "docker-compose.yml 앱 헬스체크가 /health/live 를 쓰지 않는다");

if (failures.length) {
  console.error(`infra-rules: ${failures.length}건 실패`);
  failures.forEach((f) => console.error(`  ✗ ${f}`));
  process.exit(1);
}
console.log("infra-rules: 통과 (USER 비root, 명령 인자에 비밀번호 없음, /health/live 헬스체크)");
