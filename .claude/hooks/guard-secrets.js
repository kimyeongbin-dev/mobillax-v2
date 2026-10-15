#!/usr/bin/env node
// Claude Code PreToolUse 훅: AI 도구가 .env 등 비밀값 파일을 읽거나 출력하지 못하게 차단
// 규칙: CLAUDE.md "비밀값" 절. .env.example 은 허용.
let raw = "";
process.stdin.on("data", (c) => (raw += c));
process.stdin.on("end", () => {
  let input;
  try {
    input = JSON.parse(raw);
  } catch {
    process.exit(2); // 입력을 해석할 수 없으면 안전하게 차단
  }
  const reason = check(input.tool_name || "", input.tool_input || {});
  if (reason) {
    process.stdout.write(
      JSON.stringify({
        hookSpecificOutput: {
          hookEventName: "PreToolUse",
          permissionDecision: "deny",
          permissionDecisionReason: `[guard-secrets] ${reason}. 비밀값 확인은 사용자가 직접 하거나 값을 출력하지 않는 스크립트로 합니다.`,
        },
      }),
    );
  }
});

// .env, .env.local, .env.prod 등은 막고 .env.example 은 허용
const isSecretEnvName = (name) => /^\.env(\.[\w-]+)*$/.test(name) && !/\.example$/.test(name);
const basename = (p) => String(p).split(/[\\/]/).pop();

function check(tool, ti) {
  if (["Read", "Edit", "Write", "NotebookEdit"].includes(tool)) {
    const p = ti.file_path || ti.notebook_path;
    if (p && isSecretEnvName(basename(p))) return `${tool} 로 ${basename(p)} 접근 차단`;
  }

  if (tool === "Grep" || tool === "Glob") {
    const targets = [ti.path, ti.glob, tool === "Glob" ? ti.pattern : null];
    for (const v of targets) {
      if (v && isSecretEnvName(basename(v))) return `${tool} 로 ${basename(v)} 검색 차단`;
    }
  }

  if (tool === "Bash" || tool === "PowerShell") {
    const cmd = stripMessages(String(ti.command || ""));

    const tokens = cmd.match(/(?<![\w.-])(?:[\w.~-]*[\\/])*\.env(?:\.[\w-]+)*(?![\w-])/g) || [];
    const hit = tokens.find((t) => isSecretEnvName(basename(t)));
    if (hit) return `셸 명령에서 ${basename(hit)} 참조 차단`;

    // 컨테이너에 주입된 비밀값을 출력하는 명령
    if (/docker(-compose| compose)\b[^|;&]*\bconfig\b/.test(cmd))
      return "docker compose config 는 .env 값을 펼쳐 출력하므로 차단";
    if (/\bdocker\b[^|;&]*\binspect\b/.test(cmd)) return "docker inspect 는 컨테이너 환경변수를 출력하므로 차단";
    if (/\b(exec|run)\b[^|;&]*\s(printenv|env)(\s|$)/.test(cmd)) return "컨테이너 환경변수 출력 차단";
  }

  return null;
}

// 커밋 메시지·PR 본문에 ".env" 같은 글자가 들어가는 것은 허용하되, 같은 명령의 나머지 부분은 그대로 검사
// - heredoc 본문(<<'EOF' ... EOF)과 -m/--message/--title/--body/--notes 의 인자만 지움
function stripMessages(cmd) {
  return cmd
    .replace(/<<-?\s*(['"]?)(\w+)\1[^\n]*\n[\s\S]*?\n\s*\2\s*(?=\n|$)/g, "<<HEREDOC")
    .replace(/(^|\s)(-m|--message|--title|--body|--notes)\s+("(?:[^"\\]|\\.)*"|'[^']*')/g, "$1$2 MSG");
}
