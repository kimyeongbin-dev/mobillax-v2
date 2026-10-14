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
    @Column(nullable = false)
    private String totalFareGroupId;
    @Column(name = "is_deleted", nullable = false)
    private boolean isDeleted;

}
