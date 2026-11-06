package MobilLax.Domain.Account;

import MobilLax.Common.Error.UserFacingException;
import MobilLax.Common.RateLimit.RateLimiter;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 로그인한 사람이 지금 비밀번호를 아는지 확인한다 (v2.5, v2.14, 보안 규칙 S11·S14)
 * 비밀번호 변경과 재확인 화면이 같은 횟수 제한을 쓴다: 세션을 잠깐 빌린 사람이 대입하지 못하게
 */
@Service
@RequiredArgsConstructor
public class PasswordConfirmService {

    private final UserAccountRepository accounts;
    private final PasswordEncoder passwordEncoder;
    private final PasswordAttemptGuard attemptGuard;

    public void verify(String email, String password) {
        UserAccount account = accounts.findByEmail(email).orElseThrow();
        RateLimiter.Decision attempt = attemptGuard.checkCurrentPassword(email);
        if (!attempt.allowed()) {
            throw new UserFacingException("현재 비밀번호를 여러 번 틀렸습니다. " + attempt.retryAfterText() + " 뒤에 다시 시도해 주세요.");
        }
        if (!passwordEncoder.matches(password, account.getPassword())) {
            attemptGuard.currentPasswordFailed(email);
            throw new UserFacingException("현재 비밀번호가 맞지 않습니다.");
        }
        attemptGuard.currentPasswordMatched(email);
    }
}
