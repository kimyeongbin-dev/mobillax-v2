package MobilLax.Common.Web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * 로그인 세션의 최대 시간을 지킨다 (v2.14, 보안 규칙 S14)
 * - 로그인 유지 필터보다 먼저 돈다: 세션을 끝낸 요청에 유효한 로그인 유지 쿠키가 있으면 바로 다시 로그인된다
 * - 세션을 처음 볼 때 세션 등록부에 넣는다: 로그인 유지로 만든 세션은 Spring 이 넣어 주지 않아,
 *   넣지 않으면 관리자가 계정을 정지해도 그 세션을 끝낼 수 없다 (S12)
 */
@RequiredArgsConstructor
public class SessionLifetimeFilter extends OncePerRequestFilter {

    private final SessionRegistry sessionRegistry;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        Optional<Object> principal = CurrentUser.principal();
        if (session != null && principal.isPresent()) {
            if (SessionLifetime.expired(session)) {
                session.invalidate();
                CurrentUser.clear();
            } else if (!SessionLifetime.started(session)) {
                SessionLifetime.start(session);
                if (sessionRegistry.getSessionInformation(session.getId()) == null) {
                    sessionRegistry.registerNewSession(session.getId(), principal.get());
                }
            }
        }
        chain.doFilter(request, response);
    }
}
