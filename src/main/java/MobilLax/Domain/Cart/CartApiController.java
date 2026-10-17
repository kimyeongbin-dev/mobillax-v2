package MobilLax.Domain.Cart;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
public class CartApiController {

    private final CartService cartService;

    /**
     * 사용자가 선택한 구간만 장바구니에 추가하고, 총 결제 금액 반환
     */
    @PostMapping("/add")
    public ResponseEntity<Integer> addSelectedToCart(@RequestBody CartItemRequestDTO request) {
        // 구간 수·형식은 RequestBodyValidationAdvice 가, 운임·이름은 서버가 TAGO 에서 확인한다 (보안 규칙 S1·S10)
        int totalFare = cartService.saveCartItems(request.getSelectedLegs());
        return ResponseEntity.ok(totalFare);
    }

    /**
     * 장바구니 목록 조회 (UUID 그룹별로)
     */
    @GetMapping("/list")
    public ResponseEntity<Map<String, List<CartItemEntity>>> getCartList() {
        return ResponseEntity.ok(cartService.getGroupedCartItems());
    }

    /**
     * 장바구니 전체 비우기
     */
    @DeleteMapping("/clear")
    public ResponseEntity<Void> clearCart() {
        cartService.clearCart();
        return ResponseEntity.noContent().build();
    }

    /**
     * 특정 그룹 (UUID 기반) 삭제
     */
    @DeleteMapping("/group/{groupId}")
    public ResponseEntity<Void> deleteGroup(@PathVariable UUID groupId) {
        cartService.deleteGroup(groupId.toString());
        return ResponseEntity.noContent().build();
    }
}
