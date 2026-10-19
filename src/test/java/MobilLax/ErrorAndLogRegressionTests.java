package MobilLax;

import MobilLax.Common.Error.GlobalExceptionHandler;
import MobilLax.Common.Error.UserFacingException;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 보안 회귀 테스트 S5·S6 — 응답과 로그에 내부 정보를 남기지 않는다 (docs/SECURITY_RULES.md)
 * 이 테스트를 지우거나 약하게 바꾸지 않는다. 규칙을 바꿔야 하면 문서와 함께 리뷰를 거친다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ErrorAndLogRegressionTests {

    private static final String INTERNAL = "jdbc:mysql://db:3306/mobillax java.lang.NullPointerException at MobilLax.Foo";

    @Autowired
    MockMvc mvc;

    @Autowired
    Environment env;

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    /** S5: 사용자용으로 던진 문구만 응답에 싣고, 그 밖의 예외 메시지는 일반 문구로 바꾼다 */
    @Test
    void onlyUserFacingMessagesReachTheClient() {
        assertThat(handler.userFacing(new UserFacingException("당일 출발편만 예매할 수 있습니다.")).getBody())
                .containsEntry("error", "당일 출발편만 예매할 수 있습니다.");

        ResponseEntity<Map<String, String>> library = handler.illegalArgument(new IllegalArgumentException(INTERNAL));
        assertThat(library.getStatusCode().value()).isEqualTo(400);
        assertThat(library.getBody().get("error")).doesNotContain("jdbc", "java.", "Exception", "MobilLax");

        ResponseEntity<Map<String, String>> internal = handler.internalError(new IllegalStateException(INTERNAL));
        assertThat(internal.getStatusCode().value()).isEqualTo(500);
        assertThat(internal.getBody().get("error")).doesNotContain("jdbc", "java.", "Exception", "MobilLax");
    }

    /** S5: 잘못된 요청 본문에도 파서·클래스 정보가 응답에 나오지 않는다 */
    @Test
    @WithMockUser(username = "error-test@test.local")
    void malformedRequestShowsNoParserDetails() throws Exception {
        String body = mvc.perform(post("/api/cart/add").with(csrf()).contentType("application/json")
                        .content("{\"selectedLegs\": [ {\"routePayment\": \"not-a-number\"} ]}"))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("Jackson", "java.", "Exception", "MobilLax.");
    }

    /** S5: 스프링 기본 오류 응답에 메시지·스택트레이스·예외 이름을 넣지 않는다 */
    @Test
    void springErrorAttributesHideDetails() {
        assertThat(env.getProperty("server.error.include-message")).isEqualTo("never");
        assertThat(env.getProperty("server.error.include-stacktrace")).isEqualTo("never");
        assertThat(env.getProperty("server.error.include-binding-errors")).isEqualTo("never");
        assertThat(env.getProperty("server.error.include-exception")).isEqualTo("false");
    }

    /** S6: SQL·바인드 값·보안 디버그·외부 호출 URL(키 포함)이 로그에 남는 수준으로 켜져 있지 않다 */
    @Test
    void leakyLoggersAreNotDebug() {
        String[] loggers = {
                "org.springframework.security", "org.springframework.web.client",
                "org.springframework.web.reactive.function.client", "reactor.netty.http.client",
                "org.hibernate.SQL", "org.hibernate.orm.jdbc.bind", "org.hibernate.type.descriptor.sql"
        };
        for (String name : loggers) {
            assertThat(LoggerFactory.getLogger(name).isDebugEnabled()).as("%s 가 DEBUG 이하로 켜져 있다", name).isFalse();
        }
        assertThat(env.getProperty("spring.jpa.show-sql")).isEqualTo("false");
    }
}
