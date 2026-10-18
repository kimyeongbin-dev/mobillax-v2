package MobilLax.Domain.MyPage;

import MobilLax.Common.Validation.AccountRules;
import MobilLax.Common.Validation.Password;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 비밀번호 변경 폼 (v2.5, 보안 규칙 S10)
 * - 형식은 여기서, "현재 비밀번호가 맞는지·새 비밀번호가 다른지"는 서비스가 확인한다
 */
@Data
public class PasswordChangeForm {

    /** 화면 입력 순서 (오류 문구를 고를 때) */
    static final String[] FIELD_ORDER = {"currentPassword", "newPassword", "newPasswordConfirm", "confirmed"};

    @NotBlank(message = "현재 비밀번호를 입력해 주세요.")
    @Size(max = AccountRules.MAX_PASSWORD_LENGTH, message = "현재 비밀번호가 맞지 않습니다.")
    private String currentPassword;

    @Password
    private String newPassword;

    @NotBlank(message = "새 비밀번호를 한 번 더 입력해 주세요.")
    @Size(max = AccountRules.MAX_PASSWORD_LENGTH, message = "새 비밀번호와 확인이 다릅니다.")
    private String newPasswordConfirm;

    @AssertTrue(message = "새 비밀번호와 확인이 다릅니다.")
    public boolean isConfirmed() {
        return newPassword != null && newPassword.equals(newPasswordConfirm);
    }
}
