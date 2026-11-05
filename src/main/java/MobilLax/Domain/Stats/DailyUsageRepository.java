package MobilLax.Domain.Stats;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/** 하루 합계 저장소: 더하기와 조회만 있다 */
public interface DailyUsageRepository extends Repository<DailyUsage, Long> {

    /** 없으면 1로 만들고 있으면 1 더한다. 한 문장이라 동시에 들어와도 빠지거나 겹치지 않는다 (유니크 키 uk_daily_usage_date_metric) */
    @Modifying
    @Transactional
    @Query(value = "INSERT INTO daily_usage (stat_date, metric, total) VALUES (:statDate, :metric, 1) "
            + "ON DUPLICATE KEY UPDATE total = total + 1", nativeQuery = true)
    void increment(@Param("statDate") LocalDate statDate, @Param("metric") String metric);

    List<DailyUsage> findByStatDateBetween(LocalDate from, LocalDate to);
}
