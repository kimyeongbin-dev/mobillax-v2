package MobilLax.Common.Validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * 계정 입력 규칙 (이름·비밀번호) — 보안 규칙 S10
 * - 규칙은 여기 한 곳에만 둔다. 폼 DTO 는 @UserName·@Password 로, 서비스는 check 메서드로 같은 규칙을 쓴다
 * - 위반 문구는 사용자에게 그대로 보여 줄 수 있는 문장이다 (입력값을 되풀이하지 않음)
 */
public final class AccountRules {

    public static final int MAX_NAME_LENGTH = 30;
    public static final int MIN_PASSWORD_LENGTH = 8;
    public static final int MAX_PASSWORD_LENGTH = 64;
    /** BCrypt 는 72바이트 뒤를 버린다: 앞 72바이트만 같으면 로그인되는 일을 막는다 */
    static final int MAX_PASSWORD_BYTES = 72;

    private AccountRules() {
    }

    /**
     * 이름 규칙 위반 문구
     * - 요청 본문이 UTF-8 이 아닌 인코딩(예: Windows CP949)으로 오면 읽지 못한 글자가 U+FFFD 로 바뀌어 저장된다 (v1.8)
     */
    public static Optional<String> nameError(String name) {
        if (name == null || name.isBlank()) {
            return Optional.of("이름을 입력해 주세요.");
        }
        if (name.length() > MAX_NAME_LENGTH) {
            return Optional.of("이름은 " + MAX_NAME_LENGTH + "자 이하로 입력해 주세요.");
        }
        if (name.indexOf('�') >= 0 || name.chars().anyMatch(Character::isISOControl)) {
            return Optional.of("이름에 읽을 수 없는 문자가 있습니다. 다시 입력해 주세요.");
        }
        return Optional.empty();
    }

    /** 비밀번호 규칙 위반 문구 */
    public static Optional<String> passwordError(String password) {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH || password.length() > MAX_PASSWORD_LENGTH) {
            return Optional.of("비밀번호는 " + MIN_PASSWORD_LENGTH + "자 이상 " + MAX_PASSWORD_LENGTH + "자 이하로 입력해 주세요.");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES
                || password.chars().anyMatch(Character::isISOControl)) {
            return Optional.of("비밀번호에 쓸 수 없는 문자가 있거나 너무 깁니다.");
        }
        return Optional.empty();
    }

    /** 위반 문구를 제약 위반 메시지로 내보내는 공통 처리 */
    private static boolean report(Optional<String> error, ConstraintValidatorContext context) {
        error.ifPresent(message -> {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(message).addConstraintViolation();
        });
        return error.isEmpty();
    }

    public static class UserNameValidator implements ConstraintValidator<UserName, String> {
        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            return report(nameError(value), context);
        }
    }

    public static class PasswordValidator implements ConstraintValidator<Password, String> {
        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            return report(passwordError(value), context);
        }
    }
}
