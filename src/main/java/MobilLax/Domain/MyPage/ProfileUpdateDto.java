package MobilLax.Domain.MyPage;

import MobilLax.Common.Validation.UserName;
import lombok.Data;

/** 프로필 수정 폼 (보안 규칙 S10) */
@Data
public class ProfileUpdateDto {
    @UserName
    private String name;
}
