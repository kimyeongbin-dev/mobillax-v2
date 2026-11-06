package MobilLax.Common.Web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * @RecentPassword 가 붙은 요청을 비밀번호 재확인 뒤에만 통과시킨다 (v2.14, 보안 규칙 S14)
 * 화면 요청은 확인 화면으로 보내고, fetch 요청은 화면이 확인 창을 열 수 있게 403 JSON 으로 알린다
 */
@Component
public class RecentPasswordInterceptor implements HandlerInterceptor {

    public static final String CONFIRM_PAGE = "/mypage/confirm-password";

    private static final String REQUIRED_JSON = "{\"error\":\"비밀번호를 다시 확인해 주세요.\",\"reauthRequired\":true}";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        if (!(handler instanceof HandlerMethod method)) {
            return true;
        }
        RecentPassword rule = method.getMethodAnnotation(RecentPassword.class);
        if (rule == null || RecentPasswordSession.isRecent(request.getSession(false))) {
            return true;
        }
        if (rule.returnTo().isEmpty()) {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write(REQUIRED_JSON);
        } else {
            response.sendRedirect(request.getContextPath() + CONFIRM_PAGE + "?next=" + rule.returnTo());
        }
        return false;
    }
}
