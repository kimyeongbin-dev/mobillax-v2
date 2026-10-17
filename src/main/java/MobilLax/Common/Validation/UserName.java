package MobilLax.Common.Validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE_USE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * 사용자 이름: 1-30자, 깨진 글자(U+FFFD)·제어 문자 없음. 규칙은 {@link AccountRules#nameError}
 * (보안 규칙 S10, 규칙 값은 {@link InputRules})
 */
@Constraint(validatedBy = AccountRules.UserNameValidator.class)
@Documented
@Target({FIELD, METHOD, PARAMETER, TYPE_USE, ANNOTATION_TYPE})
@Retention(RUNTIME)
public @interface UserName {

    String message() default "이름이 올바르지 않습니다.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
