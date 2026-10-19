package MobilLax.Domain.Route;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * TMAP 대중교통 API 클라이언트 (서버 전용 키, 보안 규칙 S9)
 * - 하루 호출 한도(FREE 10건)가 있어 화면은 응답을 sessionStorage 에 두고 다시 쓴다
 */
@Component
public class TmapTransitClient {

    private static final String TRANSIT_URL = "https://apis.openapi.sk.com/transit/routes";
    private static final int ROUTE_COUNT = 10;

    private final RestTemplate restTemplate;
    private final String tmapAppKey;

    public TmapTransitClient(RestTemplateBuilder builder, @Value("${TMAP_APPKEY}") String tmapAppKey) {
        this.restTemplate = builder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(15))
                .build();
        this.tmapAppKey = tmapAppKey;
    }

    /**
     * 경로를 검색해 TMAP 응답 JSON 원문을 돌려준다
     *
     * @throws org.springframework.web.client.HttpStatusCodeException TMAP 이 오류 상태로 응답한 경우
     * @throws org.springframework.web.client.ResourceAccessException TMAP 응답이 없는 경우
     */
    public String searchTransitRoutes(TransitRouteRequest request) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.set("appKey", tmapAppKey);

        // 좌표는 TMAP 문서 예시와 같이 문자열로 전달. searchDttm 을 생략하면 현재 시각 기준으로 검색됨
        Map<String, Object> body = new HashMap<>(Map.of(
                "startX", String.valueOf(request.startX()),
                "startY", String.valueOf(request.startY()),
                "endX", String.valueOf(request.endX()),
                "endY", String.valueOf(request.endY()),
                "count", ROUTE_COUNT,
                "lang", 0,
                "format", "json"
        ));
        if (request.hasDepartAt()) {
            body.put("searchDttm", request.departAt());
        }

        return restTemplate.postForObject(TRANSIT_URL, new HttpEntity<>(body, headers), String.class);
    }
}
