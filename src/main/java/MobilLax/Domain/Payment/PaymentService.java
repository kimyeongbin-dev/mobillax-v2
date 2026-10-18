package MobilLax.Domain.Payment;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

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
    public record HistoryItem(LocalDate date, String orderName, String transport, int amount,
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
        return paymentRepository.findTop50ByEmailAndStatusInOrderByIdDesc(email, HISTORY_STATUSES).stream()
                .map(p -> new HistoryItem(
                        p.getDate(),
                        p.getOrderName() != null ? p.getOrderName() : "주문 정보 없음 (v2.5 이전 결제)",
                        TRANSPORT_LABEL.getOrDefault(p.getTransportType(), p.getTransportType()),
                        p.getAmount(),
                        formatDeparture(p.getDepartureTime()),
                        p.getStatus() == PaymentEntity.PaymentStatus.SUCCESS))
                .toList();
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
