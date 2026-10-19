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

    /** 권한 (USER, ADMIN) */
    @Column(nullable = false)
    private String role;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = KoreaTime.now();
    }
}
