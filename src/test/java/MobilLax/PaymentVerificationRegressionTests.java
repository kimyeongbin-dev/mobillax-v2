package MobilLax;

import MobilLax.Domain.Cart.CartItemEntity;
import MobilLax.Domain.Cart.CartItemRepository;
import MobilLax.Domain.Cart.DepartureCheck;
import MobilLax.Domain.Payment.PaymentEntity;
import MobilLax.Domain.Payment.PaymentRepository;
import MobilLax.Domain.Payment.PortOneClient;
import MobilLax.Domain.Place.TransitKind;
import MobilLax.Domain.Trip.TagoTripClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 보안 회귀 테스트 S1 — 결제 금액은 서버가 정하고, PortOne 에서 확인한 뒤에만 완료로 기록한다 (docs/SECURITY_RULES.md)
 * 이 테스트를 지우거나 약하게 바꾸지 않는다. 규칙을 바꿔야 하면 문서와 함께 리뷰를 거친다.
 *
 * TAGO·PortOne 은 가짜(MockBean)로 바꿔 외부 호출 없이 위조 상황을 만든다. 테스트 데이터는 트랜잭션으로 되돌린다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PaymentVerificationRegressionTests {

    private static final String USER_A = "payment-a@test.local";
    private static final String USER_B = "payment-b@test.local";
    private static final int FARE = 54200;
    private static final String TRAIN_NO = "00101";

    @Autowired
    MockMvc mvc;

    @Autowired
    CartItemRepository cartItemRepository;

    @Autowired
    PaymentRepository paymentRepository;

    @MockBean
    PortOneClient portOneClient;

    @MockBean
    TagoTripClient tagoTripClient;

    @Value("${portone_store}")
    String storeId;

    /** 오늘 23:59 출발편 (당일 예매 규칙을 지키는 아직 출발하지 않은 편) */
    private final String departAt = DepartureCheck.now().substring(0, 8) + "2359";

    @BeforeEach
    void tagoHasTrain() {
        when(tagoTripClient.trips(eq(TransitKind.TRAIN), eq("NAT010000"), eq("NAT014445"), anyString()))
                .thenReturn(List.of(new TagoTripClient.Trip(TRAIN_NO, "KTX", departAt, departAt, FARE)));
    }

    private String legJson(int fare, String departureTime) {
        return """
                {"selectedLegs":[{"mode":"TRAIN","route":"기차:서울-부산","routeId":"%s","routePayment":%d,
                "startName":"서울","endName":"부산","departureTime":"%s",
                "kind":"TRAIN","depId":"NAT010000","arrId":"NAT014445"}]}
                """.formatted(TRAIN_NO, fare, departureTime);
    }

    private ResultActions postJson(String url, String body) throws Exception {
        return mvc.perform(post(url).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String cartGroupOf(String email, Boolean fareVerified) {
        String groupId = UUID.randomUUID().toString();
        cartItemRepository.save(CartItemEntity.builder()
                .email(email).mode("TRAIN").route("기차:서울-부산").routeId(TRAIN_NO).routePayment(FARE)
                .startName("서울").endName("부산").departureTime(departAt)
                .totalFareGroupId(groupId).isDeleted(false).fareVerified(fareVerified).build());
        return groupId;
    }

    private PaymentEntity paymentOf(String email, String groupId, PaymentEntity.PaymentStatus status) {
        return paymentRepository.save(PaymentEntity.builder()
                .email(email).paymentId("payment-test-" + UUID.randomUUID()).amount(FARE).date(LocalDate.now())
                .groupId(groupId).transportType("TRAIN").status(status).build());
    }

    private String completeBody(String paymentId) {
        // 브라우저가 금액을 보내더라도 서버는 쓰지 않는다
        return "{\"paymentId\":\"" + paymentId + "\",\"amount\":1}";
    }

    private PaymentEntity.PaymentStatus statusOf(String paymentId) {
        return paymentRepository.findByPaymentId(paymentId).orElseThrow().getStatus();
    }

    /** 결제 금액은 서버가 TAGO 에서 확인한 운임으로 정한다 */
    @Nested
    class ServerDecidesAmount {

        @Test
        @WithMockUser(username = USER_A)
        void cartRejectsTamperedFare() throws Exception {
            postJson("/api/cart/add", legJson(100, departAt)).andExpect(status().isBadRequest());
            assertThat(cartItemRepository.findByEmailAndIsDeletedFalse(USER_A)).isEmpty();
        }

        @Test
        @WithMockUser(username = USER_A)
        void cartStoresVerifiedFare() throws Exception {
            postJson("/api/cart/add", legJson(FARE, departAt)).andExpect(status().isOk());
            assertThat(cartItemRepository.findByEmailAndIsDeletedFalse(USER_A)).singleElement()
                    .satisfies(item -> {
                        assertThat(item.getRoutePayment()).isEqualTo(FARE);
                        assertThat(item.getFareVerified()).isTrue();
                    });
        }

        @Test
        @WithMockUser(username = USER_A)
        void cartRejectsTripTagoDoesNotHave() throws Exception {
            String otherTime = departAt.substring(0, 8) + "2358";
            postJson("/api/cart/add", legJson(FARE, otherTime)).andExpect(status().isBadRequest());
            assertThat(cartItemRepository.findByEmailAndIsDeletedFalse(USER_A)).isEmpty();
        }

        @Test
        @WithMockUser(username = USER_A)
        void cartRejectsOtherDayTrip() throws Exception {
            String tomorrow = LocalDate.now(ZoneId.of("Asia/Seoul")).plusDays(1)
                    .format(DateTimeFormatter.BASIC_ISO_DATE) + "0900";
            postJson("/api/cart/add", legJson(FARE, tomorrow)).andExpect(status().isBadRequest());
            assertThat(cartItemRepository.findByEmailAndIsDeletedFalse(USER_A)).isEmpty();
        }

        @Test
        @WithMockUser(username = USER_A)
        void directPayRejectsTamperedFareWithoutPreparing() throws Exception {
            long before = paymentRepository.count();
            postJson("/payment/direct-sdk-ready", legJson(100, departAt)).andExpect(status().isBadRequest());
            assertThat(paymentRepository.count()).isEqualTo(before);
        }

        @Test
        @WithMockUser(username = USER_A)
        void prepareRecordsPendingPaymentWithServerAmount() throws Exception {
            String groupId = cartGroupOf(USER_A, true);
            String body = mvc.perform(post("/payment/sdk-ready/" + groupId).with(csrf()))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            String paymentId = body.replaceAll(".*\"paymentId\":\"([^\"]+)\".*", "$1");

            PaymentEntity pending = paymentRepository.findByPaymentId(paymentId).orElseThrow();
            assertThat(pending.getStatus()).isEqualTo(PaymentEntity.PaymentStatus.PENDING);
            assertThat(pending.getAmount()).isEqualTo(FARE);
            assertThat(pending.getEmail()).isEqualTo(USER_A);
            assertThat(pending.getGroupId()).isEqualTo(groupId);
        }

        @Test
        @WithMockUser(username = USER_A)
        void cartItemWithUnverifiedFareCannotBePaid() throws Exception {
            String groupId = cartGroupOf(USER_A, null);
            mvc.perform(post("/payment/sdk-ready/" + groupId).with(csrf())).andExpect(status().isBadRequest());
        }
    }

    /** 결제 완료는 PortOne 에서 상태·금액·통화·상점을 확인한 뒤에만 기록한다 */
    @Nested
    class CompleteVerifiedWithPortOne {

        private void portOneReturns(String paymentId, String status, long total, String currency, String store) {
            when(portOneClient.find(paymentId))
                    .thenReturn(Optional.of(new PortOneClient.Payment(status, total, currency, store)));
        }

        @Test
        @WithMockUser(username = USER_A)
        void paidWithMatchingAmountCompletes() throws Exception {
            String groupId = cartGroupOf(USER_A, true);
            String paymentId = paymentOf(USER_A, groupId, PaymentEntity.PaymentStatus.PENDING).getPaymentId();
            portOneReturns(paymentId, "PAID", FARE, "KRW", storeId);

            postJson("/payment/complete", completeBody(paymentId)).andExpect(status().isOk());
            assertThat(statusOf(paymentId)).isEqualTo(PaymentEntity.PaymentStatus.SUCCESS);
            assertThat(cartItemRepository.findActiveByGroupId(USER_A, groupId)).isEmpty();
        }

        @Test
        @WithMockUser(username = USER_A)
        void amountMismatchIsCancelledAndFailed() throws Exception {
            String groupId = cartGroupOf(USER_A, true);
            String paymentId = paymentOf(USER_A, groupId, PaymentEntity.PaymentStatus.PENDING).getPaymentId();
            portOneReturns(paymentId, "PAID", 100, "KRW", storeId);

            postJson("/payment/complete", completeBody(paymentId)).andExpect(status().isBadRequest());
            assertThat(statusOf(paymentId)).isEqualTo(PaymentEntity.PaymentStatus.FAIL);
            verify(portOneClient).cancel(eq(paymentId), anyString());
            assertThat(cartItemRepository.findActiveByGroupId(USER_A, groupId)).hasSize(1);
        }

        @Test
        @WithMockUser(username = USER_A)
        void otherStoreOrCurrencyIsCancelledAndFailed() throws Exception {
            String paymentId = paymentOf(USER_A, UUID.randomUUID().toString(), PaymentEntity.PaymentStatus.PENDING).getPaymentId();
            portOneReturns(paymentId, "PAID", FARE, "KRW", "store-other");

            postJson("/payment/complete", completeBody(paymentId)).andExpect(status().isBadRequest());
            assertThat(statusOf(paymentId)).isEqualTo(PaymentEntity.PaymentStatus.FAIL);
            verify(portOneClient).cancel(eq(paymentId), anyString());
        }

        @Test
        @WithMockUser(username = USER_A)
        void unpaidIsNotCompleted() throws Exception {
            String groupId = cartGroupOf(USER_A, true);
            String paymentId = paymentOf(USER_A, groupId, PaymentEntity.PaymentStatus.PENDING).getPaymentId();
            portOneReturns(paymentId, "READY", FARE, "KRW", storeId);

            postJson("/payment/complete", completeBody(paymentId)).andExpect(status().isBadRequest());
            assertThat(statusOf(paymentId)).isEqualTo(PaymentEntity.PaymentStatus.PENDING);
            assertThat(cartItemRepository.findActiveByGroupId(USER_A, groupId)).hasSize(1);
        }

        @Test
        @WithMockUser(username = USER_A)
        void paymentUnknownToPortOneIsNotCompleted() throws Exception {
            String paymentId = paymentOf(USER_A, UUID.randomUUID().toString(), PaymentEntity.PaymentStatus.PENDING).getPaymentId();
            when(portOneClient.find(paymentId)).thenReturn(Optional.empty());

            postJson("/payment/complete", completeBody(paymentId)).andExpect(status().isBadRequest());
            assertThat(statusOf(paymentId)).isEqualTo(PaymentEntity.PaymentStatus.PENDING);
        }

        @Test
        @WithMockUser(username = USER_A)
        void paymentNotPreparedByServerIsRejected() throws Exception {
            String paymentId = "payment-forged-" + UUID.randomUUID();

            postJson("/payment/complete", completeBody(paymentId)).andExpect(status().isNotFound());
            assertThat(paymentRepository.findByPaymentId(paymentId)).isEmpty();
            verify(portOneClient, never()).find(any());
        }

        @Test
        @WithMockUser(username = USER_A)
        void othersPaymentCannotBeCompleted() throws Exception {
            String paymentId = paymentOf(USER_B, UUID.randomUUID().toString(), PaymentEntity.PaymentStatus.PENDING).getPaymentId();

            postJson("/payment/complete", completeBody(paymentId)).andExpect(status().isNotFound());
            assertThat(statusOf(paymentId)).isEqualTo(PaymentEntity.PaymentStatus.PENDING);
            verify(portOneClient, never()).find(any());
        }

        @Test
        @WithMockUser(username = USER_A)
        void secondPaymentForSameOrderIsCancelled() throws Exception {
            String groupId = UUID.randomUUID().toString();
            paymentOf(USER_A, groupId, PaymentEntity.PaymentStatus.SUCCESS);
            String paymentId = paymentOf(USER_A, groupId, PaymentEntity.PaymentStatus.PENDING).getPaymentId();
            portOneReturns(paymentId, "PAID", FARE, "KRW", storeId);

            postJson("/payment/complete", completeBody(paymentId)).andExpect(status().isConflict());
            assertThat(statusOf(paymentId)).isEqualTo(PaymentEntity.PaymentStatus.FAIL);
            verify(portOneClient).cancel(eq(paymentId), anyString());
        }

        @Test
        @WithMockUser(username = USER_A)
        void completingTwiceDoesNotCheckOrRecordAgain() throws Exception {
            String paymentId = paymentOf(USER_A, UUID.randomUUID().toString(), PaymentEntity.PaymentStatus.SUCCESS).getPaymentId();
            long before = paymentRepository.count();

            postJson("/payment/complete", completeBody(paymentId)).andExpect(status().isOk());
            assertThat(paymentRepository.count()).isEqualTo(before);
            verify(portOneClient, never()).find(any());
        }

        @Test
        @WithMockUser(username = USER_A)
        void failNoticeCannotOverwriteCompletedPayment() throws Exception {
            String paymentId = paymentOf(USER_A, UUID.randomUUID().toString(), PaymentEntity.PaymentStatus.SUCCESS).getPaymentId();

            postJson("/payment/fail", completeBody(paymentId)).andExpect(status().isOk());
            assertThat(statusOf(paymentId)).isEqualTo(PaymentEntity.PaymentStatus.SUCCESS);
        }

        @Test
        @WithMockUser(username = USER_A)
        void failNoticeForUnknownPaymentCreatesNothing() throws Exception {
            String paymentId = "payment-forged-" + UUID.randomUUID();

            postJson("/payment/fail", completeBody(paymentId)).andExpect(status().isNotFound());
            assertThat(paymentRepository.findByPaymentId(paymentId)).isEmpty();
        }
    }
}
