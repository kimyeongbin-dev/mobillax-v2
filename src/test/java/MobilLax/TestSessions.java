package MobilLax;

import MobilLax.Common.Time.KoreaTime;
import MobilLax.Common.Web.RecentPasswordSession;
import org.springframework.mock.web.MockHttpSession;

/** 테스트용 세션 */
final class TestSessions {

    private TestSessions() {
    }

    /** 방금 비밀번호를 다시 확인한 세션 (보안 규칙 S14): 재확인 뒤의 규칙을 검사하는 테스트가 쓴다 */
    static MockHttpSession recentPassword() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(RecentPasswordSession.CONFIRMED_AT, KoreaTime.now());
        return session;
    }
}
