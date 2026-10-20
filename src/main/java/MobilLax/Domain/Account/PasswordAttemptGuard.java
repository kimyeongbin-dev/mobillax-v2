package MobilLax.Domain.Account;

import MobilLax.Common.RateLimit.RateLimiter;
import MobilLax.Common.RateLimit.RateLimiter.Decision;
import MobilLax.Common.RateLimit.RateLimiter.Limit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Locale;

/**
 * 비밀번호 대입 막기 (보안 규칙 S11) — 틀린 횟수만 센다. 한도 값은 여기에만 둔다
 * - 로그인: 이메일마다 15분에 5번, 한 곳(IP)에서 15분에 20번 틀리면 그 칸이 끝날 때까지 비밀번호를 확인하지 않는다
 *   없는 이메일도 똑같이 센다 (잠김 여부로 가입 여부를 알 수 없게)
 * - 마이페이지 비밀번호 변경의 "현재 비밀번호": 계정마다 15분에 5번 (세션을 빌린 사람이 대입하지 못하게)
 * - 맞히면 그 계정의 실패 횟수를 지운다 (IP 실패 횟수는 지우지 않는다: 계정 하나를 알면 다른 계정을 대입할 수 있게 되므로)
 */
@Service
@RequiredArgsConstructor
public class PasswordAttemptGuard {

    static final Limit PER_ACCOUNT = new Limit(5, Duration.ofMinutes(15));
    static final Limit PER_CLIENT = new Limit(20, Duration.ofMinutes(15));

    private final RateLimiter rateLimiter;

    /** 로그인 시도를 받아도 되는지 (비밀번호를 확인하기 전에) */
    public Decision checkLogin(String email, String clientIp) {
        Decision account = rateLimiter.check(loginKey(email), PER_ACCOUNT);
        return account.allowed() ? rateLimiter.check(clientKey(clientIp), PER_CLIENT) : account;
    }

    /** 로그인 실패를 센다. 이번 실패로 잠겼으면 true */
    public boolean loginFailed(String email, String clientIp) {
        rateLimiter.record(loginKey(email), PER_ACCOUNT);
        rateLimiter.record(clientKey(clientIp), PER_CLIENT);
        return !checkLogin(email, clientIp).allowed();
    }

    public void loginSucceeded(String email) {
        rateLimiter.reset(loginKey(email));
    }

    /** 현재 비밀번호를 확인해도 되는지 */
    public Decision checkCurrentPassword(String email) {
        return rateLimiter.check(currentPasswordKey(email), PER_ACCOUNT);
    }

    public void currentPasswordFailed(String email) {
        rateLimiter.record(currentPasswordKey(email), PER_ACCOUNT);
    }

    public void currentPasswordMatched(String email) {
        rateLimiter.reset(currentPasswordKey(email));
    }

    /** 대소문자·앞뒤 공백만 다른 이메일로 한도를 피하지 못하게 */
    private static String loginKey(String email) {
        return "login:" + normalize(email);
    }

    private static String clientKey(String clientIp) {
        return "login-ip:" + clientIp;
    }

    private static String currentPasswordKey(String email) {
        return "current-password:" + normalize(email);
    }

    private static String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
