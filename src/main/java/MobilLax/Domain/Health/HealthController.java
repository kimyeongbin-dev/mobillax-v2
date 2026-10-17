package MobilLax.Domain.Health;

import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 헬스체크 (보안 규칙 S7) — 로그인 없이 열리고, 응답은 {"status": "UP" | "DOWN"} 뿐이다
 * - GET /health      : DB + 외부 API(TMAP·TAGO·카카오·도로명주소·PortOne). 하나라도 이상이면 503
 * - GET /health/live : DB 만. 컨테이너 헬스체크용 (외부 API 장애·TMAP 하루 한도 소진으로 앱을 재시작하지 않도록)
 */
@RestController
@RequestMapping("/health")
@RequiredArgsConstructor
public class HealthController {

    private final HealthService healthService;

    @GetMapping
    public ResponseEntity<Map<String, String>> health() {
        return respond(healthService.isUp());
    }

    @GetMapping("/live")
    public ResponseEntity<Map<String, String>> live() {
        return respond(healthService.isDbUp());
    }

    private static ResponseEntity<Map<String, String>> respond(boolean up) {
        return ResponseEntity.status(up ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE)
                .cacheControl(CacheControl.noStore())
                .body(Map.of("status", up ? "UP" : "DOWN"));
    }
}
