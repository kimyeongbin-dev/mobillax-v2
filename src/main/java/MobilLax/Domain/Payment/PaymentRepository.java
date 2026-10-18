package MobilLax.Domain.Payment;

import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<PaymentEntity, Long> {
    List<PaymentEntity> findByEmailAndDateBetween(String email, LocalDate start, LocalDate end);
    boolean existsByGroupIdAndStatus(String groupId, PaymentEntity.PaymentStatus status);
    boolean existsByGroupIdAndTransportTypeAndStatus(String groupId, String transportType, PaymentEntity.PaymentStatus status); // 🔥 추가
    Optional<PaymentEntity> findByPaymentId(String paymentId); // 🔥 추가
    /** 결제 내역 화면: 내 결제 중 주어진 상태이고 숨기지 않은 것만, 최근 순 (v2.5) */
    List<PaymentEntity> findTop50ByEmailAndStatusInAndHiddenAtIsNullOrderByIdDesc(
            String email, Collection<PaymentEntity.PaymentStatus> statuses);
}
