package MobilLax;

import MobilLax.Domain.Payment.PaymentEntity;
import MobilLax.Domain.Payment.PaymentRepository;
import MobilLax.Domain.Account.UserAccount;
import MobilLax.Domain.Account.UserAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * v2.5 마이페이지: 비밀번호 변경, 예매 내역, 공통 메뉴·세션 타이머
 * 테스트 데이터는 트랜잭션으로 되돌린다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MyPageFeatureTests {

    private static final String USER = "mypage-test@test.local";
    private static final String OTHER = "mypage-other@test.local";
    private static final String PASSWORD = "FAKE_current1";

    @Autowired
    MockMvc mvc;

    @Autowired
    UserAccountRepository userAccountRepository;

    @Autowired
    PaymentRepository paymentRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @BeforeEach
    void user() {
        UserAccount account = new UserAccount();
        account.setEmail(USER);
        account.setName("마이페이지검사");
        account.setPassword(passwordEncoder.encode(PASSWORD));
        account.setRole("USER");
        userAccountRepository.save(account);
    }

    private String storedHash() {
        return userAccountRepository.findByEmail(USER).orElseThrow().getPassword();
    }

    @Nested
    @WithMockUser(username = USER)
    class PasswordChange {

        private MvcResult change(String current, String next, String confirm, MockHttpSession session) throws Exception {
            return mvc.perform(post("/mypage/password").with(csrf()).session(session)
                    .param("currentPassword", current)
                    .param("newPassword", next)
                    .param("newPasswordConfirm", confirm)).andReturn();
        }

        private void assertRejected(MvcResult result, String messagePart) {
            assertThat(result.getResponse().getRedirectedUrl()).isEqualTo("/mypage/password");
            assertThat(String.valueOf(result.getFlashMap().get("error"))).contains(messagePart);
            assertThat(passwordEncoder.matches(PASSWORD, storedHash())).as("비밀번호가 바뀌지 않아야 한다").isTrue();
        }

        @Test
        void wrongCurrentPasswordIsRejected() throws Exception {
            assertRejected(change("FAKE_wrong99", "FAKE_newpass1", "FAKE_newpass1", new MockHttpSession()),
                    "현재 비밀번호가 맞지 않습니다");
        }

        @Test
        void weakOrMismatchedNewPasswordIsRejected() throws Exception {
            assertRejected(change(PASSWORD, "short", "short", new MockHttpSession()), "8자 이상");
            assertRejected(change(PASSWORD, "FAKE_newpass1", "FAKE_newpass2", new MockHttpSession()), "확인이 다릅니다");
            assertRejected(change(PASSWORD, "가".repeat(25), "가".repeat(25), new MockHttpSession()), "너무 깁니다");
            assertRejected(change("", "FAKE_newpass1", "FAKE_newpass1", new MockHttpSession()), "현재 비밀번호를 입력");
        }

        @Test
        void sameAsCurrentIsRejected() throws Exception {
            assertRejected(change(PASSWORD, PASSWORD, PASSWORD, new MockHttpSession()), "달라야 합니다");
        }

        @Test
        void validChangeUpdatesHashAndRotatesSession() throws Exception {
            MockHttpSession session = new MockHttpSession();
            String before = session.getId();

            MvcResult result = change(PASSWORD, "FAKE_newpass1", "FAKE_newpass1", session);

            assertThat(result.getResponse().getRedirectedUrl()).isEqualTo("/mypage");
            assertThat(passwordEncoder.matches("FAKE_newpass1", storedHash())).isTrue();
            assertThat(passwordEncoder.matches(PASSWORD, storedHash())).isFalse();
            assertThat(session.getId()).as("세션 고정 방지: 성공하면 세션 ID 가 바뀐다").isNotEqualTo(before);
        }

        @Test
        void changeNeedsCsrfToken() throws Exception {
            int status = mvc.perform(post("/mypage/password")
                    .param("currentPassword", PASSWORD).param("newPassword", "FAKE_newpass1")
                    .param("newPasswordConfirm", "FAKE_newpass1")).andReturn().getResponse().getStatus();
            assertThat(status).isEqualTo(403);
            assertThat(passwordEncoder.matches(PASSWORD, storedHash())).isTrue();
        }
    }

    @Nested
    @WithMockUser(username = USER)
    class PaymentHistory {

        private PaymentEntity payment(String email, PaymentEntity.PaymentStatus status, String orderName) {
            return paymentRepository.save(PaymentEntity.builder()
                    .email(email).paymentId("payment-" + UUID.randomUUID()).amount(4800).date(LocalDate.now())
                    .groupId(UUID.randomUUID().toString()).transportType("TRAIN").status(status)
                    .orderName(orderName).departureTime("202610180702").build());
        }

        @Test
        void showsOnlyMyConfirmedAndFailedPayments() throws Exception {
            payment(USER, PaymentEntity.PaymentStatus.SUCCESS, "내완료역 → 영등포");
            payment(USER, PaymentEntity.PaymentStatus.FAIL, "내실패역 → 영등포");
            payment(USER, PaymentEntity.PaymentStatus.PENDING, "내준비역 → 영등포");
            payment(OTHER, PaymentEntity.PaymentStatus.SUCCESS, "남의역 → 영등포");

            String body = mvc.perform(get("/mypage/payments")).andReturn().getResponse().getContentAsString();

            assertThat(body).contains("내완료역", "결제 완료", "내실패역", "결제 실패·취소", "₩4,800", "10월 18일 07:02 출발");
            assertThat(body).as("준비만 한 결제는 보이지 않는다").doesNotContain("내준비역");
            assertThat(body).as("다른 사용자 결제는 보이지 않는다 (S4)").doesNotContain("남의역");
        }

        @Test
        void storedTextIsEscapedOnScreen() throws Exception {
            payment(USER, PaymentEntity.PaymentStatus.SUCCESS, "<script>alert(1)</script>");
            String body = mvc.perform(get("/mypage/payments")).andReturn().getResponse().getContentAsString();
            assertThat(body).doesNotContain("<script>alert(1)</script>").contains("&lt;script&gt;");
        }

        private int hide(String paymentId) throws Exception {
            return mvc.perform(post("/mypage/payments/hide").with(csrf()).param("paymentId", paymentId))
                    .andReturn().getResponse().getStatus();
        }

        /** 내역에서 삭제는 화면에서만 숨기고 기록은 남긴다 (법정 보존, 중복 결제 확인·환불·분쟁 대응) */
        @Test
        void hideRemovesFromListButKeepsRecord() throws Exception {
            PaymentEntity paid = payment(USER, PaymentEntity.PaymentStatus.SUCCESS, "숨길역 → 영등포");
            payment(USER, PaymentEntity.PaymentStatus.SUCCESS, "남길역 → 영등포");

            assertThat(hide(paid.getPaymentId())).isEqualTo(302);

            String body = mvc.perform(get("/mypage/payments")).andReturn().getResponse().getContentAsString();
            assertThat(body).doesNotContain("숨길역").contains("남길역", "1:1 문의로 요청");
            PaymentEntity kept = paymentRepository.findByPaymentId(paid.getPaymentId()).orElseThrow();
            assertThat(kept.getHiddenAt()).isNotNull();
            assertThat(kept.getStatus()).isEqualTo(PaymentEntity.PaymentStatus.SUCCESS);
            // 같은 묶음을 다시 결제하지 못하게 막는 검사(S1)는 숨긴 기록도 본다
            assertThat(paymentRepository.existsByGroupIdAndTransportTypeAndStatus(
                    paid.getGroupId(), paid.getTransportType(), PaymentEntity.PaymentStatus.SUCCESS)).isTrue();
        }

        /** 이번 달 결제 합계는 사용자가 보는 예매 내역과 맞아야 한다: 숨긴 결제는 빼고 센다 */
        @Test
        void monthlyTotalExcludesHiddenPayments() throws Exception {
            PaymentEntity hidden = payment(USER, PaymentEntity.PaymentStatus.SUCCESS, "숨길역 → 영등포");
            payment(USER, PaymentEntity.PaymentStatus.SUCCESS, "남길역 → 영등포");
            payment(USER, PaymentEntity.PaymentStatus.FAIL, "실패역 → 영등포");

            assertThat(mvc.perform(get("/mypage")).andReturn().getResponse().getContentAsString())
                    .contains("이번 달 결제 ₩9,600");
            assertThat(hide(hidden.getPaymentId())).isEqualTo(302);
            assertThat(mvc.perform(get("/mypage")).andReturn().getResponse().getContentAsString())
                    .contains("이번 달 결제 ₩4,800");
        }

        @Test
        void cannotHideOthersPendingOrUnknownPayments() throws Exception {
            PaymentEntity others = payment(OTHER, PaymentEntity.PaymentStatus.SUCCESS, "남의역 → 영등포");
            PaymentEntity pending = payment(USER, PaymentEntity.PaymentStatus.PENDING, "준비역 → 영등포");

            assertThat(hide(others.getPaymentId())).as("남의 결제").isEqualTo(404);
            assertThat(hide(pending.getPaymentId())).as("내역에 없는 준비 결제").isEqualTo(404);
            assertThat(hide("payment-" + UUID.randomUUID())).as("없는 번호").isEqualTo(404);
            assertThat(hide("x' OR '1'='1")).as("형식이 틀린 번호").isEqualTo(400);

            assertThat(paymentRepository.findByPaymentId(others.getPaymentId()).orElseThrow().getHiddenAt()).isNull();
            assertThat(paymentRepository.findByPaymentId(pending.getPaymentId()).orElseThrow().getHiddenAt()).isNull();
        }

        @Test
        void hideNeedsCsrfToken() throws Exception {
            PaymentEntity paid = payment(USER, PaymentEntity.PaymentStatus.SUCCESS, "토큰역 → 영등포");
            int status = mvc.perform(post("/mypage/payments/hide").param("paymentId", paid.getPaymentId()))
                    .andReturn().getResponse().getStatus();
            assertThat(status).isEqualTo(403);
            assertThat(paymentRepository.findByPaymentId(paid.getPaymentId()).orElseThrow().getHiddenAt()).isNull();
        }

        @Test
        void emptyHistoryShowsGuide() throws Exception {
            String body = mvc.perform(get("/mypage/payments")).andReturn().getResponse().getContentAsString();
            assertThat(body).contains("아직 결제한 예매가 없습니다");
        }
    }

    @Nested
    class LayoutAndSession {

        @Test
        @WithMockUser(username = USER)
        void loggedInPagesShowMenuAndSessionTimer() throws Exception {
            MockHttpSession session = new MockHttpSession();
            session.setMaxInactiveInterval(1800);
            for (String page : new String[]{"/home", "/cart", "/mypage", "/mypage/payments", "/mypage/password", "/mypage/edit"}) {
                String body = mvc.perform(get(page).session(session)).andReturn().getResponse().getContentAsString();
                assertThat(body).as(page).contains(
                        "name=\"session-timeout\" content=\"1800\"", "data-session-timer",
                        "href=\"/cart\"", "href=\"/mypage/payments\"", "href=\"/mypage\"");
            }
        }

        @Test
        void anonymousPagesHaveNoSessionTimer() throws Exception {
            String body = mvc.perform(get("/home")).andReturn().getResponse().getContentAsString();
            assertThat(body).doesNotContain("session-timeout", "data-session-timer").contains("href=\"/login\"");
        }

        @Test
        @WithMockUser(username = USER)
        void extendReturnsTimeout() throws Exception {
            MockHttpSession session = new MockHttpSession();
            session.setMaxInactiveInterval(1800);
            String body = mvc.perform(get("/api/session").session(session)).andReturn().getResponse().getContentAsString();
            assertThat(body).isEqualTo("{\"timeoutSeconds\":1800}");
        }

        @Test
        void extendNeedsLogin() throws Exception {
            assertThat(mvc.perform(get("/api/session")).andReturn().getResponse().getStatus()).isEqualTo(401);
        }
    }
}
