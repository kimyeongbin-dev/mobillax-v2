package MobilLax.Domain.Payment;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "payments")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String email;

    private int amount;

    private LocalDate date;

    @Column(name = "payment_id", nullable = false, unique = true)
    private String paymentId; // 🔥 추가됨

    @Column(name = "transport_type", nullable = false)
    private String transportType;

    @Column(name = "group_id", nullable = false)
    private String groupId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    // 결제 내역 화면용 (v2.5): 서버가 결제 준비 때 TAGO 값으로 만든 주문명과 가장 이른 출발 일시. 그 전 기록은 null
    @Column(name = "order_name", length = 200)
    private String orderName;

    @Column(name = "departure_time", length = 12)
    private String departureTime;

    // 사용자가 예매 내역에서 삭제(숨김)한 시각 (v2.5). 기록은 지우지 않는다:
    // 결제·공급 기록은 법정 보존 기간 동안 보관하고, 중복 결제 확인·환불·분쟁 대응에 쓴다
    @Column(name = "hidden_at")
    private java.time.LocalDateTime hiddenAt;

    public enum PaymentStatus {
        PENDING, SUCCESS, FAIL
    }
}
