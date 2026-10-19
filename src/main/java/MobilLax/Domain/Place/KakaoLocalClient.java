package MobilLax.Domain.Place;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 카카오 로컬 API (REST 키)
 * - 카카오 약관상 좌표·검색 결과는 저장하지 않는다. 매칭할 때 비교에만 쓴다
 */
@Component
public class KakaoLocalClient {

    private static final String BASE = "https://dapi.kakao.com/v2/local";

    /** 좌표 (WGS84) */
    public record Coord(double lat, double lon) {
    }

    private final RestTemplate restTemplate;
    private final String restKey;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public KakaoLocalClient(RestTemplateBuilder builder, @Value("${KAKAO_APPKEY:}") String restKey) {
        this.restTemplate = builder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(10))
                .build();
        this.restKey = restKey;
    }

    public boolean isConfigured() {
        return restKey != null && !restKey.isBlank();
    }

    /** 좌표 → 도로명·지번 주소 */
    public Optional<KakaoAddress> coordToAddress(double lat, double lon) {
        JsonNode docs = get("/geo/coord2address.json", Map.of("x", String.valueOf(lon), "y", String.valueOf(lat)));
        if (!docs.has(0)) {
            return Optional.empty();
        }
        JsonNode road = docs.get(0).path("road_address");
        JsonNode lot = docs.get(0).path("address");
        return Optional.of(new KakaoAddress(
                lot.path("region_1depth_name").asText(road.path("region_1depth_name").asText("")),
                lot.path("region_2depth_name").asText(road.path("region_2depth_name").asText("")),
                lot.path("region_3depth_name").asText(""),
                road.path("road_name").asText(null),
                toInt(road.path("main_building_no").asText()),
                toInt(road.path("sub_building_no").asText()),
                toInt(lot.path("main_address_no").asText()),
                toInt(lot.path("sub_address_no").asText()),
                road.path("address_name").asText(null),
                lot.path("address_name").asText(null)));
    }

    /** 주소 → 좌표 */
    public Optional<Coord> addressToCoord(String address) {
        return firstCoord(get("/search/address.json", Map.of("query", address)));
    }

    /** 장소 이름 → 좌표 (첫 번째 검색 결과) */
    public Optional<Coord> keywordToCoord(String keyword) {
        return firstCoord(get("/search/keyword.json", Map.of("query", keyword)));
    }

    /**
     * 좌표 주변의 카테고리 장소 (가까운 순)
     * - 결과는 응답으로만 쓰고 저장하지 않는다 (카카오 약관)
     *
     * @param categoryCode 카카오 카테고리 그룹 코드 (서버가 정한 값만)
     * @param radius       반경(m), 카카오 최대 20000
     * @param size         최대 개수, 카카오 최대 15
     */
    public List<KakaoPlace> categorySearch(String categoryCode, double lat, double lon, int radius, int size) {
        JsonNode docs = get("/search/category.json", Map.of(
                "category_group_code", categoryCode,
                "x", String.valueOf(lon), "y", String.valueOf(lat),
                "radius", String.valueOf(radius), "size", String.valueOf(size), "sort", "distance"));
        List<KakaoPlace> places = new ArrayList<>();
        docs.forEach(doc -> places.add(new KakaoPlace(
                doc.path("id").asText(""),
                doc.path("place_name").asText(""),
                doc.path("category_name").asText(""),
                doc.path("road_address_name").asText(""),
                doc.path("address_name").asText(""),
                doc.path("phone").asText(""),
                doc.path("distance").asInt(-1))));
        return places;
    }

    private Optional<Coord> firstCoord(JsonNode docs) {
        if (!docs.has(0)) {
            return Optional.empty();
        }
        return Optional.of(new Coord(docs.get(0).path("y").asDouble(), docs.get(0).path("x").asDouble()));
    }

    private JsonNode get(String path, Map<String, String> params) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(BASE + path);
        params.keySet().forEach(name -> builder.queryParam(name, "{" + name + "}"));
        URI uri = builder.encode().buildAndExpand(params).toUri();

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, "KakaoAK " + restKey);
        String body = restTemplate.exchange(uri, HttpMethod.GET, new HttpEntity<>(headers), String.class).getBody();
        try {
            return objectMapper.readTree(body).path("documents");
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("카카오 로컬 API 응답을 읽을 수 없습니다");
        }
    }

    private static Integer toInt(String value) {
        try {
            return value == null || value.isBlank() ? null : Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
