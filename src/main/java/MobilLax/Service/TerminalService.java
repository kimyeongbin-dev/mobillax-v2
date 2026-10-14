/**
 * TerminalService.java
 *
 * ✅ 파일 목적: 공공데이터포털의 시외버스 터미널 목록 조회 API(getSuberbsBusTrminlList)를 호출하여
 *              XML 데이터를 비동기로 받아오는 기능을 제공하는 서비스 클래스입니다.
 *
 * 작성자: 김영빈
 * 마지막 수정일: 2025-05-11
 */

package MobilLax.Service;

import MobilLax.Config.BusApiProperties;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * ✅ 클래스 설명:
 * 시외버스 관련 OpenAPI를 호출하여 터미널 정보를 조회하는 서비스입니다.
 * WebClient를 이용한 비동기 HTTP 요청을 통해 XML 응답을 수신하며,
 * BusApiProperties에서 불러온 API 키를 이용해 요청 URI를 구성합니다.
 */
@Service
public class TerminalService {

    private final WebClient webClient;
    private final BusApiProperties busApiProperties;

    /**
     * 생성자 주입
     *
     * @param webClient 비동기 HTTP 요청을 위한 WebClient
     * @param busApiProperties API 키를 보유한 설정 클래스
     */
    public TerminalService(WebClient webClient, BusApiProperties busApiProperties) {
        this.webClient = webClient;
        this.busApiProperties = busApiProperties;
    }

    /**
     * ✅ 터미널 목록 조회
     *
     * 공공데이터포털의 시외버스 터미널 목록 조회 API를 호출하여,
     * 입력된 터미널 이름에 해당하는 정보를 XML 형식 문자열로 반환합니다.
     *
     * @param terminalName 사용자로부터 입력 받은 터미널 이름 (예: "동서울")
     * @return Mono<String> XML 응답 문자열을 포함하는 비동기 객체
     */
    public Mono<String> getTerminalList(String terminalName) {
        // 터미널 이름을 URL 인코딩 (한글 포함 가능성 있음)
        String encodedTerminalName = URLEncoder.encode(terminalName, StandardCharsets.UTF_8);

        // API 인증키 (이미 URL 인코딩된 값)
        String serviceKey = busApiProperties.getKey();

        // URI 직접 구성 (UriComponentsBuilder 사용 시 인코딩 중복 이슈 있음)
        URI uri = URI.create(String.format(
                "https://apis.data.go.kr/1613000/SuburbsBusInfoService/getSuberbsBusTrminlList" +
                        "?serviceKey=%s&_type=xml&terminalNm=%s",
                serviceKey, encodedTerminalName
        ));

        System.out.println("✅ 최종 URI: " + uri);

        // WebClient를 이용해 비동기 HTTP GET 요청
        return webClient.get()
                .uri(uri)
                .header("Accept", "application/xml")
                .retrieve()
                .bodyToMono(String.class);
    }
}
