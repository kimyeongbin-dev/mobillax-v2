package MobilLax.Common.Validation;

import java.util.List;

/**
 * 요청 값이 선언한 제약을 어겼다 (400) — 보안 규칙 S10
 * 어떤 필드가 틀렸는지만 담고, 보낸 값은 담지 않는다 (응답·로그에 입력값을 되풀이하지 않음, S5)
 */
public class InvalidInputException extends RuntimeException {

    private final List<String> fields;

    public InvalidInputException(List<String> fields) {
        super("입력값 검증 실패: " + fields);
        this.fields = List.copyOf(fields);
    }

    public List<String> getFields() {
        return fields;
    }
}
