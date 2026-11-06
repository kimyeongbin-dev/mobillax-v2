package MobilLax.Common.Web;

import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 로그인 세션 연장 (v2.5) — 화면의 세션 타이머 "연장" 버튼이 부른다
 * - 로그인한 사용자만 (/api/** 는 로그인 필요, 만료됐으면 401 → 화면이 "만료"로 바꾼다)
 * - 요청 자체가 세션 마지막 사용 시각을 갱신하므로, 남은 시간을 다시 세도록 만료까지 남은 초만 돌려준다
 * - 최대 시간(S14)은 연장되지 않는다. 가까워지면 비활동 시간보다 짧은 값이 간다
 */
@RestController
public class SessionApiController {

    @GetMapping("/api/session")
    public Map<String, Integer> extend(HttpSession session) {
        return Map.of("timeoutSeconds", SessionLifetime.remainingSeconds(session));
    }
}
