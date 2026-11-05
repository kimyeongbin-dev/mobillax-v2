package MobilLax.Domain.Admin;

import MobilLax.Common.Error.UserFacingException;
import MobilLax.Common.Time.KoreaTime;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;

/**
 * 감사 로그 정기 점검 (v2.13, 보안 규칙 S12)
 * - 달을 골라 일 종류별 건수와 기록을 본다. 개인정보 열람·인증 실패처럼 살펴볼 일은 표시한다
 * - 끝난 달만 "점검 완료"로 남길 수 있다. 지금 OTP 번호로 다시 확인하고, 감사 로그에 추가로 남긴다 (기록을 고치지는 않는다)
 */
@Service
@RequiredArgsConstructor
public class AdminAuditReviewService {

    static final int PAGE_SIZE = 50;

    private final AdminAuditLogRepository repository;
    private final AdminAuditService audit;
    private final AdminOtpService otpService;

    public record ActionCount(AdminAuditLog.Action action, long total) {
    }

    public record Review(YearMonth month, Page<AdminAuditLog> logs, List<ActionCount> counts, boolean reviewed,
                         boolean reviewable) {
    }

    public YearMonth lastMonth() {
        return YearMonth.from(KoreaTime.today()).minusMonths(1);
    }

    public boolean reviewed(YearMonth month) {
        return repository.existsByActionAndDetail(AdminAuditLog.Action.AUDIT_REVIEWED, month.toString());
    }

    public Review review(String adminEmail, YearMonth month, AdminAuditLog.Action action, Long targetUserId, int page,
                         String clientIp) {
        audit.record(adminEmail, AdminAuditLog.Action.AUDIT_VIEWED, targetUserId, month.toString(), clientIp);
        LocalDateTime from = month.atDay(1).atStartOfDay();
        LocalDateTime to = month.plusMonths(1).atDay(1).atStartOfDay();
        List<ActionCount> counts = repository.countByAction(from, to).stream()
                .map(row -> new ActionCount(row.getAction(), row.getTotal()))
                .sorted(Comparator.comparing((ActionCount row) -> !row.action().attention())
                        .thenComparing(Comparator.comparingLong(ActionCount::total).reversed()))
                .toList();
        return new Review(month, repository.search(from, to, action, targetUserId, PageRequest.of(page, PAGE_SIZE)), counts,
                reviewed(month), finished(month));
    }

    @Transactional
    public void confirm(String adminEmail, YearMonth month, String otpCode, String clientIp) {
        // OTP 를 확인하기 전에 거절한다 (확인 뒤 되돌아가면 쓴 번호가 다시 쓰일 수 있음)
        if (!finished(month)) {
            throw new UserFacingException("끝난 달만 점검 완료로 남길 수 있습니다.");
        }
        if (reviewed(month)) {
            throw new UserFacingException("이미 점검을 마친 달입니다.");
        }
        otpService.stepUp(adminEmail, otpCode, null, clientIp);
        audit.record(adminEmail, AdminAuditLog.Action.AUDIT_REVIEWED, null, month.toString(), clientIp);
    }

    private static boolean finished(YearMonth month) {
        return month.isBefore(YearMonth.from(KoreaTime.today()));
    }
}
