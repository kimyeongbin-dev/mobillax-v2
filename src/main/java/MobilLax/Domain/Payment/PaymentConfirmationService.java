package MobilLax.Domain.Payment;

import MobilLax.Common.Error.UserFacingException;
import MobilLax.Domain.Cart.CartItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.NoSuchElementException;

/**
 * 결제 완료·실패 처리 (보안 규칙 S1)
 * - 브라우저의 "결제됨" 알림을 믿지 않고 PortOne 에서 상태(PAID)·금액·통화·상점을 확인한 뒤에만 SUCCESS
 * - 금액이 다르거나 이미 결제된 묶음이면 PortOne 결제를 취소하고 FAIL
 * - 저장은 단계마다 바로 한다: 취소 뒤 거절(예외)해도 FAIL 기록이 남아야 한다
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentConfirmationService {

    /** 결제 완료 결과 문구 (화면은 "ok" 면 성공으로 본다) */
    static final String COMPLETED = "ok";
    static final String ALREADY_PAID = "이미 결제된 주문입니다.";

    private final PaymentRepository paymentRepository;
    private final CartItemRepository cartItemRepository;
    private final PortOneClient portOneClient;
    private final PortOneStore store;

    /** PortOne 에서 확인한 뒤 SUCCESS 로 기록하고 장바구니 묶음을 비운다 */
    public String complete(String email, String paymentId) {
        PaymentEntity payment = findOwnPayment(email, paymentId);
        if (payment.getStatus() == PaymentEntity.PaymentStatus.SUCCESS) {
            return ALREADY_PAID;
        }
        if (payment.getStatus() != PaymentEntity.PaymentStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 실패로 처리된 결제입니다.");
        }

        PortOneClient.Payment portOne = findAtPortOne(payment);
        if (!"PAID".equals(portOne.status())) {
            throw new UserFacingException("결제가 완료되지 않았습니다.");
        }
        // 금액·통화·상점이 결제 준비 때 서버가 정한 값과 다르면 위조로 보고 취소
        if (portOne.total() != payment.getAmount() || !"KRW".equals(portOne.currency())
                || !store.storeId().equals(portOne.storeId())) {
            log.warn("결제 금액 불일치 ({}): 기록 {}원, PortOne {}원 {}", payment.getPaymentId(), payment.getAmount(),
                    portOne.total(), portOne.currency());
            cancelAndFail(payment, "결제 금액 불일치");
            throw new UserFacingException("결제 금액이 주문과 달라 결제를 취소했습니다.");
        }
        // 같은 묶음·교통수단을 다른 결제로 이미 냈다면 이번 결제는 중복이므로 취소
        if (paymentRepository.existsByGroupIdAndTransportTypeAndStatus(
                payment.getGroupId(), payment.getTransportType(), PaymentEntity.PaymentStatus.SUCCESS)) {
            cancelAndFail(payment, "중복 결제");
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 결제된 주문이라 이번 결제를 취소했습니다.");
        }

        payment.setStatus(PaymentEntity.PaymentStatus.SUCCESS);
        paymentRepository.save(payment);
        cartItemRepository.markAsDeletedByEmailAndGroupId(email, payment.getGroupId());
        return COMPLETED;
    }

    /** 결제 실패·취소 알림: 아직 확인되지 않은(PENDING) 결제만 FAIL 로 바꾼다. 완료된 결제는 바꾸지 않는다 */
    public void fail(String email, String paymentId) {
        PaymentEntity payment = findOwnPayment(email, paymentId);
        if (payment.getStatus() == PaymentEntity.PaymentStatus.PENDING) {
            payment.setStatus(PaymentEntity.PaymentStatus.FAIL);
            paymentRepository.save(payment);
        }
    }

    /** 자기 결제 기록. 없거나 다른 사용자의 것이면 404 (있다는 사실도 알리지 않는다, S4) */
    private PaymentEntity findOwnPayment(String email, String paymentId) {
        return paymentRepository.findByPaymentId(paymentId)
                .filter(p -> email.equals(p.getEmail()))
                .orElseThrow(NoSuchElementException::new);
    }

    private PortOneClient.Payment findAtPortOne(PaymentEntity payment) {
        try {
            return portOneClient.find(payment.getPaymentId())
                    .orElseThrow(() -> new UserFacingException("결제 내역을 찾을 수 없습니다."));
        } catch (IllegalStateException e) {
            log.warn("결제 확인 실패 ({}): {}", payment.getPaymentId(), e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "결제를 확인하지 못했습니다. 잠시 후 마이페이지에서 확인해 주세요.");
        }
    }

    private void cancelAndFail(PaymentEntity payment, String reason) {
        try {
            portOneClient.cancel(payment.getPaymentId(), reason);
        } catch (IllegalStateException e) {
            // 취소하지 못하면 FAIL 기록과 로그로 남겨 수동 환불한다
            log.error("결제 취소 실패 ({}, {}): {}", payment.getPaymentId(), reason, e.getMessage());
        }
        payment.setStatus(PaymentEntity.PaymentStatus.FAIL);
        paymentRepository.save(payment);
    }
}
