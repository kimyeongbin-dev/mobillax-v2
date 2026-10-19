package MobilLax.Domain.Trip;

import MobilLax.Common.Error.UserFacingException;
import MobilLax.Domain.Cart.CartItemRequestDTO;
import MobilLax.Domain.Cart.DepartureCheck;
import MobilLax.Domain.Place.TransitKind;
import MobilLax.Domain.Place.TransitPlace;
import MobilLax.Domain.Place.TransitPlaceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/**
 * 예매 구간을 서버가 TAGO 에서 다시 확인한다 (보안 규칙 S1·S10, docs/SECURITY_RULES.md)
 * - 브라우저가 보낸 운임·이름·노선 표시는 믿지 않는다: 고른 편(편 번호 + 출발 일시)을 TAGO 에서 다시 찾아,
 *   운임이 화면에 보인 값과 같을 때만 통과하고, 저장·결제에는 TAGO 이름과 서버가 만든 표시만 쓴다
 * - 당일 출발이고 아직 출발하지 않은 편만 (예매 규칙)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TripVerifier {

    private static final Map<TransitKind, String> KIND_LABEL = Map.of(
            TransitKind.TRAIN, "기차", TransitKind.EXPRESS_BUS, "고속버스",
            TransitKind.INTERCITY_BUS, "시외버스", TransitKind.AIRPLANE, "항공");

    private final TagoTripClient tripClient;
    private final TransitPlaceRepository placeRepository;

    /**
     * 서버가 확인한 편
     *
     * @param fare      TAGO 운임 (= 결제 금액)
     * @param startName TAGO 출발 역·터미널·공항 이름
     * @param endName   TAGO 도착 이름
     * @param route     서버가 만든 노선 표시 (예: "기차:서울-부산 · KTX 08:00 출발")
     */
    public record VerifiedTrip(int fare, String startName, String endName, String route) {
    }

    /**
     * @throws UserFacingException     구간 정보가 올바르지 않거나, 편이 없거나, 운임이 다를 때 (400)
     * @throws ResponseStatusException TAGO 를 부르지 못했을 때 (502)
     */
    public VerifiedTrip verify(CartItemRequestDTO.LegDTO leg) {
        TransitKind kind = kindOf(leg);
        TransitPlace start = placeRepository.findByKindAndTagoId(kind, leg.getDepId())
                .orElseThrow(() -> new UserFacingException("출발 역·터미널 정보를 찾을 수 없습니다. 편을 다시 골라 주세요."));
        TransitPlace end = placeRepository.findByKindAndTagoId(kind, leg.getArrId())
                .orElseThrow(() -> new UserFacingException("도착 역·터미널 정보를 찾을 수 없습니다. 편을 다시 골라 주세요."));
        String label = start.getName() + " → " + end.getName();

        String now = DepartureCheck.now();
        if (DepartureCheck.isDeparted(leg.getDepartureTime(), now)) {
            throw new UserFacingException("이미 출발했거나 출발 시각을 알 수 없는 편은 예매할 수 없습니다: " + label);
        }
        String date = leg.getDepartureTime().substring(0, 8);
        if (!date.equals(now.substring(0, 8))) {
            throw new UserFacingException("당일 출발편만 예매할 수 있습니다: " + label);
        }

        List<TagoTripClient.Trip> trips;
        try {
            trips = tripClient.trips(kind, leg.getDepId(), leg.getArrId(), date);
        } catch (RuntimeException e) {
            // 요청 URL 에 키가 있으므로 예외 메시지는 남기지 않는다
            log.warn("운임 확인 실패 ({} {}→{}): {}", kind, leg.getDepId(), leg.getArrId(), e.getClass().getSimpleName());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "편 정보를 확인하지 못했습니다. 잠시 후 다시 시도해 주세요.");
        }

        List<TagoTripClient.Trip> same = trips.stream()
                .filter(t -> leg.getRouteId().equals(t.no()) && leg.getDepartureTime().equals(t.departAt()) && t.fare() > 0)
                .toList();
        if (same.isEmpty()) {
            throw new UserFacingException("고른 편을 찾을 수 없습니다. 편을 다시 골라 주세요: " + label);
        }
        TagoTripClient.Trip trip = same.stream()
                .filter(t -> t.fare() == leg.getRoutePayment())
                .findFirst()
                .orElseThrow(() -> new UserFacingException("운임이 편 정보와 다릅니다. 편을 다시 골라 주세요: " + label));

        String time = trip.departAt().substring(8, 10) + ":" + trip.departAt().substring(10, 12);
        String route = KIND_LABEL.get(kind) + ":" + start.getName() + "-" + end.getName()
                + " · " + trip.grade() + " " + time + " 출발";
        return new VerifiedTrip(trip.fare(), start.getName(), end.getName(), route);
    }

    /** 구간의 교통수단(TMAP mode)과 편 조회에 쓴 교통수단(kind)이 맞는지까지 확인 */
    private static TransitKind kindOf(CartItemRequestDTO.LegDTO leg) {
        TransitKind kind;
        try {
            kind = TransitKind.valueOf(String.valueOf(leg.getKind()));
        } catch (IllegalArgumentException e) {
            throw new UserFacingException("예매할 수 없는 교통수단입니다.");
        }
        boolean matches = switch (kind) {
            case TRAIN -> "TRAIN".equals(leg.getMode());
            case AIRPLANE -> "AIRPLANE".equals(leg.getMode());
            case EXPRESS_BUS, INTERCITY_BUS -> "EXPRESSBUS".equals(leg.getMode());
        };
        if (!matches) {
            throw new UserFacingException("예매할 수 없는 교통수단입니다.");
        }
        return kind;
    }
}
