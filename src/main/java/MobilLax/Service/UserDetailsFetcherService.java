/**
 * UserDetailsFetcherService.java
 *
 * ✅ 파일 목적: Spring Security 인증 과정에서 사용자 정보를 이메일 기준으로 조회하여
 *              UserDetails 객체로 반환하는 서비스 클래스입니다.
 *              DB에 저장된 사용자 정보를 기반으로 로그인 처리를 지원합니다.
 *
 * 작성자: 김영빈
 * 마지막 수정일: 2025-05-11
 */

package MobilLax.Service;

import MobilLax.Model.UserAccount;
import MobilLax.Repository.UserAccountRepository;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;

/**
 * ✅ 클래스 설명:
 * UserDetailsService 인터페이스를 구현한 서비스로,
 * Spring Security에서 로그인 시 사용자 정보를 이메일로 조회하여 인증 처리를 담당합니다.
 */
@Service
public class UserDetailsFetcherService implements UserDetailsService {

    // 사용자 정보를 DB에서 조회하기 위한 Repository
    private final UserAccountRepository userAccountRepository;

    /**
     * 생성자 주입
     * @param userAccountRepository 사용자 정보를 조회하는 JPA Repository
     */
    public UserDetailsFetcherService(UserAccountRepository userAccountRepository) {
        this.userAccountRepository = userAccountRepository;
    }

    /**
     * 이메일(username)을 기반으로 사용자 정보를 조회하여 UserDetails 객체를 생성합니다.
     * Spring Security의 인증 처리 시 호출됩니다.
     *
     * @param username 로그인 시 입력한 이메일 주소
     * @return UserDetails 객체 (Spring Security에서 사용)
     * @throws UsernameNotFoundException 사용자가 존재하지 않을 경우 예외 발생
     */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        UserAccount userAccount = userAccountRepository.findByEmail(username)
                .orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다: " + username));

        return User.builder()
                .username(userAccount.getEmail())
                .password(userAccount.getPassword())
                .roles(userAccount.getRole())
                .build();
    }
}
