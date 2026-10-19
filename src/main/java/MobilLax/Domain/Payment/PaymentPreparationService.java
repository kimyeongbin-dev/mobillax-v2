package MobilLax.Domain.Payment;

import MobilLax.Common.Error.UserFacingException;
import MobilLax.Common.Time.KoreaTime;
import MobilLax.Domain.Cart.CartItemEntity;
import MobilLax.Domain.Cart.CartItemRepository;
import MobilLax.Domain.Cart.CartItemRequestDTO;
import MobilLax.Domain.Cart.CartPayability;
import MobilLax.Domain.Cart.DepartureCheck;
import MobilLax.Domain.Trip.TripVerifier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * 결제 준비 (보안 규칙 S1): 서버가 금액을 정해 결제 기록(PENDING)을 먼저 남기고, 그 결제 번호·금액으로만 결제창을 연다
 * - 결제창은 교통수단마다 한 번 (장바구니 화면이 미리 보여 주는 단위와 같다)
 */
@Service
@RequiredArgsConstructor
public class PaymentPreparationService {

    private final CartItemRepository cartItemRepository;
    private final PaymentRepository paymentRepository;
    private final TripVerifier tripVerifier;
    private final PortOneStore store;

    /**
     * 결제창 하나에 넘길 값 (교통수단별)
     *
     * @param groupId 장바구니 묶음 번호 (바로 결제는 새로 만든 번호)
     */
    public record PaymentWindow(String storeId, String channelKey, String paymentId, String orderName, int amount,
                                String transportType, String groupId) {
    }

    /** 장바구니 묶음 결제. 삭제·결제한 묶음, 출발한 편, 운임을 확인하지 않은 항목은 거절한다 */
    public Map<String, PaymentWindow> prepareCartGroup(String email, String groupId) {
        List<CartItemEntity> items = cartItemRepository.findActiveByGroupId(email, groupId);
        if (items.isEmpty()) {
            throw new UserFacingException("결제할 항목이 없습니다.");
        }
        // 장바구니 화면이 미리 보여 주는 것과 같은 규칙
        CartPayability.blockReason(items, DepartureCheck.now()).ifPresent(reason -> {
            throw new UserFacingException(reason);
        });
        return prepareByTransport(email, groupId, items, CartItemEntity::getMode, CartItemEntity::getRoutePayment,
                CartItemEntity::getStartName, CartItemEntity::getEndName, CartItemEntity::getDepartureTime);
    }

    /** 상세 화면에서 바로 결제. 운임·이름·출발 시각·당일 여부는 서버가 TAGO 에서 다시 확인한다 (S1·S10) */
    public Map<String, PaymentWindow> prepareDirect(String email, List<CartItemRequestDTO.LegDTO> legs) {
        List<TripVerifier.VerifiedTrip> verified = legs.stream().map(tripVerifier::verify).toList();
        record Priced(String mode, TripVerifier.VerifiedTrip trip, String departureTime) {
        }
        List<Priced> priced = IntStream.range(0, legs.size())
                .mapToObj(i -> new Priced(legs.get(i).getMode(), verified.get(i), legs.get(i).getDepartureTime()))
                .toList();
        return prepareByTransport(email, UUID.randomUUID().toString(), priced, Priced::mode,
                p -> p.trip().fare(), p -> p.trip().startName(), p -> p.trip().endName(), Priced::departureTime);
    }

    /** 교통수단별로 결제 한 건씩: 서버가 정한 금액으로 결제 기록(PENDING)을 남기고 결제창에 넘길 값을 만든다 */
    private <T> Map<String, PaymentWindow> prepareByTransport(String email, String groupId, List<T> items,
                                                              Function<T, String> mode, ToIntFunction<T> fare,
                                                              Function<T, String> start, Function<T, String> end,
                                                              Function<T, String> departure) {
        Map<String, List<T>> byTransport = items.stream()
                .collect(Collectors.groupingBy(mode, LinkedHashMap::new, Collectors.toList()));

        Map<String, PaymentWindow> windows = new LinkedHashMap<>();
        byTransport.forEach((transport, legs) -> {
            int amount = legs.stream().mapToInt(fare).sum();
            String orderName = orderName(legs, start, end);
            // 결제 내역에 보여 줄 가장 이른 출발 일시 (서버가 확인한 편)
            String firstDeparture = legs.stream().map(departure).filter(Objects::nonNull)
                    .min(String::compareTo).orElse(null);

            String paymentId = "payment-" + UUID.randomUUID();
            paymentRepository.save(PaymentEntity.builder()
                    .email(email)
                    .paymentId(paymentId)
                    .amount(amount)
                    .date(KoreaTime.today())
                    .groupId(groupId)
                    .transportType(transport)
                    .status(PaymentEntity.PaymentStatus.PENDING)
                    .orderName(orderName)
                    .departureTime(firstDeparture)
                    .build());

            windows.put(transport, new PaymentWindow(store.storeId(), store.channelKey(), paymentId, orderName, amount,
                    transport, groupId));
        });
        return windows;
    }

    /** "서울 → 대전", 여러 구간이면 "서울 → 목포 외 2건" */
    private static <T> String orderName(List<T> legs, Function<T, String> start, Function<T, String> end) {
        String first = start.apply(legs.get(0));
        if (legs.size() == 1) {
            return first + " → " + end.apply(legs.get(0));
        }
        return first + " → " + end.apply(legs.get(legs.size() - 1)) + " 외 " + (legs.size() - 1) + "건";
    }
}
