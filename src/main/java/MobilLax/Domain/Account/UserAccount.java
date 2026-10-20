package MobilLax.Domain.Account;

import MobilLax.Common.Time.KoreaTime;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 회원 계정 (users 테이블). 이메일이 로그인 ID 이고, 비밀번호는 BCrypt 해시로만 저장한다
 */
@Getter
@Setter
@Entity
@Table(name = "users")
public class UserAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 로그인 ID (고유) */
    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String name;

    /** BCrypt 해시 */
    @Column(nullable = false)
    private String password;

    /** 권한 (AccountRoles.USER, ADMIN). ADMIN 은 ADMIN_EMAIL 로만 지정된다 (AdminBootstrap) */
    @Column(nullable = false)
    private String role;

    /** 관리자가 이용을 정지한 시각 (v2.11). 비어 있으면 정상 */
    @Column(name = "suspended_at")
    private LocalDateTime suspendedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = KoreaTime.now();
    }
}
