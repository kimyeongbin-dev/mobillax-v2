package MobilLax.Common.Validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.ReportAsSingleViolation;
import jakarta.validation.constraints.Pattern;

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
 * 서버가 결제 준비 때 만든 결제 번호 (payment-...)
 * (보안 규칙 S10, 규칙 값은 {@link InputRules})
 */
@Pattern(regexp = InputRules.PAYMENT_ID)
@ReportAsSingleViolation
@Constraint(validatedBy = {})
@Documented
@Target({FIELD, METHOD, PARAMETER, TYPE_USE, ANNOTATION_TYPE})
@Retention(RUNTIME)
public @interface PaymentId {

    String message() default "결제 번호 형식이 올바르지 않습니다.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
