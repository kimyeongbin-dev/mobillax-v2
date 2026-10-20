package MobilLax.Domain.Admin;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 관리자 OTP 등록 정보 (admin_otp 테이블, v2.11)
 * - 비밀값은 OtpSecretCipher 로 암호화해서만 저장한다
 * - enabledAt 이 비어 있으면 등록 중(QR 을 보여 줬지만 아직 번호로 확인하지 않음)
 * - lastUsedStep: 마지막으로 받은 시간 칸. 같은 번호를 다시 쓰지 못하게 한다
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "admin_otp")
public class AdminOtp {

    @Id
    @Column(length = 100)
    private String email;

    @Column(name = "secret_enc", nullable = false, length = 200)
    private String secretEnc;

    @Column(name = "enabled_at")
    private LocalDateTime enabledAt;

    @Column(name = "last_used_step", nullable = false)
    private long lastUsedStep;

    public AdminOtp(String email, String secretEnc) {
        this.email = email;
        this.secretEnc = secretEnc;
    }

    public boolean enabled() {
        return enabledAt != null;
    }
}
