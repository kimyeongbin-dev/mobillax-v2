package MobilLax.Domain.Trip;

import MobilLax.Config.BusApiProperties;
import MobilLax.Domain.Place.TransitKind;
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

/**
 * TAGO 출발지·도착지 기준 편(열차·고속버스·시외버스·항공) 조회
 * - 응답 필드 이름이 API 마다 달라 하나의 Trip 으로 맞춘다 (docs/PUBLIC_DATA_API.md 3~6장)
 * - 키가 들어간 요청 URL 은 로그에 남기지 않는다
 */
@Component
public class TagoTripClient {

    private static final String BASE = "https://apis.data.go.kr/1613000/";
    private static final int PAGE_SIZE = 300;

    /**
     * 편 한 건
     *
     * @param no       열차번호·노선ID·편명
     * @param grade    KTX, 우등, 프리미엄, 대한항공 등
     * @param departAt 출발 yyyyMMddHHmm
     * @param arriveAt 도착 yyyyMMddHHmm
     * @param fare     어른(일반석) 운임(원)
     */
    public record Trip(String no, String grade, String departAt, String arriveAt, int fare) {
    }

    private final RestTemplate restTemplate;
    private final BusApiProperties busApiProperties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public TagoTripClient(RestTemplateBuilder builder, BusApiProperties busApiProperties) {
        this.restTemplate = builder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(15))
                .build();
        this.busApiProperties = busApiProperties;
    }

    /**
     * @param date 출발일 yyyyMMdd
     */
    public List<Trip> trips(TransitKind kind, String depId, String arrId, String date) {
        return switch (kind) {
            case TRAIN -> fetch("TrainInfo/GetStrtpntAlocFndTrainInfo",
                    "&depPlaceId=" + enc(depId) + "&arrPlaceId=" + enc(arrId) + "&depPlandTime=" + enc(date),
                    i -> new Trip(i.path("trainno").asText(), i.path("traingradename").asText(),
                            minute(i.path("depplandtime").asText()), minute(i.path("arrplandtime").asText()),
                            i.path("adultcharge").asInt()));
            case EXPRESS_BUS -> fetch("ExpBusInfo/GetStrtpntAlocFndExpbusInfo",
                    "&depTerminalId=" + enc(depId) + "&arrTerminalId=" + enc(arrId) + "&depPlandTime=" + enc(date),
                    TagoTripClient::busTrip);
            case INTERCITY_BUS -> fetch("SuburbsBusInfo/GetStrtpntAlocFndSuberbsBusInfo",
                    "&depTerminalId=" + enc(depId) + "&arrTerminalId=" + enc(arrId) + "&depPlandTime=" + enc(date),
                    TagoTripClient::busTrip);
            case AIRPLANE -> fetch("DmstcFlightNvgInfo/GetFlightOpratInfoList",
                    "&depAirportId=" + enc(depId) + "&arrAirportId=" + enc(arrId) + "&depPlandTime=" + enc(date),
                    i -> new Trip(i.path("vihicleId").asText(), i.path("airlineNm").asText(),
                            minute(i.path("depPlandTime").asText()), minute(i.path("arrPlandTime").asText()),
                            i.path("economyCharge").asInt()));
        };
    }

    private static Trip busTrip(JsonNode i) {
        return new Trip(i.path("routeId").asText(), i.path("gradeNm").asText(),
                minute(i.path("depPlandTime").asText()), minute(i.path("arrPlandTime").asText()),
                i.path("charge").asInt());
    }

    private List<Trip> fetch(String operation, String query, java.util.function.Function<JsonNode, Trip> mapper) {
        URI uri = URI.create(BASE + operation + "?serviceKey=" + busApiProperties.getKey()
                + "&_type=json&numOfRows=" + PAGE_SIZE + "&pageNo=1" + query);
        JsonNode body;
        try {
            JsonNode response = objectMapper.readTree(restTemplate.getForObject(uri, String.class)).path("response");
            String code = response.path("header").path("resultCode").asText();
            if (!"00".equals(code)) {
                throw new IllegalStateException("TAGO " + operation + " 오류 코드 " + code);
            }
            body = response.path("body");
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("TAGO " + operation + " 응답을 읽을 수 없습니다");
        }

        List<Trip> trips = new ArrayList<>();
        JsonNode items = body.path("items").path("item");
        if (items.isArray()) {
            items.forEach(item -> trips.add(mapper.apply(item)));
        } else if (items.isObject()) {
            trips.add(mapper.apply(items));
        }
        return trips;
    }

    /** yyyyMMddHHmm(ss) → yyyyMMddHHmm */
    static String minute(String value) {
        return value == null || value.length() < 12 ? "" : value.substring(0, 12);
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
