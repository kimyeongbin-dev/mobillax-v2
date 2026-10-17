package MobilLax.Domain.Payment;

import MobilLax.Common.Validation.PaymentId;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 결제 완료·실패 알림. 금액·묶음·교통수단은 서버가 결제 준비 때 기록한 값을 쓰므로 결제 번호만 받는다 (보안 규칙 S1)
 * 결제 번호는 서버가 만든 형식(payment-...)만 받는다 (S10)
 */
@Getter
@Setter
public class PaymentRequest {
    @NotNull @PaymentId
    private String paymentId;
}
