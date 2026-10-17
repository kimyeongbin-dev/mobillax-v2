package MobilLax.Domain.Cart;

import MobilLax.Common.Validation.DateTimeMinute;
import MobilLax.Common.Validation.InputRules;
import MobilLax.Common.Validation.PayableMode;
import MobilLax.Common.Validation.TagoId;
import MobilLax.Common.Validation.TransitKindName;
import MobilLax.Common.Validation.TripNo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 장바구니 담기·바로 결제 요청 (보안 규칙 S1·S10)
 * - 형식은 RequestBodyValidationAdvice 가 이 제약으로 먼저 검사한다
 * - 서버는 kind·depId·arrId·routeId·departureTime 으로 TAGO 를 다시 조회해 운임·이름·노선 표시를 정한다
 * - route·startName·endName 은 브라우저가 바꿀 수 있어 저장·결제창에 쓰지 않는다 (서버가 TAGO 값으로 만든다)
 */
@Data
public class CartItemRequestDTO {

    @NotEmpty
    @Size(max = InputRules.MAX_LEGS)
    private List<@NotNull @Valid LegDTO> selectedLegs;

    @Data
    public static class LegDTO {
        @NotNull @PayableMode
        private String mode;
        private String route;         // 화면 표시용 (서버는 쓰지 않음)
        @NotNull @TripNo
        private String routeId;       // 고른 편의 TAGO 편 번호
        @Min(1) @Max(InputRules.MAX_FARE)
        private int routePayment;     // 화면에 보인 운임 (서버가 TAGO 운임과 같은지 확인)
        private String startName;     // 화면 표시용 (서버는 쓰지 않음)
        private String endName;       // 화면 표시용 (서버는 쓰지 않음)
        @NotNull @DateTimeMinute
        private String departureTime; // 고른 편의 출발 일시 yyyyMMddHHmm (v1.6, TAGO 편)
        // 편을 조회한 TAGO 조건: 서버가 같은 조건으로 다시 조회해 운임을 확인한다 (v2.2, 보안 규칙 S1)
        @NotNull @TransitKindName
        private String kind;          // TRAIN, EXPRESS_BUS, INTERCITY_BUS, AIRPLANE
        @NotNull @TagoId
        private String depId;         // 출발 TAGO ID
        @NotNull @TagoId
        private String arrId;         // 도착 TAGO ID
    }
}
