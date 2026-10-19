package MobilLax.Common.Error;

/**
 * 사용자에게 그대로 보여 줄 문구를 담은 요청 거절 (400) — 보안 규칙 S5
 * - 응답에는 이 예외의 메시지만 싣는다. 라이브러리·프레임워크가 던진 IllegalArgumentException 은
 *   내부 정보(클래스명, 값 형식 등)가 섞일 수 있어 일반 문구로 바꾼다 (GlobalExceptionHandler)
 * - 기존 catch (IllegalArgumentException) 와 호환되도록 하위 클래스로 둔다
 */
public class UserFacingException extends IllegalArgumentException {

    public UserFacingException(String message) {
        super(message);
    }
}
