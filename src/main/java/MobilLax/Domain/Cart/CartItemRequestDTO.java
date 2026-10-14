package MobilLax.Domain.Cart;

import lombok.Data;
import java.util.List;

@Data
public class CartItemRequestDTO {
    private List<LegDTO> selectedLegs;

    @Data
    public static class LegDTO {
        private String mode;
        private String route;         // "고속버스:춘천-동대구" 등
        private String routeId;       // "300083-300053-EXP-D001" 등
        private int routePayment;     // 각 구간의 결제 금액
        private String startName;
        private String endName;
    }
}
