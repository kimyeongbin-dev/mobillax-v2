package MobilLax.Domain.Cart;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 출발 시각 확인: 이미 출발한 편은 장바구니에 담거나 결제할 수 없다
 * - 편의 출발 일시는 yyyyMMddHHmm (한국 시간, TAGO 편 기준)
 * - 출발 일시가 없는 항목(편을 고르기 전 방식으로 담긴 예전 항목)도 결제하지 않는다
 */
public final class DepartureCheck {

    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter MINUTE = DateTimeFormatter.ofPattern("yyyyMMddHHmm");

    private DepartureCheck() {
    }

    /** 지금 한국 시간 yyyyMMddHHmm */
    public static String now() {
        return LocalDateTime.now(KOREA).format(MINUTE);
    }

    /** 결제할 수 없는 편인지 (출발 일시가 없거나, 이미 출발했거나 지금 출발) */
    public static boolean isDeparted(String departureTime, String now) {
        return departureTime == null || !departureTime.matches("\\d{12}") || departureTime.compareTo(now) <= 0;
    }
}
