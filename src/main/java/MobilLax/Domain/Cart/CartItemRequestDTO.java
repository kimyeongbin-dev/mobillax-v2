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
        private String departureTime; // 고른 편의 출발 일시 yyyyMMddHHmm (v1.6, TAGO 편)
        // 편을 조회한 TAGO 조건: 서버가 같은 조건으로 다시 조회해 운임을 확인한다 (v2.2, 보안 규칙 S1)
        private String kind;          // TRAIN, EXPRESS_BUS, INTERCITY_BUS, AIRPLANE
        private String depId;         // 출발 TAGO ID
        private String arrId;         // 도착 TAGO ID
    }
}
