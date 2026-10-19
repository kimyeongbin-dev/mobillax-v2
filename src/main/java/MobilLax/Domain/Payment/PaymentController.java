package MobilLax.Domain.Payment;

import MobilLax.Common.Error.UserFacingException;
import MobilLax.Common.Time.KoreaTime;
import MobilLax.Domain.Cart.CartItemEntity;
import MobilLax.Domain.Cart.CartItemRepository;
import MobilLax.Domain.Cart.CartPayability;
import MobilLax.Domain.Cart.CartItemRequestDTO;
import MobilLax.Domain.Cart.DepartureCheck;
import MobilLax.Domain.Trip.TripVerifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * 결제 준비·완료·실패 (보안 규칙 S1, docs/SECURITY_RULES.md)
 * - 준비: 서버가 금액을 정해 결제 기록(PENDING)을 먼저 남기고, 그 결제 번호·금액으로만 결제창을 연다
 * - 완료: 브라우저의 "결제됨" 알림을 믿지 않고 PortOne 에서 상태(PAID)·금액·통화·상점을 확인한 뒤에만 SUCCESS
 *   금액이 다르거나 이미 결제된 묶음이면 PortOne 결제를 취소하고 FAIL
 */
@Slf4j
@RestController
@RequestMapping("/payment")
@RequiredArgsConstructor
public class PaymentController {

    private final CartItemRepository cartItemRepository;
    private final PaymentRepository paymentRepository;
    private final PortOneClient portOneClient;
    private final TripVerifier tripVerifier;

    @Value("${portone_store}")
    private String storeId;

    @Value("${portone_channel_card}")
    private String channelKey;

    // 기존 장바구니 그룹 기반 결제 준비
    @PostMapping("/sdk-ready/{groupId}")
    public Map<String, Map<String, Object>> prepareSdkPayments(@PathVariable UUID groupId) {
        String email = getCurrentUserEmail();

        // 삭제했거나 이미 결제한 묶음은 다시 결제 준비하지 않는다
        List<CartItemEntity> items = cartItemRepository.findActiveByGroupId(email, groupId.toString());
        if (items.isEmpty()) throw new UserFacingException("결제할 항목이 없습니다.");
        // 장바구니 화면이 미리 보여 주는 것과 같은 규칙 (출발한 편, 운임을 확인하지 않은 항목)
        CartPayability.blockReason(items, DepartureCheck.now()).ifPresent(reason -> {
            throw new UserFacingException(reason);
        });

        return prepareByTransport(email, groupId.toString(), items, CartItemEntity::getMode,
                CartItemEntity::getRoutePayment, CartItemEntity::getStartName, CartItemEntity::getEndName,
                CartItemEntity::getDepartureTime);
    }

    @PostMapping("/direct-sdk-ready")
    public Map<String, Map<String, Object>> prepareDirectSdkPayments(@RequestBody CartItemRequestDTO request) {
        String email = getCurrentUserEmail();

        // 구간 수·형식은 RequestBodyValidationAdvice 가 확인하고, 운임·이름·출발 시각·당일 여부는 서버가 TAGO 에서 확인한다 (S1·S10)
        List<CartItemRequestDTO.LegDTO> legs = request.getSelectedLegs();
        List<TripVerifier.VerifiedTrip> verified = legs.stream().map(tripVerifier::verify).toList();
        record Priced(String mode, TripVerifier.VerifiedTrip trip, String departureTime) {
        }
        List<Priced> priced = IntStream.range(0, legs.size())
                .mapToObj(i -> new Priced(legs.get(i).getMode(), verified.get(i), legs.get(i).getDepartureTime()))
                .toList();

        // 바로 결제는 장바구니 묶음이 없으므로 새 묶음 번호를 만든다
        return prepareByTransport(email, UUID.randomUUID().toString(), priced, Priced::mode,
                p -> p.trip().fare(), p -> p.trip().startName(), p -> p.trip().endName(), Priced::departureTime);
    }

    /**
     * 교통수단별로 결제 한 건씩: 서버가 정한 금액으로 결제 기록(PENDING)을 남기고 결제창에 넘길 값을 만든다
     */
    private <T> Map<String, Map<String, Object>> prepareByTransport(String email, String groupId, List<T> items,
                                                                    Function<T, String> mode, ToIntFunction<T> fare,
                                                                    Function<T, String> start, Function<T, String> end,
                                                                    Function<T, String> departure) {
        Map<String, List<T>> groupedByTransport = items.stream()
                .collect(Collectors.groupingBy(mode, LinkedHashMap::new, Collectors.toList()));

        Map<String, Map<String, Object>> result = new HashMap<>();
        for (Map.Entry<String, List<T>> entry : groupedByTransport.entrySet()) {
            String transport = entry.getKey();
            List<T> transportItems = entry.getValue();

            int totalAmount = transportItems.stream().mapToInt(fare).sum();
            T first = transportItems.get(0);
            String orderName = transportItems.size() == 1
                    ? start.apply(first) + " → " + end.apply(first)
                    : start.apply(first) + " → " + end.apply(transportItems.get(transportItems.size() - 1))
                    + " 외 " + (transportItems.size() - 1) + "건";

            // 결제 내역에 보여 줄 가장 이른 출발 일시 (서버가 확인한 편)
            String firstDeparture = transportItems.stream().map(departure).filter(Objects::nonNull)
                    .min(String::compareTo).orElse(null);

            String paymentId = "payment-" + UUID.randomUUID();
            paymentRepository.save(PaymentEntity.builder()
                    .email(email)
                    .paymentId(paymentId)
                    .amount(totalAmount)
                    .date(KoreaTime.today())
                    .groupId(groupId)
                    .transportType(transport)
                    .status(PaymentEntity.PaymentStatus.PENDING)
                    .orderName(orderName)
                    .departureTime(firstDeparture)
                    .build());

            Map<String, Object> paymentInfo = new HashMap<>();
            paymentInfo.put("storeId", storeId);
            paymentInfo.put("channelKey", channelKey);
            paymentInfo.put("paymentId", paymentId);
            paymentInfo.put("orderName", orderName);
            paymentInfo.put("amount", totalAmount);
            paymentInfo.put("transportType", transport);
            paymentInfo.put("groupId", groupId);

            result.put(transport, paymentInfo);
        }
        return result;
    }

    /**
     * 결제 완료: PortOne 에서 확인한 뒤에만 SUCCESS 로 기록하고 장바구니 묶음을 비운다
     */
    @PostMapping("/complete")
    public Map<String, String> completePayment(@RequestBody PaymentRequest req) {
        String email = getCurrentUserEmail();
        PaymentEntity payment = findOwnPayment(email, req);

        if (payment.getStatus() == PaymentEntity.PaymentStatus.SUCCESS) {
            return Map.of("message", "이미 결제된 주문입니다.");
        }
        if (payment.getStatus() != PaymentEntity.PaymentStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 실패로 처리된 결제입니다.");
        }

        PortOneClient.Payment portOne;
        try {
            portOne = portOneClient.find(payment.getPaymentId())
                    .orElseThrow(() -> new UserFacingException("결제 내역을 찾을 수 없습니다."));
        } catch (IllegalStateException e) {
            log.warn("결제 확인 실패 ({}): {}", payment.getPaymentId(), e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "결제를 확인하지 못했습니다. 잠시 후 마이페이지에서 확인해 주세요.");
        }
        if (!"PAID".equals(portOne.status())) {
            throw new UserFacingException("결제가 완료되지 않았습니다.");
        }

        // 금액·통화·상점이 결제 준비 때 서버가 정한 값과 다르면 위조로 보고 취소
        if (portOne.total() != payment.getAmount() || !"KRW".equals(portOne.currency()) || !storeId.equals(portOne.storeId())) {
            log.warn("결제 금액 불일치 ({}): 기록 {}원, PortOne {}원 {}", payment.getPaymentId(), payment.getAmount(),
                    portOne.total(), portOne.currency());
            cancelAndFail(payment, "결제 금액 불일치");
            throw new UserFacingException("결제 금액이 주문과 달라 결제를 취소했습니다.");
        }
        // 같은 묶음·교통수단을 다른 결제로 이미 냈다면 이번 결제는 중복이므로 취소
        if (paymentRepository.existsByGroupIdAndTransportTypeAndStatus(
                payment.getGroupId(), payment.getTransportType(), PaymentEntity.PaymentStatus.SUCCESS)) {
            cancelAndFail(payment, "중복 결제");
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 결제된 주문이라 이번 결제를 취소했습니다.");
        }

        payment.setStatus(PaymentEntity.PaymentStatus.SUCCESS);
        paymentRepository.save(payment);
        cartItemRepository.markAsDeletedByEmailAndGroupId(email, payment.getGroupId());  // isDeleted=true 처리

        return Map.of("message", "ok");
    }

    /**
     * 결제 실패·취소 알림: 자기 결제 중 아직 확인되지 않은(PENDING) 것만 FAIL 로 바꾼다
     */
    @PostMapping("/fail")
    public Map<String, String> failPayment(@RequestBody PaymentRequest req) {
        String email = getCurrentUserEmail();
        PaymentEntity payment = findOwnPayment(email, req);

        // 완료가 확인된 결제는 브라우저 알림으로 실패로 바꾸지 않는다
        if (payment.getStatus() == PaymentEntity.PaymentStatus.PENDING) {
            payment.setStatus(PaymentEntity.PaymentStatus.FAIL);
            paymentRepository.save(payment);
        }
        return Map.of("message", "fail recorded");
    }

    /** 결제 준비 때 만든 자기 결제 기록. 없거나 다른 사용자의 것이면 404 (있다는 사실도 알리지 않는다) */
    private PaymentEntity findOwnPayment(String email, PaymentRequest req) {
        if (req.getPaymentId() == null || req.getPaymentId().isBlank()) {
            throw new UserFacingException("결제 번호가 없습니다.");
        }
        return paymentRepository.findByPaymentId(req.getPaymentId())
                .filter(p -> email.equals(p.getEmail()))
                .orElseThrow(NoSuchElementException::new);
    }

    private void cancelAndFail(PaymentEntity payment, String reason) {
        try {
            portOneClient.cancel(payment.getPaymentId(), reason);
        } catch (IllegalStateException e) {
            // 취소하지 못하면 FAIL 기록과 로그로 남겨 수동 환불한다
            log.error("결제 취소 실패 ({}, {}): {}", payment.getPaymentId(), reason, e.getMessage());
        }
        payment.setStatus(PaymentEntity.PaymentStatus.FAIL);
        paymentRepository.save(payment);
    }

    private String getCurrentUserEmail() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new RuntimeException("인증되지 않은 사용자입니다.");
        }

        Object principal = auth.getPrincipal();
        if (principal instanceof UserDetails) {
            return ((UserDetails) principal).getUsername();
        } else {
            return principal.toString();
        }
    }
}
