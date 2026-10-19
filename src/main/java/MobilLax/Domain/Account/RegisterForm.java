package MobilLax.Domain.Account;

import MobilLax.Common.Validation.AccountEmail;
import MobilLax.Common.Validation.Password;
import MobilLax.Common.Validation.UserName;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 회원가입 폼 (보안 규칙 S10) — 화면의 maxlength·required 는 우회할 수 있어 서버가 이 제약으로 다시 검사한다
 */
@Data
public class RegisterForm {
    @UserName
    private String name;
    @NotNull @AccountEmail
    private String email;
    @Password
    private String password;
}
