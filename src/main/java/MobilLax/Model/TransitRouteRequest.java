package MobilLax.Model;

import MobilLax.Common.Validation.DateTimeMinute;
import MobilLax.Common.Validation.KoreanLatitude;
import MobilLax.Common.Validation.KoreanLongitude;
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
public record TransitRouteRequest(@KoreanLongitude double startX, @KoreanLatitude double startY,
                                  @KoreanLongitude double endX, @KoreanLatitude double endY,
                                  @DateTimeMinute String departAt) {

    /** 출발 일시 형식 (TMAP searchDttm 과 같음) */
    public static final DateTimeFormatter DEPART_AT_FORMAT =
            DateTimeFormatter.ofPattern("uuuuMMddHHmm").withResolverStyle(ResolverStyle.STRICT);

    /** 조회할 수 있는 가장 먼 날짜 (오늘부터 며칠 뒤까지) */
    public static final int MAX_DAYS_AHEAD = 14;


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

}
