package MobilLax;

import MobilLax.Common.RateLimit.RateLimiter;
import MobilLax.Common.Time.KoreaTime;
import MobilLax.Common.Usage.CountsUsage;
import MobilLax.Common.Usage.UsageMetric;
import MobilLax.Domain.Cart.CartApiController;
import MobilLax.Domain.Cart.CartItemRequestDTO;
import MobilLax.Domain.Route.TmapTransitClient;
import MobilLax.Domain.Route.TransitRouteRequest;
import MobilLax.Domain.Stats.DailyUsage;
import MobilLax.Domain.Stats.DailyUsageRepository;
import MobilLax.Domain.Stats.UsageCounter;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.repository.CrudRepository;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 보안 회귀 테스트 S13 — 이용 통계 (v2.13, docs/SECURITY_RULES.md)
 * 이 테스트를 지우거나 약하게 바꾸지 않는다. 규칙을 바꿔야 하면 문서와 함께 리뷰를 거친다.
 *
 * 로컬 DB 를 함께 쓰므로 합계는 전후 차이로 확인하고, 트랜잭션을 되돌려 실제 통계에 남기지 않는다.
 * TMAP 은 가짜(MockBean)로 바꿔 외부 호출 없이 확인한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UsageStatsRegressionTests {

    private static final String ROUTE_BODY = "{\"startX\":127.0,\"startY\":37.5,\"endX\":127.1,\"endY\":37.6}";

    @Autowired
    MockMvc mvc;

    @Autowired
    DailyUsageRepository repository;

    @Autowired
    RateLimiter rateLimiter;

    @MockBean
    TmapTransitClient tmapClient;

    @PersistenceContext
    EntityManager entityManager;

    @BeforeEach
    void setUp() {
        rateLimiter.clear();
    }

    /** 합계는 네이티브 문장으로 더하므로, 테스트 트랜잭션이 먼저 읽어 둔 엔티티를 비워야 새 값이 보인다 */
    private long today(UsageMetric metric) {
        entityManager.flush();
        entityManager.clear();
        LocalDate today = KoreaTime.today();
        return repository.findByStatDateBetween(today, today).stream()
                .filter(row -> row.getMetric() == metric)
                .mapToLong(DailyUsage::getTotal).sum();
    }

    /** 다른 테스트의 방문 기록과 겹치지 않게 매번 다른 브라우저 정보를 쓴다 */
    private static String browser() {
        return "Mozilla/5.0 test-" + UUID.randomUUID();
    }

    @Test
    void aPageCountsAViewAndTheVisitorOnlyOnceADay() throws Exception {
        long views = today(UsageMetric.PAGE_VIEW);
        long visitors = today(UsageMetric.VISITOR);
        String browser = browser();

        mvc.perform(get("/home").header(HttpHeaders.USER_AGENT, browser)).andExpect(status().isOk());
        mvc.perform(get("/nearby").header(HttpHeaders.USER_AGENT, browser)).andExpect(status().isOk());

        assertThat(today(UsageMetric.PAGE_VIEW)).isEqualTo(views + 2);
        assertThat(today(UsageMetric.VISITOR)).isEqualTo(visitors + 1);

        mvc.perform(get("/home").header(HttpHeaders.USER_AGENT, browser())).andExpect(status().isOk());
        assertThat(today(UsageMetric.VISITOR)).as("다른 브라우저는 다른 방문자").isEqualTo(visitors + 2);
    }

    @Test
    void botsScriptsAndHealthChecksAreNotCounted() throws Exception {
        long views = today(UsageMetric.PAGE_VIEW);

        mvc.perform(get("/home")).andExpect(status().isOk());
        mvc.perform(get("/home").header(HttpHeaders.USER_AGENT, "curl/8.5.0")).andExpect(status().isOk());
        mvc.perform(get("/home").header(HttpHeaders.USER_AGENT, "Mozilla/5.0 (compatible; Googlebot/2.1)")).andExpect(status().isOk());
        mvc.perform(get("/home").header(HttpHeaders.USER_AGENT, "Mozilla/5.0 HeadlessChrome/120")).andExpect(status().isOk());

        assertThat(today(UsageMetric.PAGE_VIEW)).isEqualTo(views);
    }

    /** 브라우저가 스스로 하는 파비콘 요청이 로그인 화면으로 가면 화면 하나에 페이지 조회가 둘로 세어진다 (브라우저 확인에서 발견) */
    @Test
    void theFaviconRequestDoesNotBecomeALoginPageView() throws Exception {
        mvc.perform(get("/favicon.ico").header(HttpHeaders.USER_AGENT, browser())).andExpect(status().isNotFound());
    }

    @Test
    void redirectsAndMissingPagesAreNotPageViews() throws Exception {
        long views = today(UsageMetric.PAGE_VIEW);

        mvc.perform(get("/mypage").header(HttpHeaders.USER_AGENT, browser())).andExpect(status().is3xxRedirection());
        mvc.perform(get("/no-such-page").header(HttpHeaders.USER_AGENT, browser()));
        mvc.perform(post("/login").with(csrf()).header(HttpHeaders.USER_AGENT, browser()).param("email", "none@test.local")
                .param("password", "FAKE_wrong1")).andExpect(status().is3xxRedirection());

        assertThat(today(UsageMetric.PAGE_VIEW)).isEqualTo(views);
    }

    @Test
    void anApiCountsOnlyWhenItSucceeds() throws Exception {
        long searches = today(UsageMetric.ROUTE_SEARCH);
        when(tmapClient.searchTransitRoutes(any(TransitRouteRequest.class))).thenReturn("{}");

        mvc.perform(post("/api/route/transit").with(csrf()).header(HttpHeaders.USER_AGENT, browser())
                        .contentType(MediaType.APPLICATION_JSON).content(ROUTE_BODY))
                .andExpect(status().isOk());
        assertThat(today(UsageMetric.ROUTE_SEARCH)).isEqualTo(searches + 1);

        mvc.perform(post("/api/route/transit").with(csrf()).header(HttpHeaders.USER_AGENT, browser())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"startX\":0,\"startY\":0,\"endX\":0,\"endY\":0}"))
                .andExpect(status().isBadRequest());
        assertThat(today(UsageMetric.ROUTE_SEARCH)).as("입력이 틀리면 세지 않음").isEqualTo(searches + 1);
    }

    @Test
    void cartAddIsDeclaredAsAUsageMetric() throws Exception {
        Method add = CartApiController.class.getMethod("addSelectedToCart", CartItemRequestDTO.class, java.security.Principal.class);
        assertThat(add.getAnnotation(CountsUsage.class).value()).isEqualTo(UsageMetric.CART_ADD);
    }

    /** 통계 칸은 날짜·항목·합계뿐이다. 개인 식별 값을 저장할 칸을 더하면 여기서 실패한다 */
    @Test
    void storedStatsHoldNoPersonalData() {
        assertThat(Arrays.stream(DailyUsage.class.getDeclaredFields()).filter(f -> !f.isSynthetic()).map(Field::getName))
                .containsExactlyInAnyOrder("id", "statDate", "metric", "total");
        assertThat(CrudRepository.class.isAssignableFrom(DailyUsageRepository.class)).isFalse();
        assertThat(Arrays.stream(DailyUsageRepository.class.getMethods()).map(Method::getName))
                .containsExactlyInAnyOrderElementsOf(List.of("increment", "findByStatDateBetween"));
    }

    /** 통계를 남기지 못해도 사용자의 요청은 그대로 끝난다 */
    @Test
    void aStatsFailureDoesNotBreakTheRequest() {
        DailyUsageRepository broken = mock(DailyUsageRepository.class);
        doThrow(new IllegalStateException("db down")).when(broken).increment(any(LocalDate.class), anyString());

        assertThatCode(() -> new UsageCounter(broken).count(UsageMetric.PAGE_VIEW)).doesNotThrowAnyException();
    }
}
