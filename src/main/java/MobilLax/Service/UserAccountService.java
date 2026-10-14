/**
 * UserAccountService.java
 *
 * ✅ 파일 목적: 사용자 회원가입 및 로그인 처리를 담당하는 서비스 클래스입니다.
 *              사용자 계정 관련 핵심 로직을 캡슐화하여 Controller와 분리된 계층으로 관리합니다.
 *
 * 작성자: 김영빈
 * 마지막 수정일: 2025-05-11
 */

package MobilLax.Service;

import MobilLax.Model.UserAccount;
import MobilLax.Repository.UserAccountRepository;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ✅ 클래스 설명:
 * 사용자 계정 생성(회원가입)과 로그인 인증 기능을 제공하는 서비스 클래스입니다.
 * Spring Security의 AuthenticationManager를 통해 로그인 처리를 수행하며,
 * 이메일 유효성 검사 및 중복 체크 등 회원가입 검증 로직을 포함합니다.
 */
@Service
public class UserAccountService  {

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;

    /**
     * 생성자 주입
     *
     * @param userAccountRepository 사용자 계정 저장소
     * @param passwordEncoder 비밀번호 암호화 도구
     * @param authenticationManager Spring Security 인증 관리자
     */
    public UserAccountService(UserAccountRepository userAccountRepository,
                              PasswordEncoder passwordEncoder,
                              AuthenticationManager authenticationManager) {
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
    }

    /**
     * ✅ 회원가입 처리 메서드
     *
     * 사용자의 이름, 이메일, 비밀번호를 기반으로 회원가입을 수행합니다.
     * - 이메일 중복 확인
     * - 이메일 형식 검증
     * - 비밀번호 암호화 후 저장
     *
     * @param name 사용자 이름
     * @param email 사용자 이메일 (아이디)
     * @param password 사용자 비밀번호 (암호화됨)
     * @throws IllegalArgumentException 이메일 중복 또는 형식 오류 발생 시
     */
    @Transactional
    public void registerUser(String name, String email, String password) {
        if (userAccountRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("이 이메일은 이미 사용 중입니다.");
        }

        if (!email.matches("^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$")) {
            throw new IllegalArgumentException("유효하지 않은 이메일 형식입니다.");
        }

        String encodedPassword = passwordEncoder.encode(password);

        UserAccount userAccount = new UserAccount();
        userAccount.setName(name);
        userAccount.setEmail(email);
        userAccount.setPassword(encodedPassword);
        userAccount.setRole("USER");

        userAccountRepository.save(userAccount);
    }

    /**
     * ✅ 로그인 처리 메서드
     *
     * 주어진 이메일과 비밀번호로 인증을 시도합니다.
     * Spring Security의 AuthenticationManager를 통해 인증하며, 실패 시 예외를 발생시킵니다.
     *
     * @param email 사용자 이메일
     * @param password 사용자 비밀번호
     * @throws IllegalArgumentException 이메일 또는 비밀번호가 틀렸을 경우
     */
    public void login(String email, String password) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, password)
            );
            // 로그인 성공 시 후속 처리 로직(예: 토큰 생성 등)을 여기에 작성할 수 있습니다.
        } catch (AuthenticationException e) {
            throw new IllegalArgumentException("이메일 또는 비밀번호가 잘못되었습니다.");
        }
    }
}
