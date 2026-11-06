package MobilLax.Domain.Account;

import MobilLax.Common.Validation.AccountRules;
import MobilLax.Common.Validation.PlainText;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 비밀번호 재확인 입력 (v2.14, 보안 규칙 S10·S14). 화면 폼과 fetch 본문이 함께 쓴다 */
@Data
public class PasswordConfirmForm {

    @NotBlank(message = "비밀번호를 입력해 주세요.")
    @Size(max = AccountRules.MAX_PASSWORD_LENGTH, message = "현재 비밀번호가 맞지 않습니다.")
    private String password;

    /** 확인한 뒤 돌아갈 화면. 허용한 화면이 아니면 마이페이지로 간다 (PasswordConfirmController) */
    @Size(max = 40, message = "돌아갈 화면이 올바르지 않습니다.")
    @PlainText
    private String next;
}
