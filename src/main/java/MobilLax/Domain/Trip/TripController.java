package MobilLax.Domain.Trip;

import MobilLax.Common.Time.KoreaTime;
import MobilLax.Common.Validation.DateYmd;
import MobilLax.Common.Validation.TagoId;
import MobilLax.Common.Validation.TransitKindName;
import MobilLax.Domain.Place.TransitKind;
import MobilLax.Domain.Route.TransitRouteRequest;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
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

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("uuuuMMdd").withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter MINUTE = DateTimeFormatter.ofPattern("uuuuMMddHHmm");

    private final TagoTripClient tripClient;

    /**
     * @param kind  TRAIN, EXPRESS_BUS, INTERCITY_BUS, AIRPLANE (/api/place/match 결과의 kind)
     * @param depId 출발 TAGO ID
     * @param arrId 도착 TAGO ID
     * @param date  출발일 yyyyMMdd (오늘부터 14일)
     */
    public record TripRequest(@NotNull @TransitKindName String kind,
                              @NotNull @TagoId String depId,
                              @NotNull @TagoId String arrId,
                              @NotNull @DateYmd String date) {
    }

    public record TripView(String no, String grade, String departAt, String arriveAt, int fare, boolean bookable) {
    }

    public record TripResponse(boolean bookableDate, List<TripView> trips) {
    }

    @PostMapping
    public ResponseEntity<?> trips(@RequestBody TripRequest request) {
        // 형식은 RequestBodyValidationAdvice 가 TripRequest 의 제약으로 먼저 검사한다
        TransitKind kind = TransitKind.valueOf(request.kind());
        LocalDate date;
        try {
            date = LocalDate.parse(request.date(), DATE);
        } catch (RuntimeException e) {
            return badRequest("교통수단 또는 날짜가 올바르지 않습니다."); // 20261399 처럼 형식만 맞는 날짜
        }
        LocalDate today = KoreaTime.today();
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
        String now = KoreaTime.now().format(MINUTE);
        List<TripView> views = trips.stream()
                .filter(t -> !t.departAt().isEmpty() && t.fare() > 0)
                .sorted(Comparator.comparing(TagoTripClient.Trip::departAt))
                .map(t -> new TripView(t.no(), t.grade(), t.departAt(), t.arriveAt(), t.fare(),
                        bookableDate && t.departAt().compareTo(now) > 0))
                .toList();
        return ResponseEntity.ok(new TripResponse(bookableDate, views));
    }


    private static ResponseEntity<Map<String, String>> badRequest(String message) {
        return ResponseEntity.badRequest().body(Map.of("message", message));
    }
}
