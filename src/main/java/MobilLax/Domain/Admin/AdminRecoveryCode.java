package MobilLax.Domain.Admin;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 관리자 OTP 복구 코드 (admin_recovery_code 테이블, v2.11)
 * - 휴대폰을 잃어버렸을 때 OTP 대신 한 번만 쓰는 코드. 원문은 등록 때 한 번만 보여 주고 BCrypt 해시만 저장한다
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "admin_recovery_code")
public class AdminRecoveryCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String email;

    @Column(name = "code_hash", nullable = false, length = 100)
    private String codeHash;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    public AdminRecoveryCode(String email, String codeHash) {
        this.email = email;
        this.codeHash = codeHash;
    }
}
