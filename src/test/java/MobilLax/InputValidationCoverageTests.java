package MobilLax;

import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Constraint;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.ui.Model;
import org.springframework.validation.Errors;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.lang.annotation.Annotation;
import java.security.Principal;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 보안 회귀 테스트 S10 (구조) — 앞으로 추가하는 API 도 입력 검증을 빠뜨릴 수 없게 한다 (docs/SECURITY_RULES.md)
 * 이 테스트를 지우거나 약하게 바꾸지 않는다. 규칙을 바꿔야 하면 문서와 함께 리뷰를 거친다.
 *
 * 실제로 등록된 모든 컨트롤러 메서드의 입력을 훑어, 다음을 만족하지 않으면 실패한다.
 * - @RequestBody: 본문 타입에 제약이 선언되어 있다 (검사는 RequestBodyValidationAdvice 가 항상 한다)
 * - 폼 객체(@ModelAttribute 또는 어노테이션 없는 객체): @Valid 가 있고 타입에 제약이 선언되어 있다
 * - 경로·쿼리 값: UUID·숫자·열거형처럼 타입 자체가 형식을 보장하거나, 제약 어노테이션이 붙어 있다
 */
@SpringBootTest
class InputValidationCoverageTests {

    /** 프레임워크가 채우는 값 (브라우저 입력이 아님) */
    private static final List<Class<?>> FRAMEWORK_TYPES = List.of(
            Model.class, RedirectAttributes.class, Errors.class, ServletRequest.class, ServletResponse.class,
            HttpSession.class, Principal.class, Locale.class);

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping mapping;

    @Autowired
    Validator validator;

    @Test
    void everyBrowserInputDeclaresValidation() {
        List<String> problems = new ArrayList<>();
        mapping.getHandlerMethods().forEach((info, handler) -> {
            if (!handler.getBeanType().getPackageName().startsWith("MobilLax")) {
                return; // 스프링 기본 오류 컨트롤러 등
            }
            for (MethodParameter parameter : handler.getMethodParameters()) {
                String problem = check(parameter);
                if (problem != null) {
                    problems.add(name(handler, parameter) + ": " + problem);
                }
            }
        });
        assertThat(problems).as("검증이 선언되지 않은 입력").isEmpty();
    }

    @Test
    void scannerSeesTheControllers() {
        // 위 검사가 실제 컨트롤러를 훑고 있는지 (빈 목록이라 통과하는 일이 없도록)
        long handlers = mapping.getHandlerMethods().values().stream()
                .filter(h -> h.getBeanType().getPackageName().startsWith("MobilLax")).count();
        assertThat(handlers).isGreaterThan(15);
    }

    private String check(MethodParameter parameter) {
        Class<?> type = parameter.getParameterType();
        if (parameter.hasParameterAnnotation(AuthenticationPrincipal.class)
                || FRAMEWORK_TYPES.stream().anyMatch(t -> t.isAssignableFrom(type))) {
            return null;
        }
        if (parameter.hasParameterAnnotation(RequestBody.class)) {
            return constrained(type) ? null : "본문 타입 " + type.getSimpleName() + " 에 제약이 없다";
        }
        if (isSimple(type)) {
            // @RequestParam·@PathVariable·어노테이션 없는 단순 값
            return safeType(type) || hasConstraint(parameter) ? null
                    : "문자열 입력에 제약 어노테이션(@Size·@TagoId 등)이 없다";
        }
        // 폼 객체 (@ModelAttribute 또는 어노테이션 없는 객체)
        if (!parameter.hasParameterAnnotation(Valid.class) && !parameter.hasParameterAnnotation(Validated.class)) {
            return "폼 객체 " + type.getSimpleName() + " 에 @Valid 가 없다";
        }
        return constrained(type) ? null : "폼 객체 " + type.getSimpleName() + " 에 제약이 없다";
    }

    private boolean constrained(Class<?> type) {
        return validator.getConstraintsForClass(type).isBeanConstrained();
    }

    private static boolean isSimple(Class<?> type) {
        return BeanUtils.isSimpleValueType(type) || UUID.class == type;
    }

    /** 타입 변환 자체가 형식을 보장하는 값 (틀리면 400) */
    private static boolean safeType(Class<?> type) {
        return type.isPrimitive() || Number.class.isAssignableFrom(type) || Boolean.class == type
                || type.isEnum() || UUID.class == type || TemporalAccessor.class.isAssignableFrom(type);
    }

    private static boolean hasConstraint(MethodParameter parameter) {
        return Arrays.stream(parameter.getParameterAnnotations())
                .map(Annotation::annotationType)
                .anyMatch(a -> a.isAnnotationPresent(Constraint.class));
    }

    private static String name(HandlerMethod handler, MethodParameter parameter) {
        return handler.getBeanType().getSimpleName() + "." + handler.getMethod().getName()
                + "(" + parameter.getParameterType().getSimpleName() + " #" + parameter.getParameterIndex() + ")";
    }
}
