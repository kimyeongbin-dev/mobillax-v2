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
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.AnyRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
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

    /** 로그인 없이 열리는 화면·정적 파일 (API 는 모두 로그인 필요: 비로그인 사용 허용은 호출 횟수 제한과 함께 나중에) */
    static final String[] PUBLIC_PATHS = {
            "/", "/home", "/login", "/register", "/error",
            "/css/**", "/javascript/**", "/images/**",
            // 헬스체크: 컨테이너가 로그인 없이 확인한다. 응답은 UP/DOWN 뿐 (보안 규칙 S7)
            "/health", "/health/live"
    };

    /** 화면이 아니라 fetch 로 부르는 경로: 로그인이 안 되어 있으면 로그인 페이지로 보내지 않고 401 JSON */
    static final String[] API_PATHS = {"/api/**", "/payment/**"};

    /**
     * 보안 필터 체인 — 규칙은 SecurityRegressionTests 가 지킨다
     * - 화면·정적 파일만 공개, 모든 API·결제는 로그인 필요
     * - CSRF 보호: 폼은 Thymeleaf 가 _csrf 를 넣고, fetch 는 XSRF-TOKEN 쿠키 값을 X-XSRF-TOKEN 헤더로 보낸다 (csrf.js)
     * - 로그아웃은 POST 만 (CSRF 보호를 켜면 GET /logout 으로는 로그아웃되지 않는다)
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()));
        // 토큰은 처음 쓰일 때 만들어지므로, 폼이 없는 화면에서도 fetch 가 쓸 수 있게 매 요청 쿠키를 내려보낸다
        http.addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class);

        http.authorizeHttpRequests(authorize -> authorize
                .requestMatchers(PUBLIC_PATHS).permitAll()
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .requestMatchers("/user/**").hasRole("USER")
                .anyRequest().authenticated());

        http.formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/login")
                .usernameParameter("email")
                .passwordParameter("password")
                .defaultSuccessUrl("/home", true)
                .permitAll());

        http.logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login?logout")
                .permitAll());

        AntPathRequestMatcher[] apiMatchers = java.util.Arrays.stream(API_PATHS)
                .map(AntPathRequestMatcher::new).toArray(AntPathRequestMatcher[]::new);
        http.exceptionHandling(e -> e
                .defaultAuthenticationEntryPointFor(new JsonUnauthorizedEntryPoint(), new OrRequestMatcher(apiMatchers))
                .defaultAuthenticationEntryPointFor(new LoginUrlAuthenticationEntryPoint("/login"), AnyRequestMatcher.INSTANCE));

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


    /** 지연 생성되는 CSRF 토큰을 읽어 XSRF-TOKEN 쿠키가 항상 내려가게 한다 */
    static class CsrfCookieFilter extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
            if (token != null) {
                token.getToken();
            }
            chain.doFilter(request, response);
        }
    }

    /** API 인증 실패: 로그인 페이지로 리다이렉트하지 않고 401 JSON */
    static class JsonUnauthorizedEntryPoint implements AuthenticationEntryPoint {
        @Override
        public void commence(HttpServletRequest request, HttpServletResponse response,
                             AuthenticationException authException) throws IOException {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"error\":\"로그인이 필요합니다.\"}");
        }
    }
}
