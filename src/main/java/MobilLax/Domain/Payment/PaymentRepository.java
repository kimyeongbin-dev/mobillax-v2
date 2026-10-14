package MobilLax.Domain.Payment;

import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<PaymentEntity, Long> {
    List<PaymentEntity> findByEmailAndDateBetween(String email, LocalDate start, LocalDate end);
    boolean existsByGroupIdAndStatus(String groupId, PaymentEntity.PaymentStatus status);
    boolean existsByGroupIdAndTransportTypeAndStatus(String groupId, String transportType, PaymentEntity.PaymentStatus status); // 🔥 추가
    Optional<PaymentEntity> findByPaymentId(String paymentId); // 🔥 추가
}
