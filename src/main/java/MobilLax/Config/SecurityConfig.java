/**
 * SecurityConfig.java
 *
 * ✅ 파일 목적: Spring Security의 전역 보안 설정을 정의하는 구성 클래스입니다.
 *              운영 환경 기준으로 모든 요청에 대한 인증/인가 정책을 설정하며,
 *              사용자 정의 로그인/로그아웃 처리 및 권한 기반 접근 제어를 구성합니다.
 *
 * 작성자: 김영빈
 * 마지막 수정일: 2025-05-11
 */

package MobilLax.Config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.core.AuthenticationException;
import java.io.IOException;

/**
 * ✅ 운영 환경 전용 보안 설정 클래스
 *
 * - 개발/운영 분기 없이 운영 정책 고정
 * - 경로별 접근 권한 설정
 * - 사용자 정의 로그인/로그아웃 처리
 * - CSRF 보호 비활성화(API 또는 테스트 환경 대비)
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final UserDetailsService userDetailsService;

    /**
     * 생성자 기반 의존성 주입
     *
     * @param userDetailsService 사용자 정보를 불러오기 위한 서비스 구현체
     */
    public SecurityConfig(UserDetailsService userDetailsService) {
        this.userDetailsService = userDetailsService;
    }

    /**
     * ✅ 비밀번호 인코더 설정
     *
     * BCrypt 알고리즘을 사용하여 사용자 비밀번호를 안전하게 해싱합니다.
     *
     * @return PasswordEncoder 구현체 (BCrypt 기반)
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * ✅ SecurityFilterChain 설정
     *
     * - CSRF 보호 비활성화
     * - 정적 리소스 및 인증 관련 경로는 모두 허용
     * - /admin/** → ADMIN 권한 필요
     * - /user/** → USER 권한 필요
     * - 그 외 모든 요청은 인증 필요
     * - 사용자 정의 로그인/로그아웃 페이지 설정 포함
     *
     * @param http Spring Security 보안 설정 객체
     * @return SecurityFilterChain
     * @throws Exception 보안 설정 중 예외 발생 가능성 있음
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable()); // CSRF 보호 비활성화 (API 서버이거나 필요 없는 경우)

        http.authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/", "/home", "/route", "/css/**", "/javascript/**", "/images/**",
                        "/module/**", "/login", "/member/login", "/register", "/api/**").permitAll()  // 인증 없이 접근 허용
                .requestMatchers("/admin/**").hasRole("ADMIN")         // 관리자 권한 필요
                .requestMatchers("/user/**").hasRole("USER")           // 사용자 권한 필요
                .anyRequest().authenticated());                        // 그 외 모든 요청은 인증 필요

        http.formLogin(form -> form
                .loginPage("/login")                // 사용자 정의 로그인 페이지
                .loginProcessingUrl("/login")       // 로그인 form 전송 처리 URL
                .usernameParameter("email")         // 사용자 ID 입력 필드명
                .passwordParameter("password")      // 비밀번호 입력 필드명
                .defaultSuccessUrl("/home", true)             // 로그인 성공 시 리다이렉트 경로
                .permitAll());

        http.logout(logout -> logout
                .logoutUrl("/logout")                   // 로그아웃 요청 URL
                .logoutSuccessUrl("/member/login?logout")      // 로그아웃 성공 시 이동 페이지
                .permitAll());

        http.exceptionHandling(e -> e
                .authenticationEntryPoint(new CustomAuthenticationEntryPoint())); // ✅ 이 라인 추가

        return http.build();
    }

    /**
     * ✅ 인증 관리자(AuthenticationManager) 설정
     *
     * AuthenticationManager는 로그인 인증을 처리하는 핵심 컴포넌트입니다.
     *
     * @param config AuthenticationConfiguration
     * @return AuthenticationManager 인스턴스
     * @throws Exception 구성 중 예외 발생 시
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }


    // ✅ 인증 실패 시 처리 로직
    public static class CustomAuthenticationEntryPoint implements AuthenticationEntryPoint {
        @Override
        public void commence(HttpServletRequest request, HttpServletResponse response,
                             AuthenticationException authException) throws IOException {
            String accept = request.getHeader("Accept");
            if (accept != null && accept.contains("application/json")) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"error\": \"로그인을 먼저 하셔야 합니다.\"}");
            } else {
                response.sendRedirect("/login");
            }
        }
    }
}
