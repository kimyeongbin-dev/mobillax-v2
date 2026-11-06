package MobilLax.Domain.Account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 브라우저 하나의 로그인 유지 (v2.14, 보안 규칙 S14)
 * 토큰은 해시만 저장한다: DB 를 읽은 사람도 쿠키를 만들 수 없다
 * previousHash 는 바꾼 직전 토큰: 세션이 끝난 순간 화면이 보낸 여러 요청이 옛 쿠키를 함께 들고 와도 도난으로 보지 않으려고 둔다
 */
@Entity
@Table(name = "keep_login_token", indexes = @Index(name = "idx_keep_login_user", columnList = "user_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class KeepLoginToken {

    @Id
    @Column(length = 32)
    private String series;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "previous_hash", length = 64)
    private String previousHash;

    /** 비밀번호로 로그인한 시각. 최대 기간은 여기서부터 센다 */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /** 토큰을 마지막으로 쓴 시각. 비활동 기간은 여기서부터 센다 */
    @Column(name = "last_used_at", nullable = false)
    private LocalDateTime lastUsedAt;

    KeepLoginToken(String series, Long userId, String tokenHash, LocalDateTime now) {
        this.series = series;
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.createdAt = now;
        this.lastUsedAt = now;
    }
}
