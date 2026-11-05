package MobilLax.Common.Validation;

import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * 폼 검증 오류를 화면 문구 하나로 (보안 규칙 S10)
 * - 오류 순서는 보장되지 않으므로, 화면의 입력 순서대로 첫 오류를 고른다
 * - 문구는 제약 어노테이션의 사용자용 문장이다 (입력값을 되풀이하지 않음)
 */
public final class FormErrors {

    private FormErrors() {
    }

    /**
     * @param fieldOrder 화면에 보이는 입력 순서 (목록에 없는 필드·객체 오류는 맨 뒤)
     */
    public static String first(BindingResult binding, String... fieldOrder) {
        List<String> order = Arrays.asList(fieldOrder);
        return binding.getAllErrors().stream()
                .min(Comparator.comparingInt(e -> rank(e, order)))
                .map(FormErrors::message)
                .orElse(DEFAULT_MESSAGE);
    }

    private static final String DEFAULT_MESSAGE = "입력값이 올바르지 않습니다.";

    /** 형 변환 실패(없는 enum 값 등)의 기본 문구는 내부 클래스 이름을 담고 있어 내보내지 않는다 (S5) */
    private static String message(ObjectError error) {
        return error instanceof FieldError field && field.isBindingFailure() ? DEFAULT_MESSAGE : error.getDefaultMessage();
    }

    private static int rank(ObjectError error, List<String> order) {
        int index = error instanceof FieldError field ? order.indexOf(field.getField()) : -1;
        return index >= 0 ? index : order.size();
    }
}
