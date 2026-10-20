package MobilLax;

import MobilLax.Common.RateLimit.RateLimiter;
import MobilLax.Domain.Account.UserAccount;
import MobilLax.Domain.Account.UserAccountRepository;
import MobilLax.Domain.Route.TmapTransitClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 보안 회귀 테스트 S11 — 호출 횟수 제한과 비밀번호 대입 막기 (v2.9, docs/SECURITY_RULES.md)
 * 이 테스트를 지우거나 약하게 바꾸지 않는다. 규칙을 바꿔야 하면 문서와 함께 리뷰를 거친다.
 *
 * TMAP 은 가짜(MockBean)로 바꿔, 한도를 넘긴 요청이 외부 API 까지 가지 않는 것을 호출 횟수로 확인한다.
 * 횟수는 서버 메모리에 남으므로 테스트마다 비운다 (다른 테스트 클래스에 섞이지 않게 끝날 때도).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RateLimitRegressionTests {

    private static final String MEMBER = "ratelimit-member@test.local";
    private static final String PASSWORD = "FAKE_correct1";

    @Autowired
    MockMvc mvc;

    @Autowired
    RateLimiter rateLimiter;

    @Autowired
    UserAccountRepository userAccountRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @MockBean
    TmapTransitClient tmapClient;

    /** 시간 칸 경계에 걸려 결과가 흔들리지 않게 시각을 고정한다 (한국 시간 2026-10-20 12:00) */
    @MockBean
    Clock clock;

    private static final Instant NOON_KST = Instant.parse("2026-10-20T03:00:00Z");
    private static final Instant NEXT_MIDNIGHT_KST = Instant.parse("2026-10-20T15:00:00Z");

    @BeforeEach
    void setUp() {
        rateLimiter.clear();
        when(clock.instant()).thenReturn(NOON_KST);
        UserAccount account = new UserAccount();
        account.setEmail(MEMBER);
        account.setName("횟수제한검사");
        account.setPassword(passwordEncoder.encode(PASSWORD));
        account.setRole("USER");
        userAccountRepository.save(account);
        when(tmapClient.searchTransitRoutes(any())).thenReturn("{\"metaData\":{}}");
    }

    @AfterEach
    void clearCounts() {
        rateLimiter.clear();
    }

    private static RequestPostProcessor from(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }

    private ResultActions searchRoute(String ip) throws Exception {
        String body = "{\"startX\":126.9706,\"startY\":37.5547,\"endX\":127.4346,\"endY\":36.3326}";
        return mvc.perform(post("/api/route/transit").with(csrf()).with(from(ip))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    /** 경로 검색 (TMAP 하루 10건) */
    @Nested
    class RouteSearch {

        @Test
        void anonymousClientGetsThreePerDayThen429WithoutCallingTmap() throws Exception {
            for (int i = 0; i < 3; i++) {
                searchRoute("10.0.0.1").andExpect(status().isOk());
            }
            searchRoute("10.0.0.1")
                    .andExpect(status().isTooManyRequests())
                    .andExpect(header().exists("Retry-After"))
                    .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("로그인하면")));
            verify(tmapClient, times(3)).searchTransitRoutes(any());
        }

        @Test
        void dailyCountStartsAgainAtKoreanMidnight() throws Exception {
            for (int i = 0; i < 3; i++) {
                searchRoute("10.0.0.3").andExpect(status().isOk());
            }
            searchRoute("10.0.0.3")
                    .andExpect(status().isTooManyRequests())
                    .andExpect(header().string("Retry-After", String.valueOf(12 * 3600)));
            when(clock.instant()).thenReturn(NEXT_MIDNIGHT_KST);
            searchRoute("10.0.0.3").andExpect(status().isOk());
        }

        @Test
        void anonymousUsersTogetherGetFivePerDaySoMembersKeepTheirShare() throws Exception {
            for (int i = 1; i <= 5; i++) {
                searchRoute("10.0.1." + i).andExpect(status().isOk());
            }
            searchRoute("10.0.1.6").andExpect(status().isTooManyRequests());
            verify(tmapClient, times(5)).searchTransitRoutes(any());
        }

        @Test
        @WithMockUser(username = MEMBER)
        void memberGetsTenPerDayAndIsNotBlockedByAnonymousTotal() throws Exception {
            rateLimiter.record("api:ROUTE_SEARCH:anonymous-total", RateLimiter.Limit.perDay(5));
            for (int i = 0; i < 10; i++) {
                searchRoute("10.0.0.1").andExpect(status().isOk());
            }
            searchRoute("10.0.0.2")
                    .andExpect(status().isTooManyRequests())
                    .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.not(
                            org.hamcrest.Matchers.containsString("로그인하면"))));
        }
    }

    /** 조회 API: 편 조회·장소 매칭·주변정보를 합쳐 분당 20번. 형식이 틀린 요청도 센다 (입력 검증보다 먼저) */
    @Test
    void anonymousLookupsAreLimitedPerMinuteEvenWhenInvalid() throws Exception {
        for (int i = 0; i < 19; i++) {
            mvc.perform(post("/api/trips").with(csrf()).with(from("10.0.2.1"))
                    .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/place/match").with(csrf()).with(from("10.0.2.1"))
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/trips").with(csrf()).with(from("10.0.2.1"))
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isTooManyRequests());
        mvc.perform(post("/api/trips").with(csrf()).with(from("10.0.2.2"))
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());
    }

    /** 로그인: 비밀번호 대입 막기 */
    @Nested
    class Login {

        private String login(String email, String password, String ip) throws Exception {
            return mvc.perform(post("/login").with(csrf()).with(from(ip))
                    .param("email", email).param("password", password))
                    .andReturn().getResponse().getRedirectedUrl();
        }

        @Test
        void fiveFailuresLockTheAccountEvenForTheRightPassword() throws Exception {
            for (int i = 0; i < 4; i++) {
                assertThat(login(MEMBER, "FAKE_wrong1", "10.0.3.1")).isEqualTo("/login?error");
            }
            assertThat(login(MEMBER, "FAKE_wrong1", "10.0.3.1")).as("다섯 번째 실패").isEqualTo("/login?locked");
            assertThat(login(MEMBER, PASSWORD, "10.0.3.2")).as("잠긴 동안은 맞는 비밀번호도 확인하지 않는다")
                    .isEqualTo("/login?locked");
        }

        @Test
        void unknownEmailIsCountedTheSameWay() throws Exception {
            for (int i = 0; i < 5; i++) {
                login("nobody@test.local", "FAKE_wrong1", "10.0.3.3");
            }
            assertThat(login("nobody@test.local", "FAKE_wrong1", "10.0.3.4")).isEqualTo("/login?locked");
            assertThat(login(" NOBODY@test.local ", "FAKE_wrong1", "10.0.3.4")).as("대소문자·공백만 다른 이메일")
                    .isEqualTo("/login?locked");
        }

        @Test
        void successClearsTheAccountCount() throws Exception {
            for (int i = 0; i < 4; i++) {
                login(MEMBER, "FAKE_wrong1", "10.0.3.5");
            }
            assertThat(login(MEMBER, PASSWORD, "10.0.3.5")).isEqualTo("/home");
            assertThat(login(MEMBER, "FAKE_wrong1", "10.0.3.5")).as("성공 뒤 다시 처음부터 센다").isEqualTo("/login?error");
        }

        @Test
        void oneClientTryingManyEmailsIsLocked() throws Exception {
            for (int i = 0; i < 20; i++) {
                login("spray" + i + "@test.local", "FAKE_wrong1", "10.0.3.6");
            }
            assertThat(login(MEMBER, PASSWORD, "10.0.3.6")).isEqualTo("/login?locked");
            assertThat(login(MEMBER, PASSWORD, "10.0.3.7")).as("다른 곳에서는 로그인된다").isEqualTo("/home");
        }

        @Test
        void returnsOnlyToAllowedInternalPages() throws Exception {
            assertThat(mvc.perform(post("/login").with(csrf()).param("email", MEMBER).param("password", PASSWORD)
                    .param("next", "/route?index=1")).andReturn().getResponse().getRedirectedUrl())
                    .isEqualTo("/route?index=1");
            for (String next : new String[]{"https://evil.example", "//evil.example", "/mypage/delete", "/route?index=1&x=1"}) {
                assertThat(mvc.perform(post("/login").with(csrf()).param("email", MEMBER).param("password", PASSWORD)
                        .param("next", next)).andReturn().getResponse().getRedirectedUrl())
                        .as("next=" + next).isEqualTo("/home");
            }
        }

        @Test
        void failureKeepsTheAllowedNextPage() throws Exception {
            assertThat(mvc.perform(post("/login").with(csrf()).param("email", MEMBER).param("password", "FAKE_wrong1")
                    .param("next", "/route?index=2")).andReturn().getResponse().getRedirectedUrl())
                    .isEqualTo("/login?error&next=%2Froute%3Findex%3D2");
        }
    }

    /** 마이페이지 비밀번호 변경의 현재 비밀번호 */
    @Test
    @WithMockUser(username = MEMBER)
    void currentPasswordIsLockedAfterFiveFailures() throws Exception {
        for (int i = 0; i < 5; i++) {
            changePassword("FAKE_wrong99");
        }
        MvcResult result = changePassword(PASSWORD);
        assertThat(String.valueOf(result.getFlashMap().get("error"))).contains("여러 번 틀렸습니다");
        assertThat(passwordEncoder.matches(PASSWORD,
                userAccountRepository.findByEmail(MEMBER).orElseThrow().getPassword()))
                .as("잠긴 동안은 맞는 비밀번호로도 바뀌지 않는다").isTrue();
    }

    private MvcResult changePassword(String current) throws Exception {
        return mvc.perform(post("/mypage/password").with(csrf()).session(new MockHttpSession())
                .param("currentPassword", current)
                .param("newPassword", "FAKE_newpass1")
                .param("newPasswordConfirm", "FAKE_newpass1")).andReturn();
    }

    /** 회원가입: 한 곳에서 한 시간에 10번 */
    @Test
    void registerIsLimitedPerClient() throws Exception {
        for (int i = 0; i < 10; i++) {
            register("signup" + i + "@test.local").andExpect(status().is3xxRedirection());
        }
        String body = register("signup10@test.local").andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(body).contains("회원가입 가능 횟수를 모두 사용했습니다");
        assertThat(userAccountRepository.existsByEmail("signup10@test.local")).isFalse();
    }

    private ResultActions register(String email) throws Exception {
        return mvc.perform(post("/register").with(csrf()).with(from("10.0.4.1"))
                .param("name", "가입검사").param("email", email).param("password", "FAKE_signup1"));
    }
}
