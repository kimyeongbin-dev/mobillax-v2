package MobilLax.Domain.Payment;

import MobilLax.Domain.Cart.CartItemRequestDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.Map;
import java.util.UUID;

/**
 * 결제 준비·완료·실패 API (보안 규칙 S1, docs/SECURITY_RULES.md)
 * - 규칙은 PaymentPreparationService(서버가 금액을 정함)와 PaymentConfirmationService(PortOne 에서 확인)에 있다
 * - 로그인한 사용자(Principal)의 장바구니·결제만 다룬다 (S4). 본문 형식은 RequestBodyValidationAdvice 가 먼저 검사한다 (S10)
 */
@RestController
@RequestMapping("/payment")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentPreparationService preparationService;
    private final PaymentConfirmationService confirmationService;

    /** 장바구니 묶음 결제 준비: { 교통수단: 결제창 값 } */
    @PostMapping("/sdk-ready/{groupId}")
    public Map<String, PaymentPreparationService.PaymentWindow> prepareCartGroup(@PathVariable UUID groupId,
                                                                               Principal principal) {
        return preparationService.prepareCartGroup(principal.getName(), groupId.toString());
    }

    /** 상세 화면에서 바로 결제 준비 */
    @PostMapping("/direct-sdk-ready")
    public Map<String, PaymentPreparationService.PaymentWindow> prepareDirect(@RequestBody CartItemRequestDTO request,
                                                                            Principal principal) {
        return preparationService.prepareDirect(principal.getName(), request.getSelectedLegs());
    }

    @PostMapping("/complete")
    public Map<String, String> complete(@RequestBody PaymentRequest request, Principal principal) {
        return Map.of("message", confirmationService.complete(principal.getName(), request.getPaymentId()));
    }

    @PostMapping("/fail")
    public Map<String, String> fail(@RequestBody PaymentRequest request, Principal principal) {
        confirmationService.fail(principal.getName(), request.getPaymentId());
        return Map.of("message", "fail recorded");
    }
}
