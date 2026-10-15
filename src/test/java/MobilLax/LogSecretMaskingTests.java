package MobilLax;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 로그 패턴(logging.pattern.console)이 메시지 속 키·시크릿을 가리는지 검증
 */
@SpringBootTest
@ExtendWith(OutputCaptureExtension.class)
class LogSecretMaskingTests {

    private static final Logger log = LoggerFactory.getLogger(LogSecretMaskingTests.class);

    @Test
    void masksSecretsInLogMessages(CapturedOutput output) {
        log.info("GET https://apis.data.go.kr/x?serviceKey=FAKE_SERVICE_KEY_123&_type=xml");
        log.info("headers={\"appKey\": \"FAKE_TMAP_KEY_456\"}");
        log.info("Authorization: PortOne FAKE_PORTONE_SECRET_789");
        log.info("password=FAKE_DB_PASSWORD");

        assertThat(output.getOut())
                .doesNotContain("FAKE_SERVICE_KEY_123", "FAKE_TMAP_KEY_456", "FAKE_PORTONE_SECRET_789", "FAKE_DB_PASSWORD")
                .contains("serviceKey=****", "PortOne ****", "password=****")
                // 키가 아닌 부분은 그대로 남아야 함
                .contains("https://apis.data.go.kr/x?", "&_type=xml");
    }
}
