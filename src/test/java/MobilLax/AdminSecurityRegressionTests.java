package MobilLax;

import MobilLax.Common.RateLimit.RateLimiter;
import MobilLax.Domain.Account.AccountRoles;
import MobilLax.Domain.Account.UserAccount;
import MobilLax.Domain.Account.UserAccountRepository;
import MobilLax.Domain.Admin.AdminAuditLog;
import MobilLax.Domain.Admin.AdminAuditLogRepository;
import MobilLax.Domain.Admin.AdminOtpRepository;
import MobilLax.Domain.Admin.OtpSecretCipher;
import MobilLax.Domain.Admin.Totp;
import MobilLax.Domain.Payment.PaymentEntity;
import MobilLax.Domain.Payment.PaymentRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.repository.CrudRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 보안 회귀 테스트 S12 — 관리자 (v2.11, docs/SECURITY_RULES.md)
 * 이 테스트를 지우거나 약하게 바꾸지 않는다. 규칙을 바꿔야 하면 문서와 함께 리뷰를 거친다.
 *
 * 실제 로그인 폼으로 들어가 OTP 를 등록·확인하는 흐름 그대로 확인한다.
 * 시계는 고정하고(MockBean Clock) 필요할 때 30초씩 넘겨, 같은 번호 재사용 규칙과 다른 동작을 따로 확인한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminSecurityRegressionTests {

    private static final String ADMIN = "admin-s12@test.local";
    private static final String MEMBER = "member-s12@test.local";
    private static final String PASSWORD = "FAKE_correct1";

    @Autowired
    MockMvc mvc;

    @Autowired
    UserAccountRepository userAccountRepository;

    @Autowired
    AdminOtpRepository otpRepository;

    @Autowired
    AdminAuditLogRepository auditLogRepository;

    @Autowired
    OtpSecretCipher cipher;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    RateLimiter rateLimiter;

    @Autowired
    PaymentRepository paymentRepository;

    @MockBean
    Clock clock;

    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-10-20T03:00:00Z"));
    private Long memberId;
    private Long adminId;

    @BeforeEach
    void setUp() {
        rateLimiter.clear();
        when(clock.instant()).thenAnswer(invocation -> now.get());
        adminId = account(ADMIN, AccountRoles.ADMIN).getId();
        memberId = account(MEMBER, AccountRoles.USER).getId();
    }

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    PlatformTransactionManager transactionManager;

    /**
     * 감사 로그는 실패해도 남도록 별도 트랜잭션(REQUIRES_NEW)으로 저장되어, 테스트 트랜잭션을 되돌려도 남는다.
     * 앱 코드에는 지우는 기능이 없으므로(추가만) 테스트가 남긴 기록만 여기서 따로 지운다
     */
    @AfterEach
    void cleanUp() {
        rateLimiter.clear();
        TransactionTemplate separate = new TransactionTemplate(transactionManager);
        separate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        separate.executeWithoutResult(status ->
                jdbcTemplate.update("DELETE FROM admin_audit_log WHERE admin_email LIKE ?", "%@test.local"));
    }

    private UserAccount account(String email, String role) {
        UserAccount account = new UserAccount();
        account.setEmail(email);
        account.setName("관리자검사");
        account.setPassword(passwordEncoder.encode(PASSWORD));
        account.setRole(role);
        return userAccountRepository.save(account);
    }

    /** 실제 로그인 폼으로 로그인한 세션 */
    private MockHttpSession login(String email, String expectedRedirect) throws Exception {
        MvcResult result = mvc.perform(post("/login").with(csrf()).param("email", email).param("password", PASSWORD))
                .andExpect(redirectedUrl(expectedRedirect)).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    /** 지금 시각의 OTP 번호 (서버에 저장된 비밀값으로 계산: 인증 앱이 하는 일) */
    private String currentCode() {
        byte[] secret = cipher.decrypt(otpRepository.findById(ADMIN).orElseThrow().getSecretEnc());
        return Totp.code(secret, Totp.step(now.get().getEpochSecond()));
    }

    private void nextStep() {
        now.set(now.get().plusSeconds(30));
    }

    /** 로그인 → OTP 등록 → 확인까지 마친 관리자 세션과 복구 코드 */
    @SuppressWarnings("unchecked")
    private List<String> enroll(MockHttpSession session) throws Exception {
        mvc.perform(get("/admin/otp").session(session)).andExpect(status().isOk());
        MvcResult result = mvc.perform(post("/admin/otp/setup").with(csrf()).session(session).param("code", currentCode()))
                .andExpect(status().isOk()).andReturn();
        return (List<String>) result.getModelAndView().getModel().get("recoveryCodes");
    }

    /**
     * 이 테스트 관리자가 남긴 감사 로그. 감사 로그는 별도 트랜잭션으로 커밋되는데, 테스트 트랜잭션은 MySQL 기본 격리 수준
     * (REPEATABLE READ)에서 처음 읽은 시점의 스냅샷만 봐서 나중에 커밋된 행이 보이지 않을 수 있다. 그래서 따로 연 트랜잭션에서 읽는다
     */
    private List<AdminAuditLog.Action> auditActions() {
        TransactionTemplate separate = new TransactionTemplate(transactionManager);
        separate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return separate.execute(status -> auditLogRepository.findTop100ByOrderByIdDesc().stream()
                .filter(log -> ADMIN.equals(log.getAdminEmail()))
                .map(AdminAuditLog::getAction).toList());
    }

    @Nested
    class Access {

        @Test
        void anonymousAndMembersCannotOpenAdminPages() throws Exception {
            mvc.perform(get("/admin")).andExpect(redirectedUrlPattern("**/login"));
            MockHttpSession member = login(MEMBER, "/home");
            mvc.perform(get("/admin").session(member)).andExpect(status().isForbidden());
            mvc.perform(get("/admin/otp").session(member)).andExpect(status().isForbidden());
            mvc.perform(get("/admin/users").session(member)).andExpect(status().isForbidden());
        }

        @Test
        void adminPasswordAloneOnlyOpensTheOtpPage() throws Exception {
            MockHttpSession session = login(ADMIN, "/admin/otp");
            mvc.perform(get("/admin").session(session)).andExpect(redirectedUrl("/admin/otp"));
            mvc.perform(get("/admin/users").session(session)).andExpect(redirectedUrl("/admin/otp"));
            mvc.perform(get("/admin/otp").session(session)).andExpect(status().isOk());
        }

        @Test
        void otpGrantsAdminWithANewSessionIdAndShortTimeout() throws Exception {
            MockHttpSession session = login(ADMIN, "/admin/otp");
            String before = session.getId();
            List<String> codes = enroll(session);

            assertThat(codes).hasSize(8).allMatch(c -> c.matches("[a-z0-9]{4}-[a-z0-9]{4}"));
            assertThat(session.getId()).as("권한이 바뀌면 세션 ID 도 바뀐다").isNotEqualTo(before);
            assertThat(session.getMaxInactiveInterval()).as("관리자 세션 10분").isEqualTo(600);
            mvc.perform(get("/admin").session(session)).andExpect(status().isOk());
            assertThat(auditActions()).contains(AdminAuditLog.Action.OTP_SETUP_STARTED, AdminAuditLog.Action.OTP_ENABLED);
        }

        @Test
        void adminPostsNeedCsrf() throws Exception {
            MockHttpSession session = login(ADMIN, "/admin/otp");
            enroll(session);
            mvc.perform(post("/admin/users/" + memberId + "/suspend").session(session).param("code", "000000"))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    class Otp {

        @Test
        void sameCodeCannotBeUsedTwice() throws Exception {
            enroll(login(ADMIN, "/admin/otp"));
            String used = otpRepository.findById(ADMIN).map(o -> currentCode()).orElseThrow();

            MockHttpSession second = login(ADMIN, "/admin/otp");
            mvc.perform(post("/admin/otp/verify").with(csrf()).session(second).param("code", used))
                    .andExpect(redirectedUrl("/admin/otp"));
            mvc.perform(get("/admin").session(second)).andExpect(redirectedUrl("/admin/otp"));

            nextStep();
            mvc.perform(post("/admin/otp/verify").with(csrf()).session(second).param("code", currentCode()))
                    .andExpect(redirectedUrl("/admin"));
            mvc.perform(get("/admin").session(second)).andExpect(status().isOk());
        }

        @Test
        void fiveWrongCodesLockEvenTheRightCode() throws Exception {
            enroll(login(ADMIN, "/admin/otp"));
            nextStep();
            MockHttpSession session = login(ADMIN, "/admin/otp");
            for (int i = 0; i < 5; i++) {
                mvc.perform(post("/admin/otp/verify").with(csrf()).session(session).param("code", "000000"));
            }
            MvcResult result = mvc.perform(post("/admin/otp/verify").with(csrf()).session(session).param("code", currentCode()))
                    .andExpect(redirectedUrl("/admin/otp")).andReturn();
            assertThat(String.valueOf(result.getFlashMap().get("error"))).contains("여러 번 틀렸습니다");
            assertThat(auditActions()).contains(AdminAuditLog.Action.OTP_FAILED);
        }

        @Test
        void recoveryCodeWorksOnlyOnce() throws Exception {
            String code = enroll(login(ADMIN, "/admin/otp")).get(0);

            MockHttpSession first = login(ADMIN, "/admin/otp");
            mvc.perform(post("/admin/otp/recovery").with(csrf()).session(first).param("recoveryCode", code))
                    .andExpect(redirectedUrl("/admin"));
            MockHttpSession second = login(ADMIN, "/admin/otp");
            mvc.perform(post("/admin/otp/recovery").with(csrf()).session(second).param("recoveryCode", code))
                    .andExpect(redirectedUrl("/admin/otp"));
            assertThat(auditActions()).contains(AdminAuditLog.Action.RECOVERY_CODE_USED);
        }

        @Test
        void secretIsStoredEncrypted() throws Exception {
            MockHttpSession session = login(ADMIN, "/admin/otp");
            String page = mvc.perform(get("/admin/otp").session(session)).andReturn().getResponse().getContentAsString();
            String stored = otpRepository.findById(ADMIN).orElseThrow().getSecretEnc();
            String manualKey = Totp.base32(cipher.decrypt(stored));
            assertThat(page).contains(manualKey).contains("<svg");
            assertThat(stored).doesNotContain(manualKey);
        }
    }

    @Nested
    class Accounts {

        private MockHttpSession elevated() throws Exception {
            MockHttpSession session = login(ADMIN, "/admin/otp");
            enroll(session);
            nextStep();
            return session;
        }

        @Test
        void listMasksEmailsAndNames() throws Exception {
            String page = mvc.perform(get("/admin/users").session(elevated()).param("q", "s12"))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertThat(page).contains("m***@test.local").doesNotContain(MEMBER).contains("관****");
            assertThat(auditActions()).contains(AdminAuditLog.Action.USER_LIST_VIEWED);
        }

        @Test
        void suspendNeedsTheCurrentCodeEndsSessionsAndBlocksLogin() throws Exception {
            MockHttpSession memberSession = login(MEMBER, "/home");
            MockHttpSession admin = elevated();

            mvc.perform(post("/admin/users/" + memberId + "/suspend").with(csrf()).session(admin).param("code", "000000"))
                    .andExpect(redirectedUrl("/admin/users/" + memberId));
            assertThat(userAccountRepository.findById(memberId).orElseThrow().getSuspendedAt()).as("틀린 번호면 정지하지 않음").isNull();
            assertThat(auditActions()).contains(AdminAuditLog.Action.STEP_UP_FAILED);

            mvc.perform(post("/admin/users/" + memberId + "/suspend").with(csrf()).session(admin).param("code", currentCode()))
                    .andExpect(redirectedUrl("/admin/users/" + memberId));
            assertThat(userAccountRepository.findById(memberId).orElseThrow().getSuspendedAt()).isNotNull();
            assertThat(auditActions()).contains(AdminAuditLog.Action.USER_SUSPENDED);

            mvc.perform(get("/mypage").session(memberSession)).andExpect(redirectedUrl("/login?suspended"));
            login(MEMBER, "/login?suspended");
        }

        @Test
        void adminAccountCannotBeSuspended() throws Exception {
            MvcResult result = mvc.perform(post("/admin/users/" + adminId + "/suspend").with(csrf()).session(elevated())
                    .param("code", currentCode())).andReturn();
            assertThat(String.valueOf(result.getFlashMap().get("error"))).contains("관리자 계정은 정지할 수 없습니다");
            assertThat(userAccountRepository.findById(adminId).orElseThrow().getSuspendedAt()).isNull();
        }

        @Test
        void detailShowsPaymentsTheUserHid() throws Exception {
            paymentRepository.save(PaymentEntity.builder().email(MEMBER).paymentId("payment-s12-hidden").amount(21600)
                    .date(java.time.LocalDate.of(2026, 10, 20)).groupId("g-s12").transportType("TRAIN")
                    .status(PaymentEntity.PaymentStatus.SUCCESS).orderName("서울 → 대전")
                    .hiddenAt(java.time.LocalDateTime.of(2026, 10, 20, 12, 0)).build());
            String page = mvc.perform(get("/admin/users/" + memberId).session(elevated()))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertThat(page).contains("서울 → 대전").contains("사용자가 숨김").contains("21,600원");
            assertThat(auditActions()).contains(AdminAuditLog.Action.USER_DETAIL_VIEWED);
        }
    }

    /** 감사 로그는 추가만: 저장소에 지우기·덮어쓰기가 없고, 엔티티에 수정자가 없다 */
    @Test
    void auditLogIsAppendOnly() {
        assertThat(CrudRepository.class.isAssignableFrom(AdminAuditLogRepository.class)).isFalse();
        List<String> repositoryMethods = Arrays.stream(AdminAuditLogRepository.class.getMethods()).map(Method::getName).toList();
        assertThat(repositoryMethods).noneMatch(name -> name.startsWith("delete") || name.startsWith("saveAll") || name.startsWith("update"));
        assertThat(Arrays.stream(AdminAuditLog.class.getDeclaredMethods()).map(Method::getName))
                .noneMatch(name -> name.startsWith("set"));
    }
}
