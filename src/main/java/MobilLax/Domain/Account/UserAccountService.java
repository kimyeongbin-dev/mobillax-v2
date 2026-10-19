package MobilLax.Domain.Account;

import MobilLax.Common.Error.UserFacingException;
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

    private static final String DEFAULT_ROLE = "USER";

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;

    /** @throws UserFacingException 이미 가입한 이메일 */
    @Transactional
    public void registerUser(String name, String email, String password) {
        if (userAccountRepository.existsByEmail(email)) {
            throw new UserFacingException("이 이메일은 이미 사용 중입니다.");
        }
        UserAccount userAccount = new UserAccount();
        userAccount.setName(name);
        userAccount.setEmail(email);
        userAccount.setPassword(passwordEncoder.encode(password));
        userAccount.setRole(DEFAULT_ROLE);
        userAccountRepository.save(userAccount);
    }
}
