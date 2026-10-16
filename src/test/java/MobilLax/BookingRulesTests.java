package MobilLax;

import MobilLax.Domain.Cart.DepartureCheck;
import MobilLax.Service.UserAccountService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 예매·회원 입력 규칙 (외부 호출 없음)
 */
class BookingRulesTests {

    private static final String NOW = "202610161200";

    @Test
    void blocksDepartedOrUnknownTrips() {
        assertThat(DepartureCheck.isDeparted("202610161201", NOW)).isFalse();
        assertThat(DepartureCheck.isDeparted("202610161200", NOW)).isTrue();
        assertThat(DepartureCheck.isDeparted("202610160527", NOW)).isTrue();
        // 편을 고르기 전 방식으로 담긴 예전 항목 (출발 일시 없음)
        assertThat(DepartureCheck.isDeparted(null, NOW)).isTrue();
        assertThat(DepartureCheck.isDeparted("2026-10-16", NOW)).isTrue();
    }

    @Test
    void acceptsNormalNames() {
        UserAccountService.validateName("김영빈");
        UserAccountService.validateName("QA 테스트");
    }

    @Test
    void rejectsBrokenOrInvalidNames() {
        // CP949 로 보낸 "테스트"를 UTF-8 로 읽으면 생기는 깨진 문자(U+FFFD)
        assertThatThrownBy(() -> UserAccountService.validateName("�׽�Ʈ"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> UserAccountService.validateName(" "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> UserAccountService.validateName("이름\u0000"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> UserAccountService.validateName("가".repeat(UserAccountService.MAX_NAME_LENGTH + 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
