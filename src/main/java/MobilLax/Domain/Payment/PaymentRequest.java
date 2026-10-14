package MobilLax.Domain.Payment;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PaymentRequest {
    private String groupId;
    private String paymentId;
    private int amount;
    private String transportType;  // 🔥 추가됨
}