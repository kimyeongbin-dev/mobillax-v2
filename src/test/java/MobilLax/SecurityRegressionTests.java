package MobilLax;

import MobilLax.Domain.Cart.CartItemEntity;
import MobilLax.Domain.Cart.CartItemRepository;
import MobilLax.Domain.Payment.PaymentEntity;
import MobilLax.Domain.Payment.PaymentRepository;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 보안 회귀 테스트 — 나중에 기능을 고치더라도 깨지면 안 되는 보안 규칙 (v2 보안 작업, docs/SECURITY_RULES.md)
 * 이 테스트를 지우거나 약하게 바꾸지 않는다. 규칙을 바꿔야 하면 문서와 함께 리뷰를 거친다.
 *
 * 실제 보안 설정(SecurityConfig)으로 요청을 보내고, 테스트 데이터는 트랜잭션으로 되돌린다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SecurityRegressionTests {

    private static final String USER_A = "security-a@test.local";
    private static final String USER_B = "security-b@test.local";

    @Autowired
    MockMvc mvc;

    @Autowired
    CartItemRepository cartItemRepository;

    @Autowired
    PaymentRepository paymentRepository;

    /** S2. 로그인하지 않으면 API·결제·개인 화면을 쓸 수 없다 */
    @Nested
    class S2_AnonymousBlocked {

        @Test
        void apiReturns401JsonInsteadOfRedirect() throws Exception {
            mvc.perform(get("/api/cart/list"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("로그인이 필요합니다")));
        }

        @Test
        void everyApiAndPaymentEndpointNeedsLogin() throws Exception {
            String json = "{}";
            mvc.perform(post("/api/cart/add").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json))
                    .andExpect(status().isUnauthorized());
            mvc.perform(post("/api/route/transit").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json))
                    .andExpect(status().isUnauthorized());
            mvc.perform(post("/api/trips").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json))
                    .andExpect(status().isUnauthorized());
            mvc.perform(post("/api/place/match").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json))
                    .andExpect(status().isUnauthorized());
            mvc.perform(get("/api/session")).andExpect(status().isUnauthorized());
            mvc.perform(get("/api/terminals").param("name", "동서울"))
                    .andExpect(status().isUnauthorized());
            mvc.perform(get("/api/nearby").param("category", "CAFE").param("lat", "37.55").param("lon", "126.97"))
                    .andExpect(status().isUnauthorized());
            mvc.perform(post("/payment/sdk-ready/" + UUID.randomUUID()).with(csrf()))
                    .andExpect(status().isUnauthorized());
            mvc.perform(post("/payment/complete").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void privatePagesRedirectToLogin() throws Exception {
            // v2.3: /health/db 는 /health·/health/live(로그인 없이 UP/DOWN 만, S7)로 바뀌어 목록에서 뺐다
            for (String page : new String[]{"/cart", "/mypage", "/mypage/edit", "/mypage/password",
                    "/mypage/payments", "/route", "/compare", "/nearby"}) {
                mvc.perform(get(page)).andExpect(status().is3xxRedirection()).andExpect(redirectedUrlPattern("**/login"));
            }
        }

        @Test
        void publicPagesStayOpen() throws Exception {
            for (String page : new String[]{"/home", "/login", "/register"}) {
                mvc.perform(get(page)).andExpect(status().isOk());
            }
        }

        @Test
        @WithMockUser(username = USER_A)
        void loggedInUserCanUseApi() throws Exception {
            mvc.perform(get("/api/cart/list")).andExpect(status().isOk());
        }
    }

    /** S3. 상태를 바꾸는 요청은 CSRF 토큰이 있어야 하고, 로그아웃은 POST 로만 된다 (토큰 쿠키 발급은 CsrfCookieRegressionTests) */
    @Nested
    class S3_Csrf {

        @Test
        @WithMockUser(username = USER_A)
        void stateChangingRequestWithoutTokenIsForbidden() throws Exception {
            mvc.perform(post("/api/cart/add").contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isForbidden());
            mvc.perform(delete("/api/cart/clear")).andExpect(status().isForbidden());
            mvc.perform(post("/mypage/delete")).andExpect(status().isForbidden());
            mvc.perform(post("/mypage/password")).andExpect(status().isForbidden());
            mvc.perform(post("/mypage/payments/hide")).andExpect(status().isForbidden());
            mvc.perform(post("/payment/complete").contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(username = USER_A)
        void requestWithTokenPassesCsrfCheck() throws Exception {
            int status = mvc.perform(post("/api/cart/add").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"selectedLegs\":[]}")).andReturn().getResponse().getStatus();
            assertThat(status).isNotEqualTo(403);
        }

        @Test
        void loginFormNeedsToken() throws Exception {
            mvc.perform(post("/login").param("email", USER_A).param("password", "FAKE_password"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(username = USER_A)
        void logoutOnlyByPost() throws Exception {
            mvc.perform(get("/logout")).andExpect(result ->
                    assertThat(result.getResponse().getRedirectedUrl()).isNotEqualTo("/login?logout"));
            mvc.perform(post("/logout")).andExpect(status().isForbidden());
            mvc.perform(post("/logout").with(csrf())).andExpect(redirectedUrl("/login?logout"));
        }
    }

    /** S4. 다른 사용자의 장바구니·결제 기록은 보거나 바꿀 수 없다 */
    @Nested
    class S4_Ownership {

        private String cartGroupOf(String email) {
            String groupId = UUID.randomUUID().toString();
            cartItemRepository.save(CartItemEntity.builder()
                    .email(email).mode("TRAIN").route("기차:서울-부산").routeId("00001").routePayment(54200)
                    .startName("서울").endName("부산").departureTime("209912312359")
                    .totalFareGroupId(groupId).isDeleted(false).build());
            return groupId;
        }

        @Test
        @WithMockUser(username = USER_A)
        void cannotSeeOthersCart() throws Exception {
            String groupOfB = cartGroupOf(USER_B);
            String body = mvc.perform(get("/api/cart/list")).andReturn().getResponse().getContentAsString();
            assertThat(body).doesNotContain(groupOfB);
        }

        @Test
        @WithMockUser(username = USER_A)
        void cannotDeleteOthersCartGroup() throws Exception {
            String groupOfB = cartGroupOf(USER_B);
            mvc.perform(delete("/api/cart/group/" + groupOfB).with(csrf()));
            assertThat(cartItemRepository.findActiveByGroupId(USER_B, groupOfB)).hasSize(1);
        }

        @Test
        @WithMockUser(username = USER_A)
        void cannotPrepareOthersPayment() throws Exception {
            String groupOfB = cartGroupOf(USER_B);
            mvc.perform(post("/payment/sdk-ready/" + groupOfB).with(csrf()))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @WithMockUser(username = USER_A)
        void cannotChangeOthersPaymentRecord() throws Exception {
            // 서버가 만드는 형식(payment-...)의 번호: 형식 검사(S10)를 통과한 뒤 소유권 검사(S4)에서 막혀야 한다
            String paymentId = "payment-security-" + UUID.randomUUID();
            paymentRepository.save(PaymentEntity.builder()
                    .email(USER_B).paymentId(paymentId).amount(1000).date(LocalDate.now())
                    .groupId(UUID.randomUUID().toString()).transportType("TRAIN")
                    .status(PaymentEntity.PaymentStatus.SUCCESS).build());

            mvc.perform(post("/payment/fail").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"paymentId\":\"" + paymentId + "\",\"groupId\":\"x\",\"amount\":1,\"transportType\":\"TRAIN\"}"))
                    .andExpect(status().isNotFound());
            assertThat(paymentRepository.findByPaymentId(paymentId).orElseThrow().getStatus())
                    .isEqualTo(PaymentEntity.PaymentStatus.SUCCESS);
        }

        /**
         * 결제 번호 형식(payment-UUID)이 알려져도, 남의 진행 중(PENDING) 결제를 실패로 바꿔 그 사람의 결제를 막을 수 없다.
         * 남의 번호와 없는 번호의 응답이 같아, 응답으로 번호가 있는지 알아낼 수도 없다
         */
        @Test
        @WithMockUser(username = USER_A)
        void cannotFailOthersPendingPaymentOrProbeIt() throws Exception {
            String othersPending = "payment-" + UUID.randomUUID();
            paymentRepository.save(PaymentEntity.builder()
                    .email(USER_B).paymentId(othersPending).amount(1000).date(LocalDate.now())
                    .groupId(UUID.randomUUID().toString()).transportType("TRAIN")
                    .status(PaymentEntity.PaymentStatus.PENDING).build());
            String nonexistent = "payment-" + UUID.randomUUID();

            var others = mvc.perform(post("/payment/fail").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"paymentId\":\"" + othersPending + "\"}")).andReturn().getResponse();
            var missing = mvc.perform(post("/payment/fail").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"paymentId\":\"" + nonexistent + "\"}")).andReturn().getResponse();

            assertThat(others.getStatus()).isEqualTo(404);
            assertThat(others.getContentAsString()).isEqualTo(missing.getContentAsString());
            assertThat(missing.getStatus()).isEqualTo(404);
            assertThat(paymentRepository.findByPaymentId(othersPending).orElseThrow().getStatus())
                    .isEqualTo(PaymentEntity.PaymentStatus.PENDING);
            assertThat(paymentRepository.findByPaymentId(nonexistent)).isEmpty();
        }

        @Test
        @WithMockUser(username = USER_A)
        void unusedCartRestoreApiIsGone() throws Exception {
            mvc.perform(post("/payment/restore-cart").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isNotFound());
        }
    }
}
