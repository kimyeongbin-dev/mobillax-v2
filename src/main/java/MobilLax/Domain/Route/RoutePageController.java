package MobilLax.Domain.Route;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 경로 화면들: 홈(검색), 상세, 요금비교
 * - 홈은 로그인하지 않아도 열린다. 검색 API·상세·요금비교는 로그인 필요 (보안 규칙 S2)
 */
@Controller
public class RoutePageController {

    @GetMapping({"/", "/home"})
    public String home() {
        return "home";
    }

    @GetMapping("/route")
    public String route() {
        return "route";
    }

    /** 요금비교: 마지막 검색 결과(브라우저 sessionStorage)를 화면에서 비교한다. 서버 호출 없음 */
    @GetMapping("/compare")
    public String compare() {
        return "compare";
    }
}
