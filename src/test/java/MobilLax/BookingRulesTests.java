package MobilLax;

import MobilLax.Common.Validation.AccountRules;
import MobilLax.Domain.Cart.DepartureCheck;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

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
        assertThat(AccountRules.nameError("김영빈")).isEmpty();
        assertThat(AccountRules.nameError("QA 테스트")).isEmpty();
    }

    @Test
    void rejectsBrokenOrInvalidNames() {
        // CP949 로 보낸 "테스트"를 UTF-8 로 읽으면 생기는 깨진 문자(U+FFFD)
        assertThat(AccountRules.nameError("�׽�Ʈ")).isPresent();
        assertThat(AccountRules.nameError(" ")).isPresent();
        assertThat(AccountRules.nameError("이름\u0000")).isPresent();
        assertThat(AccountRules.nameError("가".repeat(AccountRules.MAX_NAME_LENGTH + 1))).isPresent();
    }

    @Test
    void passwordRules() {
        assertThat(AccountRules.passwordError("FAKE_pass1")).isEmpty();
        assertThat(AccountRules.passwordError("1234567")).isPresent();
        assertThat(AccountRules.passwordError("a".repeat(AccountRules.MAX_PASSWORD_LENGTH + 1))).isPresent();
        // 한글 25자 = 75바이트: BCrypt 72바이트 한계를 넘는다
        assertThat(AccountRules.passwordError("가".repeat(25))).isPresent();
        assertThat(AccountRules.passwordError(null)).isPresent();
    }
}
