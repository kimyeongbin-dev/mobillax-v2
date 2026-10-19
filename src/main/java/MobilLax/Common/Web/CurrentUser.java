package MobilLax.Common.Web;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/**
 * 지금 요청의 로그인 사용자 (공통 계층에서만 SecurityContextHolder 를 읽는다)
 * - 컨트롤러는 Principal 로 이메일을 받아 서비스에 넘긴다. 서비스는 보안 컨텍스트를 직접 읽지 않는다 (ArchitectureRulesTests)
 * - 레이아웃처럼 요청 매개변수를 받을 수 없는 공통 코드만 이 클래스를 쓴다
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    /** 로그인한 사용자의 이메일. 로그인하지 않았으면 비어 있다 */
    public static Optional<String> email() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return Optional.empty();
        }
        return Optional.of(auth.getName());
    }
}
