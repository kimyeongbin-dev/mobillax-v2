package MobilLax.Common.Validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.ReportAsSingleViolation;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

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
 * 계정 이메일: 형식과 길이 100자
 * (보안 규칙 S10, 규칙 값은 {@link InputRules})
 */
@Pattern(regexp = InputRules.EMAIL)
@Size(max = 100)
@ReportAsSingleViolation
@Constraint(validatedBy = {})
@Documented
@Target({FIELD, METHOD, PARAMETER, TYPE_USE, ANNOTATION_TYPE})
@Retention(RUNTIME)
public @interface AccountEmail {

    String message() default "이메일 형식이 올바르지 않거나 100자를 넘습니다.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
