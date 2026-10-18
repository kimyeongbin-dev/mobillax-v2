package MobilLax.Domain.Place;

import MobilLax.Config.BusApiProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * TAGO(공공데이터포털) 역·터미널·공항 목록 조회
 * - 공공데이터포털 인증키는 이미 URL 인코딩된 값이라 그대로 붙인다
 * - 키가 들어간 요청 URL 은 로그에 남기지 않는다
 */
@Component
public class TagoPlaceClient {

    private static final String BASE = "https://apis.data.go.kr/1613000/";
    private static final int PAGE_SIZE = 1000;
    private static final int MAX_PAGES = 10;

    /** TAGO 에서 받은 장소 한 건 */
    public record TagoPlace(String tagoId, String name, String cityName) {
    }

    /** 열차 시/도 코드 */
    public record TagoCity(String cityCode, String cityName) {
    }

    private final RestTemplate restTemplate;
    private final BusApiProperties busApiProperties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public TagoPlaceClient(RestTemplateBuilder builder, BusApiProperties busApiProperties) {
        this.restTemplate = builder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(15))
                .build();
        this.busApiProperties = busApiProperties;
    }

    public List<TagoCity> trainCities() {
        return fetchAll("TrainInfo/GetCtyCodeList", "",
                item -> new TagoCity(item.path("citycode").asText(), item.path("cityname").asText()));
    }

    public List<TagoPlace> trainStations(TagoCity city) {
        return fetchAll("TrainInfo/GetCtyAcctoTrainSttnList", "&cityCode=" + encode(city.cityCode()),
                item -> new TagoPlace(item.path("nodeid").asText(), item.path("nodename").asText(), city.cityName()));
    }

    public List<TagoPlace> expressTerminals() {
        return fetchAll("ExpBusInfo/GetExpBusTrminlList", "",
                item -> new TagoPlace(item.path("terminalId").asText(), item.path("terminalNm").asText(), null));
    }

    public List<TagoPlace> intercityTerminals() {
        return fetchAll("SuburbsBusInfo/GetSuberbsBusTrminlList", "",
                item -> new TagoPlace(item.path("terminalId").asText(), item.path("terminalNm").asText(),
                        item.path("cityName").asText(null)));
    }

    public List<TagoPlace> airports() {
        return fetchAll("DmstcFlightNvgInfo/GetArprtList", "",
                item -> new TagoPlace(item.path("airportId").asText(), item.path("airportNm").asText(), null));
    }

    private <T> List<T> fetchAll(String operation, String extraQuery, Function<JsonNode, T> mapper) {
        List<T> result = new ArrayList<>();
        for (int page = 1; page <= MAX_PAGES; page++) {
            URI uri = URI.create(BASE + operation + "?serviceKey=" + busApiProperties.getKey()
                    + "&_type=json&numOfRows=" + PAGE_SIZE + "&pageNo=" + page + extraQuery);
            JsonNode body = readBody(restTemplate.getForObject(uri, String.class), operation);
            JsonNode items = body.path("items").path("item");
            if (items.isArray()) {
                items.forEach(item -> result.add(mapper.apply(item)));
            } else if (items.isObject()) {
                result.add(mapper.apply(items));
            }
            if (result.size() >= body.path("totalCount").asInt(0) || !items.isArray()) {
                break;
            }
        }
        return result;
    }

    private JsonNode readBody(String json, String operation) {
        try {
            JsonNode response = objectMapper.readTree(json).path("response");
            String code = response.path("header").path("resultCode").asText();
            if (!"00".equals(code)) {
                throw new IllegalStateException("TAGO " + operation + " 오류 코드 " + code);
            }
            return response.path("body");
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            // 키 오류 등은 JSON 이 아닌 XML 오류 본문으로 온다
            throw new IllegalStateException("TAGO " + operation + " 응답을 읽을 수 없습니다");
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
