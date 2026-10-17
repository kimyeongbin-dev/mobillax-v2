// ✅ PaymentService.java
package MobilLax.Domain.Payment;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;

    public int getMonthlyPaymentTotal(String email) {
        LocalDate start = LocalDate.now().withDayOfMonth(1);
        LocalDate end = start.plusMonths(1).withDayOfMonth(1).minusDays(1);

        // 결제가 확인된 것만 (준비만 했거나 실패한 결제는 빼고)
        return paymentRepository.findByEmailAndDateBetween(email, start, end).stream()
                .filter(p -> p.getStatus() == PaymentEntity.PaymentStatus.SUCCESS)
                .mapToInt(PaymentEntity::getAmount)
                .sum();
    }
} 