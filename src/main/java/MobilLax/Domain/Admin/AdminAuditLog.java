package MobilLax.Domain.Admin;

import MobilLax.Common.Time.KoreaTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 관리자 감사 로그 (admin_audit_log 테이블, v2.11, 보안 규칙 S12)
 * - 누가(관리자) 언제 어디서(IP) 누구에게(대상 계정 ID) 무엇을 했는지 남긴다
 * - 추가만 한다: 수정자(setter)가 없고, 저장소에는 저장·조회만 있다 (AdminAuditLogRepository, AdminSecurityRegressionTests)
 */
@Getter
@NoArgsConstructor
@Entity
@Table(name = "admin_audit_log")
public class AdminAuditLog {

    /** 남기는 일의 종류 */
    public enum Action {
        OTP_SETUP_STARTED, OTP_ENABLED, OTP_VERIFIED, OTP_FAILED, RECOVERY_CODE_USED,
        USER_LIST_VIEWED, USER_DETAIL_VIEWED, USER_SUSPENDED, USER_UNSUSPENDED, LOGIN_UNLOCKED, STEP_UP_FAILED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "admin_email", nullable = false, length = 100)
    private String adminEmail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private Action action;

    /** 대상 계정 ID (없으면 null). 이메일 대신 ID 로 남겨 로그에 개인정보를 줄인다 */
    @Column(name = "target_user_id")
    private Long targetUserId;

    @Column(length = 200)
    private String detail;

    @Column(name = "client_ip", length = 64)
    private String clientIp;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public AdminAuditLog(String adminEmail, Action action, Long targetUserId, String detail, String clientIp) {
        this.adminEmail = adminEmail;
        this.action = action;
        this.targetUserId = targetUserId;
        this.detail = detail;
        this.clientIp = clientIp;
    }

    @PrePersist
    void prePersist() {
        this.createdAt = KoreaTime.now();
    }
}
