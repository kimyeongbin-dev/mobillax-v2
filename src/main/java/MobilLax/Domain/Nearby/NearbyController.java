package MobilLax.Domain.Nearby;

import MobilLax.Common.RateLimit.RateLimitPolicy;
import MobilLax.Common.RateLimit.RateLimited;
import MobilLax.Common.Usage.CountsUsage;
import MobilLax.Common.Usage.UsageMetric;
import MobilLax.Common.Validation.KoreanLatitude;
import MobilLax.Common.Validation.KoreanLongitude;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;

/**
 * 주변정보 화면과 조회 API
 * - 좌표는 국내 범위, 분류는 정해진 값(enum)만 받는다 (보안 규칙 S10). 틀리면 400
 */
@Controller
@RequiredArgsConstructor
public class NearbyController {

    private final NearbyService nearbyService;

    /** 분류 버튼은 서버의 분류 목록(NearbyCategory)으로 그린다: 화면과 서버가 다른 목록을 갖지 않게 */
    @GetMapping("/nearby")
    public String page(Model model) {
        model.addAttribute("categories", NearbyCategory.values());
        return "nearby";
    }

    @RateLimited(RateLimitPolicy.EXTERNAL_LOOKUP)
    @CountsUsage(UsageMetric.NEARBY_SEARCH)
    @GetMapping("/api/nearby")
    @ResponseBody
    public List<NearbyPlaceView> search(@RequestParam NearbyCategory category,
                                        @RequestParam @KoreanLatitude double lat,
                                        @RequestParam @KoreanLongitude double lon) {
        return nearbyService.find(category, lat, lon);
    }
}
