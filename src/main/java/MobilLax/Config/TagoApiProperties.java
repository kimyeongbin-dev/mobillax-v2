package MobilLax.Config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 공공데이터포털 TAGO 인증 키 (bus.api.key ← .env 의 BUS_API_KEY)
 * - 기차·고속·시외버스·항공 편 조회와 역·터미널 목록에 같은 키를 쓴다 (TagoTripClient, TagoPlaceClient)
 * - 키가 든 요청 URL 은 로그에 남기지 않는다 (보안 규칙 S6)
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "bus.api")
public class TagoApiProperties {

    private String key;
}
