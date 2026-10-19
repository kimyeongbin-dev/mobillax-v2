package MobilLax.Common.Web;

import MobilLax.Domain.Cart.CartService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 공통 레이아웃(templates/fragments/layout.html)이 쓰는 값 (v2.5)
 * - 템플릿에서 ${@layout.cartCount()} 처럼 화면을 그릴 때만 불린다 (API 요청에는 비용이 들지 않음)
 * - 컨트롤러마다 모델에 넣지 않아도 모든 화면의 헤더가 같은 정보를 보여 준다
 */
@Component("layout")
@RequiredArgsConstructor
public class LayoutInfo {

    private final CartService cartService;

    /** 로그인한 사용자인지 (헤더 메뉴 분기) */
    public boolean loggedIn() {
        return CurrentUser.email().isPresent();
    }

    /** 헤더의 장바구니 묶음 수 */
    public int cartCount() {
        return CurrentUser.email().map(cartService::getCartGroupCount).orElse(0);
    }

    /** 로그인 세션이 비활성으로 유지되는 시간(초). 로그인하지 않았거나 세션이 없으면 0 (타이머를 그리지 않음) */
    public int sessionTimeoutSeconds() {
        if (!loggedIn() || !(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs)) {
            return 0;
        }
        HttpServletRequest request = attrs.getRequest();
        HttpSession session = request.getSession(false);
        return session == null ? 0 : Math.max(session.getMaxInactiveInterval(), 0);
    }
}
