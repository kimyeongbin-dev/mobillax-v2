package MobilLax.Common.RateLimit;

import jakarta.servlet.http.HttpServletRequest;

import java.security.Principal;

/**
 * 호출 횟수를 세는 단위: 로그인했으면 계정, 아니면 접속 IP
 * - 비로그인 사용자의 Principal 은 null 이다 (Spring Security 는 익명 인증을 Principal 로 내놓지 않는다)
 * - 배포(v3)에서 프록시 뒤에 두면 접속 IP 가 프록시 주소가 된다. 그때 믿을 수 있는 프록시의 X-Forwarded-For 만 쓰도록 설정한다
 */
public record ClientKey(String value, boolean member) {

    public static ClientKey of(HttpServletRequest request) {
        Principal principal = request.getUserPrincipal();
        if (principal != null) {
            return new ClientKey("member:" + principal.getName(), true);
        }
        return new ClientKey("ip:" + request.getRemoteAddr(), false);
    }
}
