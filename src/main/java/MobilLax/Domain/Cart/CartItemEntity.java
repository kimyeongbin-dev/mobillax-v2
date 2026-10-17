package MobilLax.Domain.Cart;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "cart_item_entity")
public class CartItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String email; // 유저 식별자 (또는 FK)

    private String mode;
    private String route;
    private String routeId;
    private int routePayment;
    private String startName;
    private String endName;
    // 고른 편의 출발 일시 yyyyMMddHHmm (v1.6, TAGO 편)
    @Column(length = 12)
    private String departureTime;
    @Column(nullable = false)
    private String totalFareGroupId;
    @Column(name = "is_deleted", nullable = false)
    private boolean isDeleted;
    // 담을 때 서버가 TAGO 에서 운임을 확인했는지 (v2.2, 보안 규칙 S1). 그 전에 담은 항목은 null → 결제 불가
    @Column(name = "fare_verified")
    private Boolean fareVerified;

}
