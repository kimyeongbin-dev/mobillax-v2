package MobilLax.Service;

import MobilLax.Model.TransitRouteRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
public class RouteServiceImpl implements RouteService {

    private static final String TRANSIT_URL = "https://apis.openapi.sk.com/transit/routes";
    private static final int ROUTE_COUNT = 10;

    private final RestTemplate restTemplate;
    private final String tmapAppKey;

    public RouteServiceImpl(RestTemplateBuilder builder, @Value("${TMAP_APPKEY}") String tmapAppKey) {
        this.restTemplate = builder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(15))
                .build();
        this.tmapAppKey = tmapAppKey;
    }

    @Override
    public String searchTransitRoutes(TransitRouteRequest request) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.set("appKey", tmapAppKey);

        // 좌표는 TMAP 문서 예시와 같이 문자열로 전달. searchDttm 을 생략하면 현재 시각 기준으로 검색됨
        Map<String, Object> body = Map.of(
                "startX", String.valueOf(request.startX()),
                "startY", String.valueOf(request.startY()),
                "endX", String.valueOf(request.endX()),
                "endY", String.valueOf(request.endY()),
                "count", ROUTE_COUNT,
                "lang", 0,
                "format", "json"
        );

        return restTemplate.postForObject(TRANSIT_URL, new HttpEntity<>(body, headers), String.class);
    }
}
