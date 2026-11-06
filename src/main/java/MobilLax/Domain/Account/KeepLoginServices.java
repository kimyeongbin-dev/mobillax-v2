package MobilLax.Domain.Account;

import MobilLax.Common.Web.SessionLifetime;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.rememberme.AbstractRememberMeServices;
import org.springframework.security.web.authentication.rememberme.InvalidCookieException;
import org.springframework.security.web.authentication.rememberme.RememberMeAuthenticationException;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

/**
 * 로그인 유지 쿠키를 Spring Security 와 잇는다 (v2.14, 보안 규칙 S14). 규칙은 KeepLoginTokenService 에 있다
 * Spring 의 PersistentTokenBasedRememberMeServices 를 쓰지 않는 이유:
 * 토큰을 DB 에 그대로 저장하고, 최대 기간이 없으며, 동시 요청을 쿠키 도난으로 오인해 사용자를 로그아웃시킨다
 */
@Component
public class KeepLoginServices extends AbstractRememberMeServices {

    private static final String REQUEST_PARAMETER = "remember-me";
    private static final Duration COOKIE_LIFETIME = KeepLoginTokenService.IDLE_LIMIT;
    private static final int COOKIE_PARTS = 2;

    private final KeepLoginTokenService tokens;

    public KeepLoginServices(UserDetailsService userDetailsService, KeepLoginTokenService tokens) {
        // 서버 안에서 인증 객체를 확인하는 값일 뿐이다. 세션이 메모리에 있어 재시작 때 바뀌어도 된다
        super(UUID.randomUUID().toString(), userDetailsService);
        this.tokens = tokens;
        setCookieName(SessionLifetime.KEEP_LOGIN_COOKIE);
        setParameter(REQUEST_PARAMETER);
        setTokenValiditySeconds((int) COOKIE_LIFETIME.toSeconds());
    }

    @Override
    protected void onLoginSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        tokens.issue(authentication.getName()).ifPresent(issued -> writeCookie(issued.series(), issued.token(), request, response));
    }

    @Override
    protected UserDetails processAutoLoginCookie(String[] cookieTokens, HttpServletRequest request, HttpServletResponse response) {
        if (cookieTokens.length != COOKIE_PARTS) {
            throw new InvalidCookieException("로그인 유지 쿠키 형식이 올바르지 않습니다.");
        }
        KeepLoginTokenService.Used used = tokens.use(cookieTokens[0], cookieTokens[1])
                .orElseThrow(() -> new RememberMeAuthenticationException("로그인 유지가 끝났습니다."));
        if (used.nextToken() != null) {
            writeCookie(cookieTokens[0], used.nextToken(), request, response);
        }
        return getUserDetailsService().loadUserByUsername(used.email());
    }

    /** 로그아웃: 이 브라우저의 로그인 유지를 서버에서도 지운다 */
    @Override
    public void logout(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        String cookie = extractRememberMeCookie(request);
        if (cookie != null) {
            try {
                String[] parts = decodeCookie(cookie);
                if (parts.length == COOKIE_PARTS) {
                    tokens.revoke(parts[0]);
                }
            } catch (InvalidCookieException e) {
                logger.debug("형식이 틀린 로그인 유지 쿠키를 지웁니다.");
            }
        }
        super.logout(request, response, authentication);
    }

    @Override
    protected void cancelCookie(HttpServletRequest request, HttpServletResponse response) {
        addCookie("", Duration.ZERO, request, response);
    }

    private void writeCookie(String series, String token, HttpServletRequest request, HttpServletResponse response) {
        addCookie(encodeCookie(new String[]{series, token}), COOKIE_LIFETIME, request, response);
    }

    private void addCookie(String value, Duration maxAge, HttpServletRequest request, HttpServletResponse response) {
        String path = request.getContextPath().isEmpty() ? "/" : request.getContextPath();
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(SessionLifetime.KEEP_LOGIN_COOKIE, value)
                .httpOnly(true).secure(request.isSecure()).sameSite("Lax").path(path).maxAge(maxAge).build().toString());
    }
}
