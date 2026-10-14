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

    public enum PaymentStatus {
        PENDING, SUCCESS, FAIL
    }
}
