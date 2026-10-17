package MobilLax.Domain.Trip;

import MobilLax.Domain.Place.TransitKind;
import MobilLax.Model.TransitRouteRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 장거리 구간의 편 조회 API
 * - 매칭된 TAGO 출발·도착 ID 와 날짜로 그날의 편·운임을 API 가 준 그대로 돌려준다
 * - 예매는 당일 출발편만: 오늘이고 아직 출발하지 않은 편만 bookable
 */
@Slf4j
@RestController
@RequestMapping("/api/trips")
@RequiredArgsConstructor
public class TripController {

    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("uuuuMMdd").withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter MINUTE = DateTimeFormatter.ofPattern("uuuuMMddHHmm");

    private final TagoTripClient tripClient;

    /**
     * @param kind  TRAIN, EXPRESS_BUS, INTERCITY_BUS, AIRPLANE (/api/place/match 결과의 kind)
     * @param depId 출발 TAGO ID
     * @param arrId 도착 TAGO ID
     * @param date  출발일 yyyyMMdd (오늘부터 14일)
     */
    public record TripRequest(String kind, String depId, String arrId, String date) {
    }

    public record TripView(String no, String grade, String departAt, String arriveAt, int fare, boolean bookable) {
    }

    public record TripResponse(boolean bookableDate, List<TripView> trips) {
    }

    @PostMapping
    public ResponseEntity<?> trips(@RequestBody TripRequest request) {
        if (request == null) {
            return badRequest("교통수단 또는 날짜가 올바르지 않습니다.");
        }
        TransitKind kind;
        LocalDate date;
        try {
            kind = TransitKind.valueOf(request.kind());
            date = LocalDate.parse(request.date(), DATE);
        } catch (RuntimeException e) {
            return badRequest("교통수단 또는 날짜가 올바르지 않습니다.");
        }
        if (!isId(request.depId()) || !isId(request.arrId())) {
            return badRequest("출발·도착 역(터미널) 정보가 올바르지 않습니다.");
        }
        LocalDate today = LocalDate.now(KOREA);
        if (date.isBefore(today) || date.isAfter(today.plusDays(TransitRouteRequest.MAX_DAYS_AHEAD))) {
            return badRequest("오늘부터 " + TransitRouteRequest.MAX_DAYS_AHEAD + "일 안의 날짜만 조회할 수 있습니다.");
        }

        List<TagoTripClient.Trip> trips;
        try {
            trips = tripClient.trips(kind, request.depId(), request.arrId(), request.date());
        } catch (RuntimeException e) {
            // 요청 URL 에 키가 있으므로 예외 메시지는 남기지 않는다
            log.warn("편 조회 실패 ({} {}→{}): {}", kind, request.depId(), request.arrId(), e.getClass().getSimpleName());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("message", "편 정보를 불러오지 못했습니다."));
        }

        boolean bookableDate = date.equals(today);
        String now = LocalDateTime.now(KOREA).format(MINUTE);
        List<TripView> views = trips.stream()
                .filter(t -> !t.departAt().isEmpty() && t.fare() > 0)
                .sorted(Comparator.comparing(TagoTripClient.Trip::departAt))
                .map(t -> new TripView(t.no(), t.grade(), t.departAt(), t.arriveAt(), t.fare(),
                        bookableDate && t.departAt().compareTo(now) > 0))
                .toList();
        return ResponseEntity.ok(new TripResponse(bookableDate, views));
    }

    private static boolean isId(String id) {
        return id != null && id.matches("[A-Za-z0-9]{1,40}");
    }

    private static ResponseEntity<Map<String, String>> badRequest(String message) {
        return ResponseEntity.badRequest().body(Map.of("message", message));
    }
}
