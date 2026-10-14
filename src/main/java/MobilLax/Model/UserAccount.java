/**
 * UserAccount.java
 *
 * ✅ 파일 목적: 사용자 계정 정보를 저장하는 JPA 엔티티 클래스이며,
 *              MySQL의 'users' 테이블과 매핑되어 회원가입 및 인증에 사용됩니다.
 *
 * 작성자: 김영빈
 * 마지막 수정일: 2025-05-11
 */

package MobilLax.Model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

/**
 * ✅ 클래스 설명:
 * 사용자 계정 정보(UserAccount)를 표현하는 JPA Entity 클래스입니다.
 * 해당 클래스는 MySQL의 `users` 테이블과 매핑되며,
 * 이메일, 이름, 비밀번호, 역할 등의 기본 정보를 저장합니다.
 */
@Getter
@Setter
@Entity
@Table(name = "users")
public class UserAccount {

    /**
     * 사용자 고유 식별자 (Primary Key)
     * - AUTO_INCREMENT (MySQL 기준)
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 사용자 이메일
     * - 로그인 ID로 사용됨
     * - 고유(Unique) 값
     */
    @Column(nullable = false, unique = true)
    private String email;

    /**
     * 사용자 실명
     */
    @Column(nullable = false)
    private String name;

    /**
     * 비밀번호 (암호화 저장)
     */
    @Column(nullable = false)
    private String password;

    /**
     * 사용자 권한/역할
     * 예: "USER", "ADMIN"
     */
    @Column(nullable = false)
    private String role;

    /**
     * 계정 생성 시각
     * - 최초 생성 시 자동 설정
     * - 이후 수정 불가
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * ✅ 생성일 자동 설정 메서드
     * 엔티티가 persist 되기 전에 현재 시간을 createdAt에 자동 저장합니다.
     */
    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
