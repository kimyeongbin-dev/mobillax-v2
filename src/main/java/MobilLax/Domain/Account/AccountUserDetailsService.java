package MobilLax.Domain.Account;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * 폼 로그인에서 이메일로 계정을 찾는다 (Spring Security 가 비밀번호 해시를 비교)
 * - 없는 계정의 예외 문구는 로그인 화면에 나가지 않는다. 화면은 "이메일 또는 비밀번호가 잘못되었습니다."만 보인다
 * - 없는 계정은 반드시 UsernameNotFoundException 으로 알린다. 그래야 Spring Security 가 같은 비용의 가짜 해시와
 *   한 번 비교해, 응답 시간으로 가입한 이메일을 골라낼 수 없다 (보안 규칙 S11, LoginTimingRegressionTests)
 */
@Service
@RequiredArgsConstructor
public class AccountUserDetailsService implements UserDetailsService {

    private final UserAccountRepository userAccountRepository;

    @Override
    public UserDetails loadUserByUsername(String email) {
        UserAccount account = userAccountRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("계정을 찾을 수 없습니다."));
        // 관리자도 비밀번호만으로는 관리자 권한을 받지 않는다. OTP 확인 뒤 ADMIN 을 더한다 (보안 규칙 S12)
        String[] roles = AccountRoles.ADMIN.equals(account.getRole())
                ? new String[]{AccountRoles.USER, AccountRoles.ADMIN_PENDING}
                : new String[]{AccountRoles.USER};
        return User.builder()
                .username(account.getEmail())
                .password(account.getPassword())
                .roles(roles)
                .build();
    }
}
