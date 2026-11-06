package MobilLax.Common.Web;

import MobilLax.Common.Time.KoreaTime;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;

/**
 * 로그인 세션의 최대 시간 (v2.14, 보안 규칙 S14)
 * 비활동 시간(server.servlet.session.timeout)과 따로 센다: 계속 쓰고 있어도 로그인 후 이 시간이 지나면 세션이 끝난다
 */
public final class SessionLifetime {

    public static final Duration MAX = Duration.ofHours(4);

    /** 로그인 유지 쿠키 이름. 값 검사는 KeepLoginServices 가 하고, 여기서는 화면 표시에만 쓴다 */
    public static final String KEEP_LOGIN_COOKIE = "remember-me";

    public static final String STARTED_AT = "mobillax.login.startedAt";

    private SessionLifetime() {
    }

    static boolean started(HttpSession session) {
        return session.getAttribute(STARTED_AT) != null;
    }

    static void start(HttpSession session) {
        session.setAttribute(STARTED_AT, KoreaTime.now());
    }

    static boolean expired(HttpSession session) {
        return session.getAttribute(STARTED_AT) instanceof LocalDateTime startedAt
                && !KoreaTime.now().isBefore(startedAt.plus(MAX));
    }

    /** 활동이 없을 때 세션이 끝나기까지 남은 초: 비활동 시간과 최대 시간 중 먼저 오는 쪽 */
    public static int remainingSeconds(HttpSession session) {
        int idle = Math.max(session.getMaxInactiveInterval(), 0);
        if (session.getAttribute(STARTED_AT) instanceof LocalDateTime startedAt) {
            long untilMax = Duration.between(KoreaTime.now(), startedAt.plus(MAX)).toSeconds();
            return (int) Math.max(0, Math.min(idle, untilMax));
        }
        return idle;
    }

    /** 로그인 유지 쿠키가 있는지 (세션이 끝나도 다시 로그인되므로 남은 시간을 보이지 않는다) */
    public static boolean keepLoginRequested(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        return cookies != null && Arrays.stream(cookies).anyMatch(cookie -> KEEP_LOGIN_COOKIE.equals(cookie.getName()));
    }
}
