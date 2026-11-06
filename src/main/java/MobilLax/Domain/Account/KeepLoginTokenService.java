package MobilLax.Domain.Account;

import MobilLax.Common.Time.KoreaTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

/**
 * 로그인 유지 토큰의 규칙 (v2.14, 보안 규칙 S14)
 * - 14일 동안 쓰지 않거나, 비밀번호로 로그인한 지 30일이 지나면 끝난다
 * - 쓸 때마다 토큰을 바꾼다. 이미 바뀐 옛 토큰이 나중에 오면 쿠키를 빼앗긴 것으로 보고 그 계정의 로그인 유지를 모두 끝낸다
 * - 관리자는 쓰지 못한다(S12의 OTP 를 건너뛰지 않게). 정지·탈퇴한 계정도 쓰지 못한다
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KeepLoginTokenService {

    static final Duration IDLE_LIMIT = Duration.ofDays(14);
    static final Duration MAX_LIFETIME = Duration.ofDays(30);

    /** 옛 토큰을 도난으로 보지 않는 시간: 세션이 끝난 순간 화면이 동시에 보낸 요청들 */
    static final Duration ROTATION_GRACE = Duration.ofSeconds(30);

    private static final int SERIES_BYTES = 16;
    private static final int TOKEN_BYTES = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final KeepLoginTokenRepository tokens;
    private final UserAccountRepository accounts;

    /** 쿠키에 넣을 값 */
    public record Issued(String series, String token) {
    }

    /**
     * 토큰을 쓴 결과
     *
     * @param nextToken 쿠키를 바꿀 새 토큰. 다른 동시 요청이 이미 바꿨으면 null
     */
    public record Used(String email, String nextToken) {
    }

    /** 비밀번호 로그인 때 만든다. 쓸 수 없는 계정이면 비어 있다 */
    @Transactional
    public Optional<Issued> issue(String email) {
        LocalDateTime now = KoreaTime.now();
        tokens.deleteExpired(now.minus(IDLE_LIMIT), now.minus(MAX_LIFETIME));
        return accounts.findByEmail(email).filter(KeepLoginTokenService::eligible).map(account -> {
            String token = random(TOKEN_BYTES);
            String series = random(SERIES_BYTES);
            tokens.save(new KeepLoginToken(series, account.getId(), hash(token), now));
            return new Issued(series, token);
        });
    }

    /** 쿠키의 값을 확인하고 토큰을 바꾼다. 받아들일 수 없으면 비어 있다 */
    @Transactional
    public Optional<Used> use(String series, String presented) {
        Optional<KeepLoginToken> found = tokens.findById(series);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        KeepLoginToken token = found.get();
        LocalDateTime now = KoreaTime.now();
        Optional<UserAccount> account = accounts.findById(token.getUserId()).filter(KeepLoginTokenService::eligible);
        if (account.isEmpty() || expired(token, now)) {
            tokens.delete(token);
            return Optional.empty();
        }
        String presentedHash = hash(presented);
        String email = account.get().getEmail();
        if (same(presentedHash, token.getTokenHash())) {
            String next = random(TOKEN_BYTES);
            boolean rotated = tokens.rotate(series, presentedHash, hash(next), now) == 1;
            return Optional.of(new Used(email, rotated ? next : null));
        }
        if (same(presentedHash, token.getPreviousHash()) && now.isBefore(token.getLastUsedAt().plus(ROTATION_GRACE))) {
            return Optional.of(new Used(email, null));
        }
        log.warn("로그인 유지 토큰을 다시 쓴 요청을 막고 그 계정의 로그인 유지를 끝냈습니다.");
        tokens.deleteByUserId(token.getUserId());
        return Optional.empty();
    }

    /** 로그아웃: 이 브라우저만 끝낸다 */
    @Transactional
    public void revoke(String series) {
        tokens.deleteById(series);
    }

    /** 비밀번호 변경·탈퇴: 모든 브라우저를 끝낸다 */
    @Transactional
    public void revokeAll(String email) {
        accounts.findByEmail(email).ifPresent(account -> tokens.deleteByUserId(account.getId()));
    }

    private static boolean eligible(UserAccount account) {
        return account.getSuspendedAt() == null && !AccountRoles.ADMIN.equals(account.getRole());
    }

    private static boolean expired(KeepLoginToken token, LocalDateTime now) {
        return !now.isBefore(token.getLastUsedAt().plus(IDLE_LIMIT)) || !now.isBefore(token.getCreatedAt().plus(MAX_LIFETIME));
    }

    private static boolean same(String a, String b) {
        return b != null && MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    private static String random(int bytes) {
        byte[] value = new byte[bytes];
        RANDOM.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
