package MobilLax.Domain.Cart;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

/**
 * 장바구니 API. 로그인한 사용자(Principal)의 장바구니만 다룬다 (보안 규칙 S4)
 */
@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
public class CartApiController {

    private final CartService cartService;

    /** 고른 구간을 담고 합계 운임을 돌려준다. 구간 수·형식은 RequestBodyValidationAdvice, 운임·이름은 서버가 TAGO 로 확인 (S1·S10) */
    @PostMapping("/add")
    public ResponseEntity<Integer> addSelectedToCart(@RequestBody CartItemRequestDTO request, Principal principal) {
        return ResponseEntity.ok(cartService.saveCartItems(principal.getName(), request.getSelectedLegs()));
    }

    /** 장바구니 화면용 묶음 목록 (서버가 만든 표시 값과 결제 가능 여부만, DB 항목은 내보내지 않는다) */
    @GetMapping("/list")
    public ResponseEntity<List<CartGroupView>> getCartList(Principal principal) {
        return ResponseEntity.ok(cartService.getCartView(principal.getName()));
    }

    @DeleteMapping("/clear")
    public ResponseEntity<Void> clearCart(Principal principal) {
        cartService.clearCart(principal.getName());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/group/{groupId}")
    public ResponseEntity<Void> deleteGroup(@PathVariable UUID groupId, Principal principal) {
        cartService.deleteGroup(principal.getName(), groupId.toString());
        return ResponseEntity.noContent().build();
    }
}
