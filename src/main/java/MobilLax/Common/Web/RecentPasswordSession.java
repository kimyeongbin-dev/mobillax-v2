package MobilLax.Common.Web;

import MobilLax.Common.Time.KoreaTime;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import java.time.Duration;
import java.time.LocalDateTime;

/** 비밀번호를 다시 확인한 시각을 세션에 둔다 (v2.14, 보안 규칙 S14). 묶음 결제의 결제창마다 다시 묻지 않도록 5분간 유효하다 */
public final class RecentPasswordSession {

    public static final Duration VALID_FOR = Duration.ofMinutes(5);

    public static final String CONFIRMED_AT = "mobillax.password.confirmedAt";

    private RecentPasswordSession() {
    }

    /** 확인 표시를 남긴다. 세션의 힘이 커지므로 세션 ID 를 먼저 바꾼다 (CurrentUser.grant 와 같은 이유) */
    public static void mark(HttpServletRequest request) {
        request.getSession();
        request.changeSessionId();
        request.getSession().setAttribute(CONFIRMED_AT, KoreaTime.now());
    }

    static boolean isRecent(HttpSession session) {
        return session != null
                && session.getAttribute(CONFIRMED_AT) instanceof LocalDateTime confirmedAt
                && KoreaTime.now().isBefore(confirmedAt.plus(VALID_FOR));
    }
}
