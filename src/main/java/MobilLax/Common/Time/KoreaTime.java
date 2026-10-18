package MobilLax.Common.Time;

import java.time.ZoneId;
import java.util.TimeZone;

/**
 * 서비스 기준 시간대 (한국 시간) — 한 곳에서만 정한다
 * - 컨테이너는 UTC 로 돌아서, 기본 시간대를 쓰는 LocalDate.now() 가 한국 시간 0-9시에 하루 전 날짜를 냈다
 *   (예: 10월 18일 03:55 결제가 10월 17일로 기록). 앱이 뜰 때 JVM 기본 시간대를 여기 값으로 맞춘다 (v2.5)
 * - 시간대를 명시해야 하는 코드는 ZONE 을 쓴다
 */
public final class KoreaTime {

    public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private KoreaTime() {
    }

    /** JVM 기본 시간대를 한국 시간으로 (MobilLaxApplication 이 클래스를 불러올 때 한 번) */
    public static void applyAsDefault() {
        TimeZone.setDefault(TimeZone.getTimeZone(ZONE));
    }
}
