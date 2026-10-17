package MobilLax;

import MobilLax.Domain.Health.HealthService;
import MobilLax.Domain.Health.HealthService.Component;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 보안 회귀 테스트 S7 — 헬스체크 (docs/SECURITY_RULES.md)
 * 이 테스트를 지우거나 약하게 바꾸지 않는다. 규칙을 바꿔야 하면 문서와 함께 리뷰를 거친다.
 *
 * - 로그인 없이 열리고, 응답은 {"status":"UP"|"DOWN"} 뿐이다 (구성 요소 이름·오류 메시지 없음)
 * - 이상이 있으면 503
 * - /health/live 는 DB 만 본다 (외부 API 장애로 컨테이너를 재시작하지 않도록)
 * DB 장애 판정은 HealthServiceTests 가 가짜 DataSource 로 확인한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class HealthRegressionTests {

    @Autowired
    MockMvc mvc;

    @Autowired
    HealthService healthService;

    @AfterEach
    void reset() {
        // 같은 스프링 컨텍스트를 쓰는 다른 테스트에 상태가 남지 않도록
        for (Component component : Component.values()) {
            healthService.record(component, true);
        }
    }

    @Test
    void healthIsOpenWithoutLoginAndReturnsOnlyStatus() throws Exception {
        mvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"status\":\"UP\"}", true))
                .andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(get("/health/live"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"status\":\"UP\"}", true));
    }

    @Test
    void externalFailureGives503WithoutDetails() throws Exception {
        healthService.record(Component.PORTONE, false);

        String body = mvc.perform(get("/health"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().json("{\"status\":\"DOWN\"}", true))
                .andReturn().getResponse().getContentAsString();
        for (Component component : Component.values()) {
            assertThat(body).doesNotContainIgnoringCase(component.name());
        }
    }

    @Test
    void liveIgnoresExternalApis() throws Exception {
        healthService.record(Component.TMAP, false); // 예: TMAP 하루 한도 소진
        mvc.perform(get("/health/live")).andExpect(status().isOk());
        mvc.perform(get("/health")).andExpect(status().isServiceUnavailable());
    }

    @Test
    void oldDbEndpointIsGone() throws Exception {
        // 예외 메시지를 내보내던 /health/db 는 없앴다. 로그인하지 않으면 다른 비공개 경로처럼 로그인으로 보낸다
        mvc.perform(get("/health/db")).andExpect(status().is3xxRedirection());
    }
}
