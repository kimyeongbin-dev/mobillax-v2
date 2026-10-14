package MobilLax.Domain.MyPage;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MyPageDto {

    private String name;          // 사용자 이름
    private String email;         // 사용자 이메일
    private String recentRoute;   // 최근 이용 경로
    private int totalPayment;     // 총 결제 금액
    private int cartCount;        // 장바구니 건수
}
