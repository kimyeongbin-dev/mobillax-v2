package MobilLax.Domain.Stats;

import MobilLax.Common.Time.KoreaTime;
import MobilLax.Common.Usage.UsageMetric;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** 이용 항목을 오늘 합계에 더한다. 통계를 남기지 못해도 사용자의 요청은 실패하지 않는다 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UsageCounter {

    private final DailyUsageRepository repository;

    public void count(UsageMetric metric) {
        try {
            repository.increment(KoreaTime.today(), metric.name());
        } catch (RuntimeException e) {
            log.warn("이용 통계를 남기지 못했습니다: {} {}", metric, e.getClass().getSimpleName());
        }
    }
}
