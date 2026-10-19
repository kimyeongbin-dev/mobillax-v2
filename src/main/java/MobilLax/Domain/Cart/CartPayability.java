package MobilLax.Domain.Cart;

import java.util.List;
import java.util.Optional;

/**
 * 장바구니 묶음을 결제할 수 있는지 (장바구니 화면과 결제 준비가 같은 규칙을 쓴다)
 * - 화면은 막힌 이유를 미리 보여 주고, 결제 준비는 같은 이유로 거절한다. 규칙이 두 곳에서 어긋나지 않게 여기에만 둔다
 */
public final class CartPayability {

    static final String DEPARTED = "출발 시각이 지났거나 알 수 없는 편이 있어 결제할 수 없습니다. 이 묶음을 삭제하고 편을 다시 골라 주세요.";
    static final String UNVERIFIED = "운임을 확인하지 않은 항목이 있어 결제할 수 없습니다. 이 묶음을 삭제하고 편을 다시 골라 주세요.";

    private CartPayability() {
    }

    /**
     * 결제할 수 없는 이유, 결제할 수 있으면 비어 있음
     *
     * @param items 한 묶음의 활성 항목
     * @param now   지금 한국 시간 yyyyMMddHHmm ({@link DepartureCheck#now()})
     */
    public static Optional<String> blockReason(List<CartItemEntity> items, String now) {
        if (items.stream().anyMatch(i -> DepartureCheck.isDeparted(i.getDepartureTime(), now))) {
            return Optional.of(DEPARTED);
        }
        // 담을 때 서버가 운임을 확인한 항목만 (v2.2 이전에 담은 항목은 브라우저가 보낸 운임이라 결제하지 않는다)
        if (items.stream().anyMatch(i -> !Boolean.TRUE.equals(i.getFareVerified()))) {
            return Optional.of(UNVERIFIED);
        }
        return Optional.empty();
    }
}
