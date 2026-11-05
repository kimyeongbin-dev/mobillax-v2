package MobilLax.Domain.Payment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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
    /** 관리자 계정 상세: 숨긴 결제·준비 중 결제까지 모두, 최근 순 (v2.11) */
    List<PaymentEntity> findTop50ByEmailOrderByIdDesc(String email);

    /** 통계: 날짜별 완료한 결제 건수와 금액 (v2.13). 사용자가 내역에서 숨긴 결제도 실제 결제이므로 센다 */
    @Query("select p.date as statDate, count(p) as total, sum(p.amount) as amount from PaymentEntity p "
            + "where p.date between :from and :to and p.status = :status group by p.date")
    List<DailyPayment> sumByDay(@Param("from") LocalDate from, @Param("to") LocalDate to,
                                @Param("status") PaymentEntity.PaymentStatus status);

    interface DailyPayment {
        LocalDate getStatDate();

        long getTotal();

        long getAmount();
    }
}
