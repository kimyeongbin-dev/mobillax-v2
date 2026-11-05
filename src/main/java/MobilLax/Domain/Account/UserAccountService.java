package MobilLax.Domain.Account;

import MobilLax.Common.Error.UserFacingException;
import MobilLax.Common.RateLimit.ClientKey;
import MobilLax.Common.RateLimit.RateLimitPolicy;
import MobilLax.Common.RateLimit.RateLimiter;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원가입 업무 규칙. 이름·이메일·비밀번호 형식은 RegisterForm 의 제약으로 컨트롤러 입구에서 검사한다 (S10)
 * 로그인은 Spring Security 폼 로그인이 AccountUserDetailsService 로 처리한다
 */
@Service
@RequiredArgsConstructor
public class UserAccountService {


    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final RateLimiter rateLimiter;

    /** @throws UserFacingException 한 곳(IP)에서 가입을 너무 많이 시도함(S11), 이미 가입한 이메일 */
    @Transactional
    public void registerUser(String name, String email, String password, ClientKey client) {
        RateLimiter.Decision decision = rateLimiter.acquire(RateLimitPolicy.REGISTER, client);
        if (!decision.allowed()) {
            throw new UserFacingException(RateLimitPolicy.REGISTER.message(decision, client.member()));
        }
        if (userAccountRepository.existsByEmail(email)) {
            throw new UserFacingException("이 이메일은 이미 사용 중입니다.");
        }
        UserAccount userAccount = new UserAccount();
        userAccount.setName(name);
        userAccount.setEmail(email);
        userAccount.setPassword(passwordEncoder.encode(password));
        userAccount.setRole(AccountRoles.USER);
        userAccountRepository.save(userAccount);
    }

    /** 관리자가 이용을 정지한 계정인지 (로그인 성공 직후 확인, v2.11) */
    @Transactional(readOnly = true)
    public boolean isSuspended(String email) {
        return userAccountRepository.findByEmail(email).map(account -> account.getSuspendedAt() != null).orElse(false);
    }

    /** 로그인한 사용자의 계정 ID. 다른 기능은 이메일 대신 이 ID 로 기록을 묶는다 (v2.12 문의) */
    @Transactional(readOnly = true)
    public Long idOf(String email) {
        return userAccountRepository.findByEmail(email).map(UserAccount::getId).orElseThrow();
    }
}
