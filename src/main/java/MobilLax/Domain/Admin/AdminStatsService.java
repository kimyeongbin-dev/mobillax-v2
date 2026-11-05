package MobilLax.Domain.Admin;

import MobilLax.Common.Time.KoreaTime;
import MobilLax.Common.Usage.DailyCount;
import MobilLax.Common.Usage.UsageMetric;
import MobilLax.Domain.Account.UserAccountRepository;
import MobilLax.Domain.Inquiry.InquiryRepository;
import MobilLax.Domain.Payment.PaymentEntity;
import MobilLax.Domain.Payment.PaymentRepository;
import MobilLax.Domain.Stats.DailyUsage;
import MobilLax.Domain.Stats.DailyUsageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 관리자 통계 (v2.13, 보안 규칙 S13): 날짜별 합계만 보여 주고, 계정이나 사람 단위 값은 내보내지 않는다
 * - 방문·검색·장바구니는 daily_usage 합계, 가입·결제·문의는 각 테이블에서 읽는다 (같은 값을 두 곳에 저장하지 않는다)
 */
@Service
@RequiredArgsConstructor
public class AdminStatsService {

    /** 고를 수 있는 기간 */
    public enum Range {
        WEEK(7, "최근 7일"), MONTH(30, "최근 30일"), QUARTER(90, "최근 90일");

        private final int days;
        private final String label;

        Range(int days, String label) {
            this.days = days;
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public record DayRow(LocalDate date, long visitors, long pageViews, long routeSearches, long nearbySearches,
                         long cartAdds, long signups, long payments, long paymentAmount, long inquiries) {

        private static DayRow zero() {
            return new DayRow(null, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        }

        private DayRow plus(DayRow other) {
            return new DayRow(null, visitors + other.visitors, pageViews + other.pageViews,
                    routeSearches + other.routeSearches, nearbySearches + other.nearbySearches, cartAdds + other.cartAdds,
                    signups + other.signups, payments + other.payments, paymentAmount + other.paymentAmount,
                    inquiries + other.inquiries);
        }
    }

    public record Report(Range range, List<DayRow> days, DayRow total) {
    }

    private final DailyUsageRepository usageRepository;
    private final UserAccountRepository userAccountRepository;
    private final PaymentRepository paymentRepository;
    private final InquiryRepository inquiryRepository;
    private final AdminAuditService audit;

    public Report report(String adminEmail, Range range, String clientIp) {
        audit.record(adminEmail, AdminAuditLog.Action.STATS_VIEWED, null, range.name(), clientIp);
        LocalDate today = KoreaTime.today();
        List<DayRow> days = rows(today.minusDays(range.days - 1L), today);
        return new Report(range, days, days.stream().reduce(DayRow.zero(), DayRow::plus));
    }

    /** 요약 화면의 오늘 숫자 */
    public DayRow today() {
        LocalDate today = KoreaTime.today();
        return rows(today, today).get(0);
    }

    /** 이용이 없던 날도 0 으로 보이게 기간의 모든 날을 만든다 (최근 날짜부터) */
    private List<DayRow> rows(LocalDate from, LocalDate to) {
        Map<LocalDate, Map<UsageMetric, Long>> usage = usageRepository.findByStatDateBetween(from, to).stream()
                .collect(Collectors.groupingBy(DailyUsage::getStatDate,
                        Collectors.toMap(DailyUsage::getMetric, DailyUsage::getTotal)));
        LocalDateTime start = from.atStartOfDay();
        LocalDateTime end = to.plusDays(1).atStartOfDay();
        Map<LocalDate, Long> signups = byDay(userAccountRepository.countSignupsByDay(start, end));
        Map<LocalDate, Long> inquiries = byDay(inquiryRepository.countByDay(start, end));
        Map<LocalDate, PaymentRepository.DailyPayment> payments = paymentRepository.sumByDay(from, to, PaymentEntity.PaymentStatus.SUCCESS).stream()
                .collect(Collectors.toMap(PaymentRepository.DailyPayment::getStatDate, row -> row));

        List<DayRow> rows = new ArrayList<>();
        for (LocalDate day = to; !day.isBefore(from); day = day.minusDays(1)) {
            Map<UsageMetric, Long> counts = usage.getOrDefault(day, Map.of());
            PaymentRepository.DailyPayment payment = payments.get(day);
            rows.add(new DayRow(day, counts.getOrDefault(UsageMetric.VISITOR, 0L), counts.getOrDefault(UsageMetric.PAGE_VIEW, 0L),
                    counts.getOrDefault(UsageMetric.ROUTE_SEARCH, 0L), counts.getOrDefault(UsageMetric.NEARBY_SEARCH, 0L),
                    counts.getOrDefault(UsageMetric.CART_ADD, 0L), signups.getOrDefault(day, 0L),
                    payment == null ? 0 : payment.getTotal(), payment == null ? 0 : payment.getAmount(),
                    inquiries.getOrDefault(day, 0L)));
        }
        return rows;
    }

    private static Map<LocalDate, Long> byDay(List<DailyCount> rows) {
        return rows.stream().collect(Collectors.toMap(DailyCount::getStatDate, DailyCount::getTotal));
    }
}
