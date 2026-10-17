package MobilLax.Common.Validation;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Set;

/**
 * 모든 @RequestBody 를 컨트롤러에 넘기기 전에 검증하는 공통 계층 (보안 규칙 S10)
 * - 미들웨어처럼 동작한다: 컨트롤러가 @Valid 를 빠뜨려도 본문 DTO 에 선언한 제약(@TagoId 등)이 항상 검사된다
 * - 위반하면 InvalidInputException → GlobalExceptionHandler 가 400 "입력값이 올바르지 않습니다." 로 응답
 * - DTO 에 제약을 선언하지 않은 본문은 InputValidationCoverageTests 가 빌드에서 막는다
 */
@ControllerAdvice
@RequiredArgsConstructor
public class RequestBodyValidationAdvice extends RequestBodyAdviceAdapter {

    private final Validator validator;

    @Override
    public boolean supports(MethodParameter parameter, Type targetType,
                            Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object afterBodyRead(Object body, HttpInputMessage inputMessage, MethodParameter parameter,
                                Type targetType, Class<? extends HttpMessageConverter<?>> converterType) {
        Set<ConstraintViolation<Object>> violations = validator.validate(body);
        if (!violations.isEmpty()) {
            List<String> fields = violations.stream()
                    .map(v -> v.getPropertyPath().toString())
                    .distinct()
                    .sorted()
                    .toList();
            throw new InvalidInputException(fields);
        }
        return body;
    }
}
