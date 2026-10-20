package MobilLax.Domain.Payment;

import MobilLax.Common.Time.KoreaTime;
import MobilLax.Domain.Trip.TripDisplay;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class PaymentHistoryService {

    /** 결제 내역에 보이는 상태: 확인된 결제와 실패·취소. 준비만 하고 버린 PENDING 은 보이지 않는다 */
    private static final List<PaymentEntity.PaymentStatus> HISTORY_STATUSES =
            List.of(PaymentEntity.PaymentStatus.SUCCESS, PaymentEntity.PaymentStatus.FAIL);

    private final PaymentRepository paymentRepository;

    /**
     * 결제 내역 한 줄 (화면 표시용, 모든 값은 서버가 만든 것)
     *
     * @param departure 출발 일시 표시 (예: "10월 18일 07:02 출발"), 알 수 없으면 빈 문자열
     */
    public record HistoryItem(String paymentId, LocalDate date, String orderName, String transport, int amount,
                              String departure, boolean paid) {
    }

    /**
     * 관리자가 보는 결제 한 줄 (v2.11): 사용자가 숨긴 결제와 준비·실패 결제도 보인다
     *
     * @param status SUCCESS, FAIL, PENDING
     * @param hidden 사용자가 예매 내역에서 삭제(숨김)했는지
     */
    public record AdminPaymentItem(LocalDate date, String orderName, String transport, int amount, String departure,
                                   String status, boolean hidden) {
    }

    public int getMonthlyPaymentTotal(String email) {
        LocalDate start = KoreaTime.today().withDayOfMonth(1);
        LocalDate end = start.plusMonths(1).withDayOfMonth(1).minusDays(1);

        // 결제가 확인되고 예매 내역에 보이는 것만 (준비·실패 결제, 사용자가 내역에서 삭제한 결제는 빼고)
        // 합계는 사용자가 보는 내역과 맞아야 한다. 숨긴 기록은 보관만 하고 화면 계산에는 넣지 않는다
        return paymentRepository.findByEmailAndDateBetween(email, start, end).stream()
                .filter(p -> p.getStatus() == PaymentEntity.PaymentStatus.SUCCESS && p.getHiddenAt() == null)
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
                        TripDisplay.transportLabel(p.getTransportType()),
                        p.getAmount(),
                        formatDeparture(p.getDepartureTime()),
                        p.getStatus() == PaymentEntity.PaymentStatus.SUCCESS))
                .toList();
    }

    /** 관리자 계정 상세용 결제 기록 (최근 50건, 숨긴 결제 포함). 관리자 확인(OTP)을 거친 화면에서만 부른다 */
    public List<AdminPaymentItem> getAllForAdmin(String email) {
        return paymentRepository.findTop50ByEmailOrderByIdDesc(email).stream()
                .map(p -> new AdminPaymentItem(
                        p.getDate(),
                        p.getOrderName() != null ? p.getOrderName() : "주문 정보 없음",
                        TripDisplay.transportLabel(p.getTransportType()),
                        p.getAmount(),
                        formatDeparture(p.getDepartureTime()),
                        p.getStatus() == null ? "" : p.getStatus().name(),
                        p.getHiddenAt() != null))
                .toList();
    }

    /**
     * 예매 내역에서 삭제 (v2.5) — 사용자 화면에서만 숨기고 기록은 남긴다
     * - 법정 보존 기간 동안 보관하고, 중복 결제 확인(S1)·환불·분쟁 대응에 그대로 쓴다
     * - 숨긴 내역은 사용자가 직접 다시 볼 수 없고, 고객센터 문의로 관리자가 확인한다 (관리자 계정 상세, v2.11)
     * - 내 기록이고 내역에 보이는 상태(완료·실패)일 때만. 아니면 없는 기록과 같은 404 (보안 규칙 S4)
     * - 이미 숨긴 기록을 다시 숨겨도 처음 숨긴 시각을 유지한다
     */
    public void hide(String email, String paymentId) {
        PaymentEntity payment = paymentRepository.findByPaymentId(paymentId)
                .filter(p -> email.equals(p.getEmail()) && HISTORY_STATUSES.contains(p.getStatus()))
                .orElseThrow(NoSuchElementException::new);
        if (payment.getHiddenAt() == null) {
            payment.setHiddenAt(KoreaTime.now());
            paymentRepository.save(payment);
        }
    }

    /** yyyyMMddHHmm → "10월 18일 07:02 출발" */
    static String formatDeparture(String departureTime) {
        String time = TripDisplay.departure(departureTime);
        return time.isEmpty() ? "" : time + " 출발";
    }
}
