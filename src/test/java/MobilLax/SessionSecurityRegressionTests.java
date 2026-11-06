package MobilLax;

import MobilLax.Common.RateLimit.RateLimiter;
import MobilLax.Common.Time.KoreaTime;
import MobilLax.Common.Web.RecentPasswordSession;
import MobilLax.Common.Web.SessionLifetime;
import MobilLax.Domain.Account.KeepLoginToken;
import MobilLax.Domain.Account.KeepLoginTokenRepository;
import MobilLax.Domain.Account.UserAccount;
import MobilLax.Domain.Account.UserAccountRepository;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 보안 회귀 테스트 S14 — 세션 시간, 로그인 유지, 비밀번호 재확인 (v2.14, docs/SECURITY_RULES.md)
 * 이 테스트를 지우거나 약하게 바꾸지 않는다. 규칙을 바꿔야 하면 문서와 함께 리뷰를 거친다.
 * 트랜잭션을 되돌려 DB 에 남기지 않는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SessionSecurityRegressionTests {

    private static final String PASSWORD = "FAKE_pw_12345";
    private static final String COOKIE = SessionLifetime.KEEP_LOGIN_COOKIE;
    private static final String CONFIRM_API = "/api/account/confirm-password";

    @Autowired
    MockMvc mvc;

    @Autowired
    UserAccountRepository accounts;

    @Autowired
    KeepLoginTokenRepository tokens;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    RateLimiter rateLimiter;

    @Autowired
    SessionRegistry sessionRegistry;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    EntityManager entityManager;

    @Value("${server.servlet.session.timeout}")
    Duration idleTimeout;

    private String email;
    private UserAccount account;

    @BeforeEach
    void setUp() {
        rateLimiter.clear();
        email = "s14-" + UUID.randomUUID() + "@test.local";
        account = new UserAccount();
        account.setEmail(email);
        account.setName("세션검사");
        account.setPassword(passwordEncoder.encode(PASSWORD));
        account.setRole("USER");
        account = accounts.saveAndFlush(account);
    }

    private MvcResult login(boolean keep) throws Exception {
        var request = post("/login").with(csrf()).param("email", email).param("password", PASSWORD);
        if (keep) {
            request.param("remember-me", "true");
        }
        return mvc.perform(request).andExpect(status().is3xxRedirection()).andReturn();
    }

    private Cookie keepCookie() throws Exception {
        Cookie cookie = login(true).getResponse().getCookie(COOKIE);
        assertThat(cookie).isNotNull();
        return cookie;
    }

    private void age(String column, Duration by) {
        LocalDateTime when = KoreaTime.now().minus(by);
        entityManager.flush(); // 먼저 내보내지 않으면 아직 저장되지 않은 행이 clear 로 사라져 시험이 거짓으로 통과한다
        jdbc.update("update keep_login_token set " + column + " = ? where user_id = ?", when, account.getId());
        entityManager.clear();
    }

    private long tokenCount() {
        entityManager.flush();
        return tokens.findAll().stream().filter(t -> t.getUserId().equals(account.getId())).count();
    }

    private MvcResult openMyPage(Cookie cookie) throws Exception {
        return mvc.perform(get("/mypage").cookie(cookie)).andReturn();
    }

    private void assertLoggedIn(MvcResult result) {
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
    }

    private void assertSentToLogin(MvcResult result) {
        assertThat(result.getResponse().getStatus()).isEqualTo(302);
        assertThat(result.getResponse().getRedirectedUrl()).contains("/login");
    }

    // 세션 시간

    @Test
    void idleTimeoutIsThirtyMinutesAndTheLimitIsFourHours() {
        assertThat(idleTimeout).isEqualTo(Duration.ofMinutes(30));
        assertThat(SessionLifetime.MAX).isEqualTo(Duration.ofHours(4));
    }

    @Test
    void aSessionEndsFourHoursAfterLoginEvenIfActive() throws Exception {
        MockHttpSession session = (MockHttpSession) login(false).getRequest().getSession();
        mvc.perform(get("/mypage").session(session)).andExpect(status().isOk());

        session.setAttribute(SessionLifetime.STARTED_AT, KoreaTime.now().minus(SessionLifetime.MAX).plusMinutes(1));
        mvc.perform(get("/mypage").session(session)).andExpect(status().isOk());

        session.setAttribute(SessionLifetime.STARTED_AT, KoreaTime.now().minus(SessionLifetime.MAX));
        assertSentToLogin(mvc.perform(get("/mypage").session(session)).andReturn());
        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    void anEndedSessionAnswersApiCallsWith401() throws Exception {
        MockHttpSession session = (MockHttpSession) login(false).getRequest().getSession();
        mvc.perform(get("/api/session").session(session)).andExpect(status().isOk());

        session.setAttribute(SessionLifetime.STARTED_AT, KoreaTime.now().minus(SessionLifetime.MAX));
        mvc.perform(get("/api/session").session(session)).andExpect(status().isUnauthorized());
    }

    @Test
    void theTimerNeverPromisesMoreThanTheLimitLeaves() throws Exception {
        MockHttpSession session = (MockHttpSession) login(false).getRequest().getSession();
        session.setMaxInactiveInterval(1800);
        mvc.perform(get("/mypage").session(session)).andExpect(status().isOk());
        session.setAttribute(SessionLifetime.STARTED_AT, KoreaTime.now().minus(SessionLifetime.MAX).plusMinutes(10));

        int left = SessionLifetime.remainingSeconds(session);

        assertThat(left).isBetween(590, 600);
    }

    // 로그인 유지

    @Test
    void keepLoginIsOffUnlessChosen() throws Exception {
        assertThat(login(false).getResponse().getCookie(COOKIE)).isNull();
        assertThat(tokenCount()).isZero();
    }

    @Test
    void theCookieIsHiddenFromScriptsAndTheServerStoresOnlyAHash() throws Exception {
        MvcResult result = login(true);

        String header = result.getResponse().getHeaders(HttpHeaders.SET_COOKIE).stream()
                .filter(value -> value.startsWith(COOKIE + "=")).findFirst().orElseThrow();
        assertThat(header).contains("HttpOnly").contains("SameSite=Lax").contains("Max-Age=" + Duration.ofDays(14).toSeconds());

        String[] parts = decode(result.getResponse().getCookie(COOKIE));
        KeepLoginToken stored = tokens.findById(parts[0]).orElseThrow();
        assertThat(stored.getTokenHash()).isNotEqualTo(parts[1]).hasSize(64);
    }

    private static String[] decode(Cookie cookie) {
        return new String(Base64.getDecoder().decode(cookie.getValue()), StandardCharsets.UTF_8).split(":");
    }

    @Test
    void theCookieLogsBackInAndChangesTheToken() throws Exception {
        Cookie first = keepCookie();

        MvcResult result = openMyPage(first);

        assertLoggedIn(result);
        Cookie second = result.getResponse().getCookie(COOKIE);
        assertThat(second).isNotNull();
        assertThat(second.getValue()).isNotEqualTo(first.getValue());
        assertLoggedIn(openMyPage(second));
    }

    /** 세션이 끝난 순간 화면이 동시에 보낸 요청이 옛 쿠키를 들고 와도 사용자를 로그아웃시키지 않는다 */
    @Test
    void theOldTokenStillWorksForAMoment() throws Exception {
        Cookie first = keepCookie();
        Cookie second = openMyPage(first).getResponse().getCookie(COOKIE);

        MvcResult concurrent = openMyPage(first);

        assertLoggedIn(concurrent);
        assertThat(concurrent.getResponse().getCookie(COOKIE)).as("쿠키를 또 바꾸지 않는다").isNull();
        assertLoggedIn(openMyPage(second));
    }

    /** 바뀐 지 오래된 토큰이 오면 쿠키를 빼앗긴 것이다: 도둑과 주인 모두 비밀번호로 다시 로그인한다 */
    @Test
    void reusingAnOldTokenEndsEveryKeptLogin() throws Exception {
        Cookie stolen = keepCookie();
        Cookie current = openMyPage(stolen).getResponse().getCookie(COOKIE);
        Cookie otherBrowser = keepCookie();
        age("last_used_at", Duration.ofMinutes(1));

        assertSentToLogin(openMyPage(stolen));

        assertThat(tokenCount()).isZero();
        assertSentToLogin(openMyPage(current));
        assertSentToLogin(openMyPage(otherBrowser));
    }

    @Test
    void aTokenUnusedForFourteenDaysEnds() throws Exception {
        Cookie cookie = keepCookie();
        age("last_used_at", Duration.ofDays(14).plusMinutes(1));

        assertSentToLogin(openMyPage(cookie));

        assertThat(tokenCount()).isZero();
    }

    @Test
    void aTokenUsedInsideFourteenDaysLives() throws Exception {
        Cookie cookie = keepCookie();
        age("last_used_at", Duration.ofDays(13));
        age("created_at", Duration.ofDays(13));

        assertLoggedIn(openMyPage(cookie));
    }

    /** 계속 써도 비밀번호로 로그인한 지 30일이 지나면 끝난다 */
    @Test
    void aTokenEndsThirtyDaysAfterPasswordLoginEvenIfUsed() throws Exception {
        Cookie cookie = keepCookie();
        age("created_at", Duration.ofDays(30).plusMinutes(1));

        assertSentToLogin(openMyPage(cookie));

        assertThat(tokenCount()).isZero();
    }

    @Test
    void anEndedSessionLogsBackInWithTheCookie() throws Exception {
        MvcResult loggedIn = login(true);
        MockHttpSession session = (MockHttpSession) loggedIn.getRequest().getSession();
        mvc.perform(get("/mypage").session(session)).andExpect(status().isOk());
        session.setAttribute(SessionLifetime.STARTED_AT, KoreaTime.now().minus(SessionLifetime.MAX));

        MvcResult result = mvc.perform(get("/mypage").session(session).cookie(loggedIn.getResponse().getCookie(COOKIE))).andReturn();

        assertLoggedIn(result);
        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    void administratorsCannotKeepLogin() throws Exception {
        account.setRole("ADMIN");
        accounts.saveAndFlush(account);

        assertThat(login(true).getResponse().getCookie(COOKIE)).isNull();
        assertThat(tokenCount()).isZero();
    }

    /** 로그인 유지 뒤에 관리자가 된 계정도 쿠키로 OTP 앞 단계까지 들어오지 못한다 */
    @Test
    void anAccountPromotedLaterLosesItsToken() throws Exception {
        Cookie cookie = keepCookie();
        account.setRole("ADMIN");
        accounts.saveAndFlush(account);

        assertSentToLogin(openMyPage(cookie));

        assertThat(tokenCount()).isZero();
    }

    @Test
    void suspendedAccountsCannotUseTheirToken() throws Exception {
        Cookie cookie = keepCookie();
        account.setSuspendedAt(KoreaTime.now());
        accounts.saveAndFlush(account);

        assertSentToLogin(openMyPage(cookie));

        assertThat(tokenCount()).isZero();
    }

    /** 세션 등록부에 없으면 관리자가 계정을 정지해도 이 세션을 끝낼 수 없다 (S12) */
    @Test
    void aSessionFromTheCookieIsRegisteredSoSuspensionCanEndIt() throws Exception {
        MvcResult reentry = openMyPage(keepCookie());
        MockHttpSession session = (MockHttpSession) reentry.getRequest().getSession();

        mvc.perform(get("/mypage").session(session)).andExpect(status().isOk());

        assertThat(sessionRegistry.getSessionInformation(session.getId())).isNotNull();
    }

    @Test
    void logoutEndsThisBrowserOnly() throws Exception {
        Cookie thisBrowser = keepCookie();
        Cookie otherBrowser = keepCookie();

        MvcResult logout = mvc.perform(post("/logout").with(csrf()).with(user(email)).cookie(thisBrowser)).andReturn();

        assertThat(logout.getResponse().getCookie(COOKIE).getMaxAge()).isZero();
        assertThat(tokenCount()).isEqualTo(1);
        assertSentToLogin(openMyPage(thisBrowser));
        assertLoggedIn(openMyPage(otherBrowser));
    }

    @Test
    void changingThePasswordEndsEveryKeptLogin() throws Exception {
        Cookie cookie = keepCookie();

        mvc.perform(post("/mypage/password").with(csrf()).with(user(email))
                        .param("currentPassword", PASSWORD)
                        .param("newPassword", "FAKE_new_12345")
                        .param("newPasswordConfirm", "FAKE_new_12345"))
                .andExpect(redirectedUrl("/mypage"));

        assertThat(tokenCount()).isZero();
        assertSentToLogin(openMyPage(cookie));
    }

    @Test
    void withdrawalEndsEveryKeptLogin() throws Exception {
        keepCookie();

        mvc.perform(post("/mypage/delete").with(csrf()).with(user(email)).session(TestSessions.recentPassword()))
                .andExpect(status().is3xxRedirection());

        assertThat(tokenCount()).isZero();
    }

    @Test
    void aForgedCookieIsRefused() throws Exception {
        String forged = Base64.getEncoder().withoutPadding().encodeToString("nope:nope".getBytes(StandardCharsets.UTF_8));

        MvcResult result = openMyPage(new Cookie(COOKIE, forged));

        assertSentToLogin(result);
        assertThat(result.getResponse().getCookie(COOKIE).getMaxAge()).isZero();
    }

    // 비밀번호 재확인

    @Test
    void paymentNeedsARecentPasswordAndTheCookieIsNotEnough() throws Exception {
        mvc.perform(post("/payment/sdk-ready/" + UUID.randomUUID()).with(csrf()).with(user(email)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.reauthRequired").value(true));
        mvc.perform(post("/payment/direct-sdk-ready").with(csrf()).with(user(email))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.reauthRequired").value(true));

        Cookie cookie = keepCookie();
        mvc.perform(post("/payment/sdk-ready/" + UUID.randomUUID()).with(csrf()).cookie(cookie))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.reauthRequired").value(true));
    }

    @Test
    void theProfilePageAndItsSaveNeedARecentPassword() throws Exception {
        mvc.perform(get("/mypage/edit").with(user(email))).andExpect(redirectedUrl("/mypage/confirm-password?next=/mypage/edit"));
        mvc.perform(post("/mypage/edit").with(csrf()).with(user(email)).param("name", "바뀐이름"))
                .andExpect(redirectedUrl("/mypage/confirm-password?next=/mypage/edit"));

        assertThat(accounts.findByEmail(email).orElseThrow().getName()).isEqualTo("세션검사");
    }

    @Test
    void withdrawalNeedsARecentPassword() throws Exception {
        mvc.perform(get("/mypage/withdraw").with(user(email))).andExpect(redirectedUrl("/mypage/confirm-password?next=/mypage/withdraw"));
        mvc.perform(post("/mypage/delete").with(csrf()).with(user(email)))
                .andExpect(redirectedUrl("/mypage/confirm-password?next=/mypage/withdraw"));

        assertThat(accounts.findByEmail(email)).isPresent();
    }

    @Test
    void aCorrectPasswordOpensThePageForFiveMinutes() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/mypage/confirm-password").with(csrf()).with(user(email)).session(session)
                        .param("password", PASSWORD).param("next", "/mypage/edit"))
                .andExpect(redirectedUrl("/mypage/edit"));

        mvc.perform(get("/mypage/edit").with(user(email)).session(session)).andExpect(status().isOk());

        session.setAttribute(RecentPasswordSession.CONFIRMED_AT,
                KoreaTime.now().minus(RecentPasswordSession.VALID_FOR).plusSeconds(5));
        mvc.perform(get("/mypage/edit").with(user(email)).session(session)).andExpect(status().isOk());

        session.setAttribute(RecentPasswordSession.CONFIRMED_AT, KoreaTime.now().minus(RecentPasswordSession.VALID_FOR));
        mvc.perform(get("/mypage/edit").with(user(email)).session(session))
                .andExpect(redirectedUrl("/mypage/confirm-password?next=/mypage/edit"));
    }

    @Test
    void aWrongPasswordOpensNothing() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/mypage/confirm-password").with(csrf()).with(user(email)).session(session)
                        .param("password", "FAKE_wrong").param("next", "/mypage/edit"))
                .andExpect(redirectedUrl("/mypage/confirm-password?next=/mypage/edit"));
        mvc.perform(post(CONFIRM_API).with(csrf()).with(user(email)).session(session)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"FAKE_wrong\"}"))
                .andExpect(status().isBadRequest());

        mvc.perform(get("/mypage/edit").with(user(email)).session(session))
                .andExpect(redirectedUrl("/mypage/confirm-password?next=/mypage/edit"));
    }

    /** 세션을 빌린 사람이 재확인 창으로 비밀번호를 대입하지 못한다: 맞는 값도 잠시 받지 않는다 */
    @Test
    void guessingIsLimitedEvenForTheRightPassword() throws Exception {
        for (int i = 0; i < 5; i++) {
            mvc.perform(post(CONFIRM_API).with(csrf()).with(user(email))
                            .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"FAKE_wrong\"}"))
                    .andExpect(status().isBadRequest());
        }

        mvc.perform(post(CONFIRM_API).with(csrf()).with(user(email))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void theConfirmApiNeedsLoginAndCsrf() throws Exception {
        String body = "{\"password\":\"" + PASSWORD + "\"}";
        mvc.perform(post(CONFIRM_API).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(CONFIRM_API).with(user(email)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void theConfirmApiOpensPayment() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc.perform(post(CONFIRM_API).with(csrf()).with(user(email)).session(session)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk());

        // 재확인을 지나 서비스까지 간다: 없는 묶음이라 400
        mvc.perform(post("/payment/sdk-ready/" + UUID.randomUUID()).with(csrf()).with(user(email)).session(session))
                .andExpect(status().isBadRequest());
    }

    /** 돌아갈 화면은 정해 둔 곳만 받는다 (열린 리다이렉트) */
    @Test
    void theReturnPageMustBeOnTheList() throws Exception {
        for (String next : new String[]{"https://evil.example", "//evil.example", "/admin", "/mypage/edit?x=1"}) {
            MockHttpSession session = new MockHttpSession();
            mvc.perform(post("/mypage/confirm-password").with(csrf()).with(user(email)).session(session)
                            .param("password", PASSWORD).param("next", next))
                    .andExpect(redirectedUrl("/mypage"));
        }
    }

    @Test
    void thePagesDoNotShowThePassword() throws Exception {
        String page = mvc.perform(get("/mypage/confirm-password").with(user(email)).param("next", "/mypage/edit"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat(page).contains("name=\"next\"").contains("value=\"/mypage/edit\"");
        assertThat(mvc.perform(get("/mypage/withdraw").with(user(email)).session(TestSessions.recentPassword()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
                .contains("/mypage/delete").contains("data-confirm");
    }
}
