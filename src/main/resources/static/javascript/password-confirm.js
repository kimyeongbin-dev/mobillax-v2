/* exported confirmPassword */
/**
 * 비밀번호 재확인 창 (v2.14, 보안 규칙 S14): 결제처럼 화면을 떠나면 고른 내용을 잃는 작업에 쓴다
 * 비밀번호는 서버로 보내 확인만 하고 어디에도 두지 않는다. 서버 호출은 csrf.js 가 토큰을 붙인다
 *
 * @returns {Promise<boolean>} 확인했으면 true, 취소했으면 false
 */
function confirmPassword() {
  return new Promise((resolve) => {
    const dialog = document.createElement("dialog");
    dialog.className = "password-dialog";

    const form = document.createElement("form");
    const title = document.createElement("h3");
    title.textContent = "비밀번호 확인";
    const help = document.createElement("p");
    help.textContent = "결제 전에 비밀번호를 한 번 더 확인합니다. 확인하면 5분 동안 다시 묻지 않습니다.";
    const input = document.createElement("input");
    input.type = "password";
    input.required = true;
    input.maxLength = 64;
    input.autocomplete = "current-password";
    input.setAttribute("aria-label", "비밀번호");
    const message = document.createElement("p");
    message.className = "password-dialog-error";
    message.setAttribute("role", "alert");
    const submit = document.createElement("button");
    submit.type = "submit";
    submit.textContent = "확인";
    const cancel = document.createElement("button");
    cancel.type = "button";
    cancel.textContent = "취소";
    const buttons = document.createElement("div");
    buttons.className = "password-dialog-buttons";
    buttons.append(submit, cancel);
    form.append(title, help, input, message, buttons);
    dialog.append(form);
    document.body.append(dialog);

    const finish = (confirmed) => {
      dialog.close();
      dialog.remove();
      resolve(confirmed);
    };
    cancel.addEventListener("click", () => finish(false));
    dialog.addEventListener("cancel", (event) => {
      event.preventDefault();
      finish(false);
    });
    form.addEventListener("submit", async (event) => {
      event.preventDefault();
      submit.disabled = true;
      message.textContent = "";
      try {
        const response = await fetch("/api/account/confirm-password", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ password: input.value }),
        });
        if (response.ok) {
          finish(true);
          return;
        }
        const data = await response.json().catch(() => ({}));
        message.textContent = data.error || "비밀번호를 확인하지 못했습니다.";
      } catch {
        message.textContent = "비밀번호를 확인하지 못했습니다. 잠시 뒤 다시 시도해 주세요.";
      }
      input.value = "";
      submit.disabled = false;
      input.focus();
    });

    dialog.showModal();
    input.focus();
  });
}
