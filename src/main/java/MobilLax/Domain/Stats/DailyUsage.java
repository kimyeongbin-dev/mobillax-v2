package MobilLax.Domain.Stats;

import MobilLax.Common.Usage.UsageMetric;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 하루 이용 합계 (daily_usage 테이블, v2.13, 보안 규칙 S13)
 * 날짜·항목·합계만 있다. 사람을 알아볼 수 있는 값(IP, 계정, 브라우저 정보)을 넣지 않는다 (UsageStatsRegressionTests 가 칸 목록을 검사)
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "daily_usage", uniqueConstraints = @UniqueConstraint(name = "uk_daily_usage_date_metric",
        columnNames = {"stat_date", "metric"}))
public class DailyUsage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "stat_date", nullable = false)
    private LocalDate statDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(30)")
    private UsageMetric metric;

    @Column(nullable = false)
    private long total;
}
