package MobilLax.Config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
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
 * 보안 설정: 공개 경로, 로그인·로그아웃, CSRF, API 인증 실패 응답 (보안 규칙 S2·S3, SecurityRegressionTests)
 * 로그인 계정 조회는 AccountUserDetailsService(UserDetailsService 빈)를 Spring Security 가 자동으로 쓴다
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /** 비밀번호 해시: BCrypt */
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
