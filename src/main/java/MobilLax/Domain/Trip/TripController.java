package MobilLax.Domain.Trip;

import MobilLax.Common.RateLimit.RateLimitPolicy;
import MobilLax.Common.RateLimit.RateLimited;
import MobilLax.Common.Validation.DateYmd;
import MobilLax.Common.Validation.TagoId;
import MobilLax.Common.Validation.TransitKindName;
import MobilLax.Domain.Place.TransitKind;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 장거리 구간의 편 조회 API — 날짜 범위·예매 가능 판단은 TripSearchService 가 한다
 * 오류는 공통 예외 응답 {"error": 문구} (v2.10 전에는 {"message": 문구} 였다)
 */
@RestController
@RequestMapping("/api/trips")
@RequiredArgsConstructor
public class TripController {

    private final TripSearchService tripSearchService;

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

    @RateLimited(RateLimitPolicy.EXTERNAL_LOOKUP)
    @PostMapping
    public TripSearchService.TripResponse trips(@RequestBody TripRequest request) {
        // 형식은 RequestBodyValidationAdvice 가 TripRequest 의 제약으로 먼저 검사한다
        return tripSearchService.search(TransitKind.valueOf(request.kind()), request.depId(), request.arrId(),
                request.date());
    }
}
