package MobilLax.Common.Web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 지금 요청의 로그인 사용자 (공통 계층에서만 SecurityContextHolder 를 읽는다)
 * - 컨트롤러는 Principal 로 이메일을 받아 서비스에 넘긴다. 서비스는 보안 컨텍스트를 직접 읽지 않는다 (ArchitectureRulesTests)
 * - 레이아웃처럼 요청 매개변수를 받을 수 없는 공통 코드만 이 클래스를 쓴다
 * - 로그인 뒤 권한을 더하는 일(관리자 OTP 확인, v2.11)도 여기서만 한다
 */
public final class CurrentUser {

    private static final HttpSessionSecurityContextRepository CONTEXT_REPOSITORY = new HttpSessionSecurityContextRepository();

    private CurrentUser() {
    }

    /** 로그인한 사용자의 이메일. 로그인하지 않았으면 비어 있다 */
    public static Optional<String> email() {
        return authentication().map(Authentication::getName);
    }

    /** 지금 사용자가 이 권한(ROLE_ 포함 이름)을 가졌는지 */
    public static boolean hasAuthority(String authority) {
        return authentication().map(auth -> auth.getAuthorities().stream()
                .anyMatch(granted -> granted.getAuthority().equals(authority))).orElse(false);
    }

    /**
     * 로그인한 사용자에게 권한 하나를 더하고 세션에 저장한다 (보안 규칙 S12: 관리자는 OTP 확인 뒤에만 ROLE_ADMIN)
     * 권한이 바뀌므로 세션 ID 를 먼저 바꾼다 (바꾸기 전 세션 ID 를 알던 사람이 높아진 권한을 쓰지 못하게)
     */
    public static void grant(String authority, HttpServletRequest request, HttpServletResponse response) {
        Authentication current = authentication().orElseThrow(() -> new IllegalStateException("로그인하지 않았습니다"));
        List<GrantedAuthority> authorities = new ArrayList<>(current.getAuthorities());
        authorities.add(new SimpleGrantedAuthority(authority));
        UsernamePasswordAuthenticationToken upgraded =
                UsernamePasswordAuthenticationToken.authenticated(current.getPrincipal(), null, authorities);
        upgraded.setDetails(current.getDetails());

        request.changeSessionId();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(upgraded);
        SecurityContextHolder.setContext(context);
        CONTEXT_REPOSITORY.saveContext(context, request, response);
    }

    private static Optional<Authentication> authentication() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return Optional.empty();
        }
        return Optional.of(auth);
    }
}
