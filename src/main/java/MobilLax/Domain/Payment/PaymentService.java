package MobilLax.Domain.Payment;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class PaymentService {

    /** 결제 내역에 보이는 상태: 확인된 결제와 실패·취소. 준비만 하고 버린 PENDING 은 보이지 않는다 */
    private static final List<PaymentEntity.PaymentStatus> HISTORY_STATUSES =
            List.of(PaymentEntity.PaymentStatus.SUCCESS, PaymentEntity.PaymentStatus.FAIL);

    private static final Map<String, String> TRANSPORT_LABEL = Map.of(
            "TRAIN", "기차", "EXPRESSBUS", "고속·시외버스", "AIRPLANE", "항공");

    private final PaymentRepository paymentRepository;

    /**
     * 결제 내역 한 줄 (화면 표시용, 모든 값은 서버가 만든 것)
     *
     * @param departure 출발 일시 표시 (예: "10월 18일 07:02 출발"), 알 수 없으면 빈 문자열
     */
    public record HistoryItem(String paymentId, LocalDate date, String orderName, String transport, int amount,
                              String departure, boolean paid) {
    }

    public int getMonthlyPaymentTotal(String email) {
        LocalDate start = LocalDate.now().withDayOfMonth(1);
        LocalDate end = start.plusMonths(1).withDayOfMonth(1).minusDays(1);

        // 결제가 확인된 것만 (준비만 했거나 실패한 결제는 빼고)
        return paymentRepository.findByEmailAndDateBetween(email, start, end).stream()
                .filter(p -> p.getStatus() == PaymentEntity.PaymentStatus.SUCCESS)
                .mapToInt(PaymentEntity::getAmount)
                .sum();
    }

    /** 내 결제 내역 (최근 50건, 보안 규칙 S4: 로그인한 사용자 것만) */
    public List<HistoryItem> getHistory(String email) {
        return paymentRepository.findTop50ByEmailAndStatusInAndHiddenAtIsNullOrderByIdDesc(email, HISTORY_STATUSES).stream()
                .map(p -> new HistoryItem(
                        p.getPaymentId(),
                        p.getDate(),
                        p.getOrderName() != null ? p.getOrderName() : "주문 정보 없음 (v2.5 이전 결제)",
                        TRANSPORT_LABEL.getOrDefault(p.getTransportType(), p.getTransportType()),
                        p.getAmount(),
                        formatDeparture(p.getDepartureTime()),
                        p.getStatus() == PaymentEntity.PaymentStatus.SUCCESS))
                .toList();
    }

    /**
     * 예매 내역에서 삭제 (v2.5) — 사용자 화면에서만 숨기고 기록은 남긴다
     * - 법정 보존 기간 동안 보관하고, 중복 결제 확인(S1)·환불·분쟁 대응에 그대로 쓴다
     * - 숨긴 내역은 사용자가 직접 다시 볼 수 없고, 고객센터 문의로 관리자가 확인한다 (관리자 기능은 이후 버전)
     * - 내 기록이고 내역에 보이는 상태(완료·실패)일 때만. 아니면 없는 기록과 같은 404 (보안 규칙 S4)
     * - 이미 숨긴 기록을 다시 숨겨도 처음 숨긴 시각을 유지한다
     */
    public void hide(String email, String paymentId) {
        PaymentEntity payment = paymentRepository.findByPaymentId(paymentId)
                .filter(p -> email.equals(p.getEmail()) && HISTORY_STATUSES.contains(p.getStatus()))
                .orElseThrow(NoSuchElementException::new);
        if (payment.getHiddenAt() == null) {
            payment.setHiddenAt(LocalDateTime.now());
            paymentRepository.save(payment);
        }
    }

    /** yyyyMMddHHmm → "10월 18일 07:02 출발" */
    static String formatDeparture(String departureTime) {
        if (departureTime == null || !departureTime.matches("\\d{12}")) {
            return "";
        }
        return Integer.parseInt(departureTime.substring(4, 6)) + "월 "
                + Integer.parseInt(departureTime.substring(6, 8)) + "일 "
                + departureTime.substring(8, 10) + ":" + departureTime.substring(10, 12) + " 출발";
    }
}
