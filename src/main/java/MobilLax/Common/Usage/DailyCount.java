package MobilLax.Common.Usage;

import java.time.LocalDate;

/** 날짜별 건수 조회 결과 (가입·문의 저장소가 함께 쓴다) */
public interface DailyCount {
    LocalDate getStatDate();

    long getTotal();
}
