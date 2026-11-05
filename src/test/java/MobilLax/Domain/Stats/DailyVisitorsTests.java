package MobilLax.Domain.Stats;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/** 방문자 판단 (보안 규칙 S13): 날짜를 직접 넘겨 날이 바뀌는 경우까지 확인한다 */
class DailyVisitorsTests {

    private static final LocalDate DAY = LocalDate.of(2026, 11, 5);
    private static final String IP = "203.0.113.7";
    private static final String BROWSER = "Mozilla/5.0";

    @Test
    void theSamePersonCountsOnceADay() {
        DailyVisitors visitors = new DailyVisitors();
        assertThat(visitors.firstVisit(DAY, IP, BROWSER)).isTrue();
        assertThat(visitors.firstVisit(DAY, IP, BROWSER)).isFalse();
        assertThat(visitors.firstVisit(DAY, "203.0.113.8", BROWSER)).isTrue();
        assertThat(visitors.firstVisit(DAY, IP, "Mozilla/5.0 other")).isTrue();
    }

    @Test
    void theNextDayStartsOver() {
        DailyVisitors visitors = new DailyVisitors();
        visitors.firstVisit(DAY, IP, BROWSER);
        assertThat(visitors.firstVisit(DAY.plusDays(1), IP, BROWSER)).isTrue();
    }

    @Test
    void memoryIsBoundedAndFreedTheNextDay() {
        DailyVisitors visitors = new DailyVisitors(2);
        assertThat(visitors.firstVisit(DAY, "a", BROWSER)).isTrue();
        assertThat(visitors.firstVisit(DAY, "b", BROWSER)).isTrue();
        assertThat(visitors.firstVisit(DAY, "c", BROWSER)).as("상한을 넘으면 더 세지 않음").isFalse();
        assertThat(visitors.firstVisit(DAY.plusDays(1), "c", BROWSER)).isTrue();
    }

    /** 메모리에도 IP·브라우저 문자열을 그대로 두지 않는다: 문자열 칸은 없고 해시만 있다 */
    @Test
    void rawValuesAreNotKept() {
        assertThat(Arrays.stream(DailyVisitors.class.getDeclaredFields()).map(Field::getType))
                .doesNotContain((Class) String.class);
    }
}
