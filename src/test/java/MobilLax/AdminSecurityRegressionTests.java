package MobilLax;

import MobilLax.Common.RateLimit.RateLimiter;
import MobilLax.Common.Time.KoreaTime;
import MobilLax.Domain.Account.AccountRoles;
import MobilLax.Domain.Account.UserAccount;
import MobilLax.Domain.Account.UserAccountRepository;
import MobilLax.Domain.Admin.AdminAuditLog;
import MobilLax.Domain.Admin.AdminAuditLogRepository;
import MobilLax.Domain.Admin.AdminAuditService;
import MobilLax.Domain.Admin.AdminOtpRepository;
import MobilLax.Domain.Admin.AdminStatsService;
import MobilLax.Domain.Admin.OtpSecretCipher;
import MobilLax.Domain.Admin.Totp;
import MobilLax.Domain.Inquiry.Inquiry;
import MobilLax.Domain.Inquiry.InquiryForm;
import MobilLax.Domain.Inquiry.InquiryRepository;
import MobilLax.Domain.Inquiry.InquiryService;
import MobilLax.Domain.Payment.PaymentEntity;
import MobilLax.Domain.Payment.PaymentRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
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
        void revealNeedsTheCurrentCodeAndAReasonAndIsAudited() throws Exception {
            MockHttpSession admin = elevated();
            String path = "/admin/users/" + memberId + "/reveal";

            mvc.perform(post(path).with(csrf()).session(admin).param("code", "000000").param("reason", "문의 답변"))
                    .andExpect(redirectedUrl("/admin/users/" + memberId));
            mvc.perform(post(path).with(csrf()).session(admin).param("code", currentCode()))
                    .andExpect(status().isBadRequest());

            String page = mvc.perform(post(path).with(csrf()).session(admin).param("code", currentCode()).param("reason", "문의 답변"))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertThat(page).contains(MEMBER).contains("관리자검사");
            assertThat(auditActions()).contains(AdminAuditLog.Action.STEP_UP_FAILED, AdminAuditLog.Action.PII_REVEALED);

            String detail = mvc.perform(get("/admin/users/" + memberId).session(admin))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertThat(detail).as("다시 열면 가려진다 (세션에 남기지 않음)").doesNotContain(MEMBER);
        }

        @Test
        void revealIsLimitedPerHourEvenWithTheRightCode() throws Exception {
            MockHttpSession admin = elevated();
            String path = "/admin/users/" + memberId + "/reveal";
            for (int i = 0; i < 10; i++) {
                mvc.perform(post(path).with(csrf()).session(admin).param("code", currentCode()).param("reason", "점검"))
                        .andExpect(status().isOk());
                nextStep();
            }
            MvcResult result = mvc.perform(post(path).with(csrf()).session(admin).param("code", currentCode()).param("reason", "점검"))
                    .andExpect(redirectedUrl("/admin/users/" + memberId)).andReturn();
            assertThat(String.valueOf(result.getFlashMap().get("error"))).contains("개인정보 열람 가능 횟수");
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

    /** 1:1 문의 답변 (v2.12): 이메일 없이 계정 ID 로만, 답변은 OTP 재확인, 한 번만, 감사 로그 */
    @Nested
    class Inquiries {

        @Autowired
        InquiryService inquiryService;

        @Autowired
        InquiryRepository inquiryRepository;

        private Long memberInquiry() {
            InquiryForm form = new InquiryForm();
            form.setCategory(Inquiry.Category.BOOKING);
            form.setTitle("숨긴 결제를 보고 싶어요");
            form.setContent("지난주 결제를 내역에서 지웠습니다.");
            return inquiryService.create(MEMBER, form);
        }

        private MockHttpSession elevated() throws Exception {
            MockHttpSession session = login(ADMIN, "/admin/otp");
            enroll(session);
            nextStep();
            return session;
        }

        @Test
        void adminSeesAccountIdButNoEmail() throws Exception {
            Long id = memberInquiry();
            MockHttpSession admin = elevated();
            String list = mvc.perform(get("/admin/inquiries").session(admin))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertThat(list).contains("숨긴 결제를 보고 싶어요").doesNotContain(MEMBER);
            String detail = mvc.perform(get("/admin/inquiries/" + id).session(admin))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertThat(detail).contains("계정 #" + memberId).doesNotContain(MEMBER);
            assertThat(auditActions()).contains(AdminAuditLog.Action.INQUIRY_LIST_VIEWED, AdminAuditLog.Action.INQUIRY_VIEWED);
        }

        @Test
        void answerNeedsTheCurrentCodeAndHappensOnce() throws Exception {
            Long id = memberInquiry();
            MockHttpSession admin = elevated();
            String path = "/admin/inquiries/" + id + "/answer";

            mvc.perform(post(path).with(csrf()).session(admin).param("code", "000000").param("answer", "확인해 드렸습니다."))
                    .andExpect(redirectedUrl("/admin/inquiries/" + id));
            assertThat(inquiryRepository.findById(id).orElseThrow().answered()).as("틀린 번호면 답하지 않음").isFalse();
            assertThat(auditActions()).contains(AdminAuditLog.Action.STEP_UP_FAILED);

            mvc.perform(post(path).with(csrf()).session(admin).param("code", currentCode()).param("answer", "확인해 드렸습니다."))
                    .andExpect(redirectedUrl("/admin/inquiries/" + id));
            Inquiry answered = inquiryRepository.findById(id).orElseThrow();
            assertThat(answered.answered()).isTrue();
            assertThat(answered.getAnswer()).isEqualTo("확인해 드렸습니다.");
            assertThat(auditActions()).contains(AdminAuditLog.Action.INQUIRY_ANSWERED);

            nextStep();
            MvcResult again = mvc.perform(post(path).with(csrf()).session(admin).param("code", currentCode()).param("answer", "바꾼 답변"))
                    .andReturn();
            assertThat(String.valueOf(again.getFlashMap().get("error"))).contains("이미 답변한 문의");
            assertThat(inquiryRepository.findById(id).orElseThrow().getAnswer()).as("보낸 답변은 바뀌지 않음").isEqualTo("확인해 드렸습니다.");
        }

        @Test
        void memberSeesTheAnswerAsCustomerCenterAndNewBadgeClearsAfterReading() throws Exception {
            Long id = memberInquiry();
            MockHttpSession admin = elevated();
            mvc.perform(post("/admin/inquiries/" + id + "/answer").with(csrf()).session(admin)
                    .param("code", currentCode()).param("answer", "확인해 드렸습니다."));

            MockHttpSession member = login(MEMBER, "/home");
            assertThat(mvc.perform(get("/mypage").session(member)).andReturn().getResponse().getContentAsString())
                    .contains("새 답변 1건");
            String page = mvc.perform(get("/mypage/inquiries/" + id).session(member))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertThat(page).contains("확인해 드렸습니다.").contains("MobilLax 고객센터").doesNotContain(ADMIN);
            assertThat(mvc.perform(get("/mypage").session(member)).andReturn().getResponse().getContentAsString())
                    .doesNotContain("새 답변");
        }

        @Test
        void membersCannotAnswer() throws Exception {
            Long id = memberInquiry();
            MockHttpSession member = login(MEMBER, "/home");
            mvc.perform(post("/admin/inquiries/" + id + "/answer").with(csrf()).session(member)
                    .param("code", "123456").param("answer", "스스로 답변")).andExpect(status().isForbidden());
            assertThat(inquiryRepository.findById(id).orElseThrow().answered()).isFalse();
        }
    }

    /** v2.13: 통계와 감사 로그 정기 점검 */
    @Nested
    class StatsAndAuditReview {

        /** 시험 기록과 실제 기록이 겹치지 않는 오래전 달 */
        private static final String OLD_MONTH = "2001-01";

        @Autowired
        AdminStatsService statsService;

        @Autowired
        AdminAuditService auditService;

        @PersistenceContext
        EntityManager entityManager;

        /** 합계는 네이티브 문장으로 더하므로, 테스트 트랜잭션이 먼저 읽어 둔 엔티티를 비워야 새 값이 보인다 */
        private AdminStatsService.DayRow freshToday() {
            entityManager.flush();
            entityManager.clear();
            return statsService.today();
        }

        private MockHttpSession elevated() throws Exception {
            MockHttpSession session = login(ADMIN, "/admin/otp");
            enroll(session);
            nextStep();
            return session;
        }

        /** 감사 로그는 별도 트랜잭션으로 커밋된다. 테스트 트랜잭션의 스냅샷 밖에서 화면을 열어 그 기록을 본다 */
        private String outsideTestTransaction(MockHttpSession session, String path) {
            TransactionTemplate outside = new TransactionTemplate(transactionManager);
            outside.setPropagationBehavior(TransactionDefinition.PROPAGATION_NOT_SUPPORTED);
            return outside.execute(status -> {
                try {
                    return mvc.perform(get(path).session(session)).andReturn().getResponse().getContentAsString();
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            });
        }

        @Test
        void membersCannotOpenStatsOrAudit() throws Exception {
            MockHttpSession member = login(MEMBER, "/home");
            mvc.perform(get("/admin/stats").session(member)).andExpect(status().isForbidden());
            mvc.perform(get("/admin/audit").session(member)).andExpect(status().isForbidden());
            mvc.perform(post("/admin/audit/review").with(csrf()).session(member).param("month", OLD_MONTH).param("code", "123456"))
                    .andExpect(status().isForbidden());
            mvc.perform(get("/admin/stats")).andExpect(redirectedUrlPattern("**/login"));
        }

        @Test
        void statsShowDailyTotalsWithoutAccountsAndAreAudited() throws Exception {
            AdminStatsService.DayRow before = statsService.today();
            paymentRepository.saveAndFlush(PaymentEntity.builder().email(MEMBER).amount(5000).date(KoreaTime.today())
                    .paymentId("stats-" + java.util.UUID.randomUUID()).transportType("TRAIN").groupId("stats-group")
                    .status(PaymentEntity.PaymentStatus.SUCCESS).build());
            paymentRepository.saveAndFlush(PaymentEntity.builder().email(MEMBER).amount(9000).date(KoreaTime.today())
                    .paymentId("stats-" + java.util.UUID.randomUUID()).transportType("TRAIN").groupId("stats-group")
                    .status(PaymentEntity.PaymentStatus.PENDING).build());

            AdminStatsService.DayRow after = statsService.today();
            assertThat(after.payments()).as("완료한 결제만").isEqualTo(before.payments() + 1);
            assertThat(after.paymentAmount()).isEqualTo(before.paymentAmount() + 5000);

            MockHttpSession admin = elevated();
            String html = mvc.perform(get("/admin/stats").param("range", "MONTH").session(admin))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertThat(html).contains("최근 30일").doesNotContain(MEMBER);
            assertThat(auditActions()).contains(AdminAuditLog.Action.STATS_VIEWED);

            mvc.perform(get("/admin/stats").param("range", "FOREVER").session(admin)).andExpect(status().isBadRequest());
        }

        @Test
        void adminRequestsAreNotCountedAsUsage() throws Exception {
            MockHttpSession admin = elevated();
            AdminStatsService.DayRow before = freshToday();
            String browser = "Mozilla/5.0 admin-" + java.util.UUID.randomUUID();
            mvc.perform(get("/admin/stats").header("User-Agent", browser).session(admin)).andExpect(status().isOk());
            mvc.perform(get("/home").header("User-Agent", browser).session(admin)).andExpect(status().isOk());
            AdminStatsService.DayRow after = freshToday();
            assertThat(after.pageViews()).isEqualTo(before.pageViews());
            assertThat(after.visitors()).isEqualTo(before.visitors());
        }

        @Test
        void auditFiltersByMonthActionAndTarget() throws Exception {
            MockHttpSession admin = elevated();
            auditService.record(ADMIN, AdminAuditLog.Action.PII_REVEALED, memberId, "사유: 필터 확인용", "127.0.0.1");
            String month = java.time.YearMonth.from(KoreaTime.today()).toString();

            assertThat(outsideTestTransaction(admin, "/admin/audit?month=" + month + "&action=PII_REVEALED"))
                    .contains("사유: 필터 확인용");
            assertThat(outsideTestTransaction(admin, "/admin/audit?month=" + month + "&action=PII_REVEALED&target=" + memberId))
                    .contains("사유: 필터 확인용");
            assertThat(outsideTestTransaction(admin, "/admin/audit?month=" + month + "&action=USER_SUSPENDED"))
                    .doesNotContain("사유: 필터 확인용");
            assertThat(outsideTestTransaction(admin, "/admin/audit?month=" + OLD_MONTH + "&action=PII_REVEALED"))
                    .doesNotContain("사유: 필터 확인용");
            assertThat(auditActions()).contains(AdminAuditLog.Action.AUDIT_VIEWED);

            mvc.perform(get("/admin/audit").param("month", "2026-13").session(admin)).andExpect(status().isBadRequest());
            mvc.perform(get("/admin/audit").param("action", "NOPE").session(admin)).andExpect(status().isBadRequest());
        }

        @Test
        void reviewNeedsCsrfAndTheCurrentCodeAndHappensOnce() throws Exception {
            MockHttpSession admin = elevated();
            String path = "/admin/audit/review";

            mvc.perform(post(path).session(admin).param("month", OLD_MONTH).param("code", "123456"))
                    .andExpect(status().isForbidden());

            mvc.perform(post(path).with(csrf()).session(admin).param("month", OLD_MONTH).param("code", "000000"))
                    .andExpect(redirectedUrl("/admin/audit?month=" + OLD_MONTH));
            assertThat(auditActions()).contains(AdminAuditLog.Action.STEP_UP_FAILED)
                    .doesNotContain(AdminAuditLog.Action.AUDIT_REVIEWED);

            mvc.perform(post(path).with(csrf()).session(admin).param("month", OLD_MONTH).param("code", currentCode()))
                    .andExpect(redirectedUrl("/admin/audit?month=" + OLD_MONTH));
            assertThat(auditActions()).contains(AdminAuditLog.Action.AUDIT_REVIEWED);

            nextStep();
            String code = currentCode();
            TransactionTemplate outside = new TransactionTemplate(transactionManager);
            outside.setPropagationBehavior(TransactionDefinition.PROPAGATION_NOT_SUPPORTED);
            Object error = outside.execute(status -> {
                try {
                    return mvc.perform(post(path).with(csrf()).session(admin).param("month", OLD_MONTH).param("code", code))
                            .andReturn().getFlashMap().get("error");
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            });
            assertThat(error).as("두 번은 남기지 않음").isEqualTo("이미 점검을 마친 달입니다.");
        }

        @Test
        void onlyFinishedMonthsCanBeReviewed() throws Exception {
            MockHttpSession admin = elevated();
            String thisMonth = java.time.YearMonth.from(KoreaTime.today()).toString();
            Object error = mvc.perform(post("/admin/audit/review").with(csrf()).session(admin)
                            .param("month", thisMonth).param("code", currentCode()))
                    .andReturn().getFlashMap().get("error");
            assertThat(error).isEqualTo("끝난 달만 점검 완료로 남길 수 있습니다.");
            assertThat(auditActions()).doesNotContain(AdminAuditLog.Action.AUDIT_REVIEWED);
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
