package MobilLax.Model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;

/**
 * 대중교통 경로 검색 요청 (WGS84 경도 x, 위도 y)
 *
 * @param departAt 출발 일시 yyyyMMddHHmm (TMAP searchDttm). 비어 있으면 현재 시각 기준으로 검색
 */
public record TransitRouteRequest(double startX, double startY, double endX, double endY, String departAt) {

    /** 출발 일시 형식 (TMAP searchDttm 과 같음) */
    public static final DateTimeFormatter DEPART_AT_FORMAT =
            DateTimeFormatter.ofPattern("uuuuMMddHHmm").withResolverStyle(ResolverStyle.STRICT);

    /** 조회할 수 있는 가장 먼 날짜 (오늘부터 며칠 뒤까지) */
    public static final int MAX_DAYS_AHEAD = 30;

    /** 출발지·도착지가 모두 대한민국 범위 안의 좌표인지 */
    public boolean isInKorea() {
        return inKorea(startX, startY) && inKorea(endX, endY);
    }

    /** 출발 일시를 지정했는지 */
    public boolean hasDepartAt() {
        return departAt != null && !departAt.isBlank();
    }

    /** 출발 일시가 비어 있거나, 올바른 형식이고 오늘부터 MAX_DAYS_AHEAD 일 안인지 */
    public boolean isDepartAtValid(LocalDate today) {
        if (!hasDepartAt()) {
            return true;
        }
        try {
            LocalDate date = LocalDateTime.parse(departAt, DEPART_AT_FORMAT).toLocalDate();
            return !date.isBefore(today) && !date.isAfter(today.plusDays(MAX_DAYS_AHEAD));
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    private static boolean inKorea(double lon, double lat) {
        return lon >= 124 && lon <= 132 && lat >= 33 && lat <= 39;
    }
}
