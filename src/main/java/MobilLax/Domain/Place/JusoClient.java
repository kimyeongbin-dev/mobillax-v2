package MobilLax.Domain.Place;

import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 행정안전부 도로명주소 검색 API (business.juso.go.kr, 인터넷망 검색 API)
 * - 승인키에는 = + / 같은 문자가 들어갈 수 있어 URI 변수로 넘겨 모두 인코딩한다
 * - 공공데이터라 결과를 기준표(transit_place)에 저장해도 된다
 */
@Component
public class JusoClient {

    private static final String URL = "https://business.juso.go.kr/addrlink/addrLinkApi.do";

    private final RestTemplate restTemplate;
    private final String confmKey;
    // 응답의 상세건물명 목록에 이스케이프되지 않은 제어문자가 섞여 오는 경우가 있다
    private final JsonMapper jsonMapper = JsonMapper.builder()
            .enable(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS)
            .build();

    public JusoClient(RestTemplateBuilder builder, @Value("${JUSO_SEARCH_KEY:}") String confmKey) {
        this.restTemplate = builder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(10))
                .build();
        this.confmKey = confmKey;
    }

    public boolean isConfigured() {
        return confmKey != null && !confmKey.isBlank();
    }

    /**
     * 검색어로 주소를 찾는다 (건물명·도로명 주소 모두 검색어로 쓸 수 있음)
     */
    public List<JusoAddress> search(String keyword) {
        URI uri = UriComponentsBuilder.fromHttpUrl(URL)
                .queryParam("confmKey", "{key}")
                .queryParam("keyword", "{keyword}")
                .queryParam("currentPage", 1)
                .queryParam("countPerPage", 10)
                .queryParam("resultType", "json")
                .encode()
                .buildAndExpand(Map.of("key", confmKey, "keyword", keyword))
                .toUri();

        JsonNode results;
        try {
            results = jsonMapper.readTree(restTemplate.getForObject(uri, String.class)).path("results");
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("도로명주소 API 응답을 읽을 수 없습니다");
        }
        String errorCode = results.path("common").path("errorCode").asText();
        if (!"0".equals(errorCode)) {
            throw new IllegalStateException("도로명주소 API 오류 " + errorCode + " "
                    + results.path("common").path("errorMessage").asText());
        }

        List<JusoAddress> addresses = new ArrayList<>();
        for (JsonNode j : results.path("juso")) {
            addresses.add(new JusoAddress(
                    j.path("bdNm").asText(""),
                    j.path("roadAddrPart1").asText(""),
                    j.path("jibunAddr").asText(""),
                    j.path("siNm").asText(""),
                    j.path("sggNm").asText(""),
                    j.path("rn").asText(""),
                    toInt(j.path("buldMnnm").asText()),
                    toInt(j.path("buldSlno").asText()),
                    j.path("emdNm").asText(""),
                    toInt(j.path("lnbrMnnm").asText()),
                    toInt(j.path("lnbrSlno").asText()),
                    "1".equals(j.path("udrtYn").asText())));
        }
        return addresses;
    }

    private static Integer toInt(String value) {
        try {
            return value == null || value.isBlank() ? null : Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
