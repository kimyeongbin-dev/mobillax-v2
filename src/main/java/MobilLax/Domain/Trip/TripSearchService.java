package MobilLax.Domain.Trip;

import MobilLax.Common.Error.UserFacingException;
import MobilLax.Common.Time.KoreaTime;
import MobilLax.Domain.Place.TransitKind;
import MobilLax.Domain.Route.TransitRouteRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.Comparator;
import java.util.List;

/**
 * 장거리 구간의 편 조회 규칙 (v2.10, 컨트롤러에서 옮김)
 * - 조회는 오늘부터 14일 안의 날짜만
 * - 예매는 당일 출발편만: 오늘이고 아직 출발하지 않은 편만 bookable
 * - 출발 시각·운임이 없는 편은 뺀다
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TripSearchService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("uuuuMMdd").withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter MINUTE = DateTimeFormatter.ofPattern("uuuuMMddHHmm");

    private final TagoTripClient tripClient;

    public record TripView(String no, String grade, String departAt, String arriveAt, int fare, boolean bookable) {
    }

    public record TripResponse(boolean bookableDate, List<TripView> trips) {
    }

    /**
     * @param date 출발일 yyyyMMdd (형식은 컨트롤러 입구에서 검사했다)
     * @throws UserFacingException     없는 날짜(20261399 등), 조회 범위 밖 날짜 → 400
     * @throws ResponseStatusException TAGO 조회 실패 → 502
     */
    public TripResponse search(TransitKind kind, String depId, String arrId, String date) {
        LocalDate day = parseDate(date);
        LocalDate today = KoreaTime.today();
        if (day.isBefore(today) || day.isAfter(today.plusDays(TransitRouteRequest.MAX_DAYS_AHEAD))) {
            throw new UserFacingException("오늘부터 " + TransitRouteRequest.MAX_DAYS_AHEAD + "일 안의 날짜만 조회할 수 있습니다.");
        }

        List<TagoTripClient.Trip> trips;
        try {
            trips = tripClient.trips(kind, depId, arrId, date);
        } catch (RuntimeException e) {
            // 요청 URL 에 키가 있으므로 예외 메시지는 남기지 않는다
            log.warn("편 조회 실패 ({} {}→{}): {}", kind, depId, arrId, e.getClass().getSimpleName());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "편 정보를 불러오지 못했습니다.");
        }

        boolean bookableDate = day.equals(today);
        String now = KoreaTime.now().format(MINUTE);
        List<TripView> views = trips.stream()
                .filter(t -> !t.departAt().isEmpty() && t.fare() > 0)
                .sorted(Comparator.comparing(TagoTripClient.Trip::departAt))
                .map(t -> new TripView(t.no(), t.grade(), t.departAt(), t.arriveAt(), t.fare(),
                        bookableDate && t.departAt().compareTo(now) > 0))
                .toList();
        return new TripResponse(bookableDate, views);
    }

    private static LocalDate parseDate(String date) {
        try {
            return LocalDate.parse(date, DATE);
        } catch (RuntimeException e) {
            throw new UserFacingException("교통수단 또는 날짜가 올바르지 않습니다.");
        }
    }
}
