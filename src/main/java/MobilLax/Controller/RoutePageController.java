package MobilLax.Controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class RoutePageController {
    @GetMapping("/route")
    public String routepage() {
        return "route";
    }

    /** 요금비교: 마지막 검색 결과(브라우저 sessionStorage)를 화면에서 비교한다. 서버 호출 없음 */
    @GetMapping("/compare")
    public String compare() {
        return "compare";
    }
}
