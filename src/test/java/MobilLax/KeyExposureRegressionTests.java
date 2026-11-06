package MobilLax;

import MobilLax.Domain.Account.UserAccount;
import MobilLax.Domain.Account.UserAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * 보안 회귀 테스트 S9 — 서버용 키는 브라우저로 보내지 않는다 (docs/SECURITY_RULES.md)
 * 이 테스트를 지우거나 약하게 바꾸지 않는다. 규칙을 바꿔야 하면 문서와 함께 리뷰를 거친다.
 *
 * - 로그인 전·후의 모든 화면과 정적 JS 에 서버용 키 값이 들어 있지 않은지 실제 설정값과 비교한다
 * - 브라우저에 허용하는 키는 카카오 JavaScript 키 하나이고, 카카오 지도 SDK 주소 안에서만 쓰인다
 * - 실패해도 키 값은 출력하지 않는다 (키 이름과 화면 경로만 알린다)
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class KeyExposureRegressionTests {

    private static final String USER = "key-exposure@test.local";

    /** 브라우저로 가면 안 되는 키 (설정 이름) */
    private static final String[] SERVER_KEYS = {
            "TMAP_APPKEY", "KAKAO_APPKEY", "BUS_API_KEY", "JUSO_SEARCH_KEY", "PORTONE_APPKEY",
            "spring.datasource.password"
    };

    /** 화면이 불러와도 되는 외부 스크립트 주소 */
    private static final List<String> ALLOWED_SCRIPT_HOSTS = List.of(
            "https://dapi.kakao.com/v2/maps/sdk.js", "https://cdn.portone.io/v2/browser-sdk.js");

    private static final String KAKAO_SDK_KEY_PARAM = "dapi.kakao.com/v2/maps/sdk.js?appkey=";
    private static final Pattern SCRIPT_SRC = Pattern.compile("<script[^>]*\\ssrc=\"(https?:)?//([^\"?]+)");

    @Autowired
    MockMvc mvc;

    @Autowired
    Environment env;

    @Autowired
    UserAccountRepository userAccountRepository;

    @BeforeEach
    void user() {
        // 마이페이지는 실제 사용자 정보가 있어야 그려진다 (트랜잭션으로 되돌림)
        if (userAccountRepository.findByEmail(USER).isEmpty()) {
            UserAccount account = new UserAccount();
            account.setEmail(USER);
            account.setName("키노출검사");
            account.setPassword("FAKE_not_used");
            account.setRole("USER");
            userAccountRepository.save(account);
        }
    }

    /** 화면 경로 → 응답 본문 (로그인 전 공개 화면, 로그인 후 화면, 모든 정적 JS) */
    private Map<String, String> browserResponses(boolean loggedIn) throws Exception {
        Map<String, String> bodies = new LinkedHashMap<>();
        List<String> paths = new ArrayList<>(List.of("/", "/home", "/login", "/register", "/route", "/compare", "/nearby"));
        if (loggedIn) {
            paths.addAll(List.of("/route", "/cart", "/mypage", "/mypage/edit"));
        }
        for (Resource js : new PathMatchingResourcePatternResolver().getResources("classpath:/static/**/*.js")) {
            String url = js.getURL().toString();
            paths.add(url.substring(url.indexOf("/static/") + "/static".length()));
        }
        for (String path : paths) {
            bodies.put(path, mvc.perform(get(path).session(TestSessions.recentPassword())).andReturn().getResponse().getContentAsString());
        }
        return bodies;
    }

    private void assertNoServerKey(Map<String, String> bodies) {
        for (String name : SERVER_KEYS) {
            String value = env.getProperty(name);
            if (value == null || value.isBlank() || value.length() < 8) {
                continue; // 설정되지 않은 환경에서는 비교할 값이 없다
            }
            bodies.forEach((path, body) -> assertThat(body.contains(value))
                    .as("서버용 키 %s 가 %s 응답에 들어 있다", name, path)
                    .isFalse());
        }
    }

    private void assertKakaoJsKeyOnlyInSdkUrl(Map<String, String> bodies) {
        String jsKey = env.getProperty("KAKAO_JS_APPKEY");
        if (jsKey == null || jsKey.length() < 8) {
            return;
        }
        bodies.forEach((path, body) -> assertThat(body.replace(KAKAO_SDK_KEY_PARAM + jsKey, "").contains(jsKey))
                .as("카카오 JavaScript 키가 %s 에서 지도 SDK 주소 밖에 쓰였다", path)
                .isFalse());
    }

    private void assertOnlyAllowedScripts(Map<String, String> bodies) {
        bodies.forEach((path, body) -> {
            Matcher m = SCRIPT_SRC.matcher(body);
            while (m.find()) {
                String src = "https://" + m.group(2);
                assertThat(ALLOWED_SCRIPT_HOSTS).as("%s 가 허용하지 않은 외부 스크립트 %s 를 불러온다", path, src)
                        .contains(src);
            }
            // 본문을 출력하지 않도록 boolean 으로 비교 (본문에 키가 섞여 있을 수 있다)
            assertThat(body.contains("apis.openapi.sk.com")).as("%s 가 TMAP 주소를 브라우저에서 쓴다", path).isFalse();
        });
    }

    @Test
    void anonymousPagesExposeNoServerKey() throws Exception {
        Map<String, String> bodies = browserResponses(false);
        assertNoServerKey(bodies);
        assertKakaoJsKeyOnlyInSdkUrl(bodies);
        assertOnlyAllowedScripts(bodies);
    }

    @Test
    @WithMockUser(username = USER)
    void loggedInPagesExposeNoServerKey() throws Exception {
        Map<String, String> bodies = browserResponses(true);
        assertThat(bodies.get("/route").contains(KAKAO_SDK_KEY_PARAM)).as("경로 화면은 카카오 지도 SDK 를 불러온다").isTrue();
        assertNoServerKey(bodies);
        assertKakaoJsKeyOnlyInSdkUrl(bodies);
        assertOnlyAllowedScripts(bodies);
    }
}
