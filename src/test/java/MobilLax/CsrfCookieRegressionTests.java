package MobilLax;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 보안 회귀 테스트 S3 — 스크립트(fetch)가 CSRF 토큰을 쓸 수 있도록 XSRF-TOKEN 쿠키가 HttpOnly 없이 내려가야 한다
 * MockMvc 는 이 쿠키(Set-Cookie 헤더)를 재현하지 못해 실제 서버를 띄워 확인한다
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CsrfCookieRegressionTests {

    @Autowired
    TestRestTemplate rest;

    @Test
    void tokenCookieIsIssuedForScripts() {
        List<String> cookies = rest.getForEntity("/home", String.class).getHeaders().get("Set-Cookie");
        assertThat(cookies).isNotNull()
                .anySatisfy(c -> assertThat(c).startsWith("XSRF-TOKEN=").doesNotContainIgnoringCase("HttpOnly"));
    }
}
