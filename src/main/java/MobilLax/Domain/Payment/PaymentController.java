package MobilLax.Domain.Payment;

import MobilLax.Domain.Cart.CartItemEntity;
import MobilLax.Domain.Cart.CartItemRepository;
import MobilLax.Domain.Cart.CartItemRequestDTO;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/payment")
@RequiredArgsConstructor
public class PaymentController {

    private final CartItemRepository cartItemRepository;
    private final PaymentRepository paymentRepository;

    @Value("${portone_store}")
    private String storeId;

    @Value("${portone_channel_card}")
    private String channelKey;

    // 기존 장바구니 그룹 기반 결제 준비
    @PostMapping("/sdk-ready/{groupId}")
    public Map<String, Map<String, Object>> prepareSdkPayments(@PathVariable UUID groupId) {
        String email = getCurrentUserEmail();

        List<CartItemEntity> items = cartItemRepository.findByEmailAndTotalFareGroupId(email, groupId.toString());
        if (items.isEmpty()) throw new IllegalArgumentException("결제할 항목이 없습니다.");

        Map<String, List<CartItemEntity>> groupedByTransport = items.stream()
                .collect(Collectors.groupingBy(CartItemEntity::getMode));

        Map<String, Map<String, Object>> result = new HashMap<>();

        for (Map.Entry<String, List<CartItemEntity>> entry : groupedByTransport.entrySet()) {
            String transport = entry.getKey();
            List<CartItemEntity> transportItems = entry.getValue();

            int totalAmount = transportItems.stream().mapToInt(CartItemEntity::getRoutePayment).sum();

            String orderName = transportItems.size() == 1 ?
                    transportItems.get(0).getStartName() + " → " + transportItems.get(0).getEndName() :
                    transportItems.get(0).getStartName() + " → " + transportItems.get(transportItems.size() - 1).getEndName()
                            + " 외 " + (transportItems.size() - 1) + "건";

            Map<String, Object> paymentInfo = new HashMap<>();
            paymentInfo.put("storeId", storeId);
            paymentInfo.put("channelKey", channelKey);
            paymentInfo.put("paymentId", "payment-" + UUID.randomUUID());
            paymentInfo.put("orderName", orderName);
            paymentInfo.put("amount", totalAmount);
            paymentInfo.put("transportType", transport);
            paymentInfo.put("groupId", groupId.toString());

            result.put(transport, paymentInfo);
        }

        return result;
    }
    @PostMapping("/direct-sdk-ready")
    public ResponseEntity<?> prepareDirectSdkPayments(@RequestBody CartItemRequestDTO request) {
        String email = getCurrentUserEmail();

        List<CartItemRequestDTO.LegDTO> selectedLegs = request.getSelectedLegs();

        // ✅ 유효성 검사: 선택된 구간이 비어있는 경우
        if (selectedLegs == null || selectedLegs.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "선택된 구간이 없습니다."));
        }

        // ✅ 유효 구간 필터링: 요금 0 이하 또는 출발/도착지 누락된 경우 제외
        List<CartItemRequestDTO.LegDTO> validLegs = selectedLegs.stream()
                .filter(leg -> leg.getRoutePayment() > 0
                        && leg.getStartName() != null && !leg.getStartName().isBlank()
                        && leg.getEndName() != null && !leg.getEndName().isBlank())
                .toList();

        if (validLegs.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "요금이 있는 구간만 결제할 수 있습니다."));
        }

        // ✅ 교통수단별로 구간 그룹화
        Map<String, List<CartItemRequestDTO.LegDTO>> groupedByTransport = validLegs.stream()
                .collect(Collectors.groupingBy(CartItemRequestDTO.LegDTO::getMode));

        Map<String, Map<String, Object>> result = new HashMap<>();

        for (Map.Entry<String, List<CartItemRequestDTO.LegDTO>> entry : groupedByTransport.entrySet()) {
            String transport = entry.getKey();
            List<CartItemRequestDTO.LegDTO> legs = entry.getValue();

            int totalAmount = legs.stream().mapToInt(CartItemRequestDTO.LegDTO::getRoutePayment).sum();

            String orderName = legs.size() == 1
                    ? legs.get(0).getStartName() + " → " + legs.get(0).getEndName()
                    : legs.get(0).getStartName() + " → " + legs.get(legs.size() - 1).getEndName()
                    + " 외 " + (legs.size() - 1) + "건";

            String paymentId = "payment-" + UUID.randomUUID();
            String groupId = UUID.randomUUID().toString();

            Map<String, Object> paymentInfo = new HashMap<>();
            paymentInfo.put("storeId", storeId);
            paymentInfo.put("channelKey", channelKey);
            paymentInfo.put("paymentId", paymentId);
            paymentInfo.put("orderName", orderName);
            paymentInfo.put("amount", totalAmount);
            paymentInfo.put("groupId", groupId);
            paymentInfo.put("transportType", transport);

            result.put(transport, paymentInfo);
        }

        return ResponseEntity.ok(result);
    }

    // 결제 완료 저장
    @PostMapping("/complete")
    public String completePayment(@RequestBody PaymentRequest req) {
        String email = getCurrentUserEmail();

        boolean alreadyPaid = paymentRepository.existsByGroupIdAndTransportTypeAndStatus(
                req.getGroupId(), req.getTransportType(), PaymentEntity.PaymentStatus.SUCCESS);
        if (alreadyPaid) {
            return "이미 결제된 주문입니다.";
        }

        PaymentEntity entity = PaymentEntity.builder()
                .email(email)
                .amount(req.getAmount())
                .date(LocalDate.now())
                .groupId(req.getGroupId())
                .transportType(req.getTransportType())
                .status(PaymentEntity.PaymentStatus.SUCCESS)
                .build();

        paymentRepository.save(entity);
        cartItemRepository.markAsDeletedByEmailAndGroupId(email, req.getGroupId());  // isDeleted=true 처리

        return "ok";
    }

    // 결제 실패 저장
    @PostMapping("/fail")
    public String failPayment(@RequestBody PaymentRequest req) {
        String email = getCurrentUserEmail();

        Optional<PaymentEntity> existing = paymentRepository.findByPaymentId(req.getPaymentId());

        PaymentEntity entity = existing.orElseGet(() -> PaymentEntity.builder()
                .email(email)
                .paymentId(req.getPaymentId())
                .amount(req.getAmount())
                .date(LocalDate.now())
                .groupId(req.getGroupId())
                .transportType(req.getTransportType())
                .build());

        entity.setStatus(PaymentEntity.PaymentStatus.FAIL);
        paymentRepository.save(entity);

        return "fail recorded";
    }

    // 장바구니 복원 처리
    @PostMapping("/restore-cart")
    public String restoreCart(@RequestBody RestoreCartRequest req) {
        String email = getCurrentUserEmail();

        boolean isPaid = paymentRepository.existsByGroupIdAndTransportTypeAndStatus(
                req.getGroupId(), req.getTransportType(), PaymentEntity.PaymentStatus.SUCCESS
        );

        if (isPaid) {
            return "이미 결제된 항목입니다.";
        }

        cartItemRepository.restoreByEmailAndGroupIdAndTransport(email, req.getGroupId(), req.getTransportType());
        return "복원 완료";
    }

    @Getter
    @Setter
    public static class RestoreCartRequest {
        private String groupId;
        private String transportType;
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
