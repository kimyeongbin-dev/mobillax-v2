package MobilLax.Domain.Route;

import MobilLax.Common.Time.KoreaTime;
import MobilLax.Domain.Health.HealthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;


/**
 * 대중교통 경로 검색 API
 * - 브라우저 대신 서버가 TMAP 을 호출해 REST 키가 브라우저에 노출되지 않게 함
 * - 응답은 TMAP JSON 원문을 그대로 전달 (프론트의 경로 표시 코드가 TMAP 응답 형식을 사용)
 * - 오류 응답도 TMAP 과 같은 {"result": {"message": ...}} 형식으로 맞춤
 */
@Slf4j
@RestController
@RequestMapping("/api/route")
@RequiredArgsConstructor
public class RouteController {


    private final TmapTransitClient tmapClient;
    private final HealthService healthService;

    @PostMapping(value = "/transit", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> searchTransit(@RequestBody TransitRouteRequest request) {
        // 좌표(국내)·출발 일시 형식은 RequestBodyValidationAdvice 가 TransitRouteRequest 의 제약으로 먼저 검사한다
        if (!request.isDepartAtValid(KoreaTime.today())) {
            return error(HttpStatus.BAD_REQUEST,
                    "출발 일시가 올바르지 않습니다. 오늘부터 " + TransitRouteRequest.MAX_DAYS_AHEAD + "일 안에서 선택해 주세요.");
        }

        try {
            String routes = tmapClient.searchTransitRoutes(request);
            healthService.record(HealthService.Component.TMAP, true);
            return ResponseEntity.ok(routes);
        } catch (HttpStatusCodeException e) {
            log.warn("TMAP 대중교통 API 오류: {} {}", e.getStatusCode(), e.getResponseBodyAsString());
            int code = e.getStatusCode().value();
            // 헬스체크(S7): 하루 10건 한도라 따로 점검 호출을 하지 않고 실제 호출 결과로 판단. 400(검색 결과 없음 등)은 서비스 정상
            healthService.record(HealthService.Component.TMAP, code == 400);
            if (code == 400) {
                // "검색 결과 없음" 등 요청 단위 오류는 TMAP 안내 문구를 그대로 전달
                return ResponseEntity.badRequest()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(e.getResponseBodyAsString());
            }
            if (code == 429) {
                return error(HttpStatus.TOO_MANY_REQUESTS, "경로 검색 가능 횟수를 모두 사용했습니다. 잠시 후 다시 시도해 주세요.");
            }
            // 키 오류 등 서버 설정 문제는 내부 정보를 노출하지 않고 로그로만 남김
            return error(HttpStatus.BAD_GATEWAY, "경로 검색 서비스를 일시적으로 사용할 수 없습니다.");
        } catch (ResourceAccessException e) {
            log.warn("TMAP 대중교통 API 응답 없음: {}", e.getMessage());
            healthService.record(HealthService.Component.TMAP, false);
            return error(HttpStatus.GATEWAY_TIMEOUT, "경로 검색 서버가 응답하지 않습니다. 잠시 후 다시 시도해 주세요.");
        }
    }

    private static ResponseEntity<String> error(HttpStatus status, String message) {
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"result\":{\"status\":-1,\"message\":\"" + message + "\"}}");
    }
}
