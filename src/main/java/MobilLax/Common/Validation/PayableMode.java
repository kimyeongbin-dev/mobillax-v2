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
 * 예매 대상 TMAP 구간 mode (TRAIN, EXPRESSBUS, AIRPLANE)
 * (보안 규칙 S10, 규칙 값은 {@link InputRules})
 */
@Pattern(regexp = InputRules.PAYABLE_MODE)
@ReportAsSingleViolation
@Constraint(validatedBy = {})
@Documented
@Target({FIELD, METHOD, PARAMETER, TYPE_USE, ANNOTATION_TYPE})
@Retention(RUNTIME)
public @interface PayableMode {

    String message() default "예매할 수 없는 교통수단입니다.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
