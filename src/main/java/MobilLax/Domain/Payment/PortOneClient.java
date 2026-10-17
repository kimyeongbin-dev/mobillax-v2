package MobilLax.Domain.Payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/**
 * PortOne V2 REST API (결제 단건 조회·취소) — 결제 완료를 서버에서 확인한다 (보안 규칙 S1)
 * - API Secret(PORTONE_APPKEY)은 서버에서만 쓰고, 로그·예외 메시지에 남기지 않는다
 * - 문서: https://developers.portone.io/api/rest-v2/payment
 */
@Component
public class PortOneClient {

    private static final String BASE = "https://api.portone.io/payments/";

    /**
     * PortOne 이 알고 있는 결제 상태
     *
     * @param status   PAID, READY, FAILED, CANCELLED 등
     * @param total    결제 금액 합계(원)
     * @param currency KRW
     * @param storeId  결제가 일어난 상점
     */
    public record Payment(String status, long total, String currency, String storeId) {
    }

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String secret;

    public PortOneClient(RestTemplateBuilder builder, @Value("${portone_key}") String secret) {
        this.restTemplate = builder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(15))
                .build();
        this.secret = secret;
    }

    /**
     * 결제 단건 조회
     *
     * @return PortOne 에 없는 결제면 빈 값
     * @throws IllegalStateException 조회하지 못했을 때 (키 오류·네트워크 등)
     */
    public Optional<Payment> find(String paymentId) {
        try {
            String body = restTemplate.exchange(BASE + enc(paymentId), HttpMethod.GET,
                    new HttpEntity<>(headers()), String.class).getBody();
            JsonNode node = objectMapper.readTree(body);
            return Optional.of(new Payment(node.path("status").asText(), node.path("amount").path("total").asLong(-1),
                    node.path("currency").asText(), node.path("storeId").asText()));
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                return Optional.empty();
            }
            throw new IllegalStateException("PortOne 결제 조회 실패 HTTP " + e.getStatusCode().value());
        } catch (Exception e) {
            throw new IllegalStateException("PortOne 결제 조회 실패 " + e.getClass().getSimpleName());
        }
    }

    /**
     * 결제 전액 취소 (금액이 맞지 않거나 중복 결제일 때)
     *
     * @throws IllegalStateException 취소하지 못했을 때
     */
    public void cancel(String paymentId, String reason) {
        try {
            restTemplate.exchange(BASE + enc(paymentId) + "/cancel", HttpMethod.POST,
                    new HttpEntity<>(Map.of("reason", reason), headers()), String.class);
        } catch (Exception e) {
            throw new IllegalStateException("PortOne 결제 취소 실패 " + e.getClass().getSimpleName());
        }
    }

    private HttpHeaders headers() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.AUTHORIZATION, "PortOne " + secret);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
