package MobilLax.Common.Error;

import MobilLax.Common.Validation.InvalidInputException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Map;
import java.util.NoSuchElementException;

/**
 * 전역 예외 처리: 예외 종류에 맞는 HTTP 상태 코드로 응답한다
 * - 4xx: 요청 쪽 문제. 사용자가 고칠 수 있는 안내 문구를 준다
 * - 5xx: 서버 문제. 내부 정보(예외 메시지)는 응답에 넣지 않고 로그에만 남긴다
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 없는 주소·파일 → 404 */
    @ExceptionHandler({NoResourceFoundException.class, NoSuchElementException.class})
    public ResponseEntity<Map<String, String>> notFound(Exception ex) {
        return body(HttpStatus.NOT_FOUND, "요청한 페이지나 데이터를 찾을 수 없습니다.");
    }

    /** 지원하지 않는 HTTP 메서드 → 405 */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, String>> methodNotAllowed(HttpRequestMethodNotSupportedException ex) {
        return body(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 요청 방식입니다.");
    }

    /** 지원하지 않는 Content-Type → 415 */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Map<String, String>> unsupportedMediaType(HttpMediaTypeNotSupportedException ex) {
        return body(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "지원하지 않는 요청 형식입니다.");
    }

    /** 읽을 수 없는 본문, 빠진 값, 잘못된 형식 → 400 */
    @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class})
    public ResponseEntity<Map<String, String>> badRequest(Exception ex) {
        return body(HttpStatus.BAD_REQUEST, "요청 형식이 올바르지 않습니다.");
    }

    /**
     * 요청 DTO 의 형식·길이·범위 검증(@Valid) 실패 → 400 (보안 규칙 S10)
     * 어떤 값이 왜 틀렸는지는 응답에 싣지 않는다 (입력값을 되돌려 보내지 않음, S5). 필드 이름만 로그에 남긴다
     */
    @ExceptionHandler({InvalidInputException.class, MethodArgumentNotValidException.class,
            HandlerMethodValidationException.class, jakarta.validation.ConstraintViolationException.class})
    public ResponseEntity<Map<String, String>> invalidInput(Exception ex) {
        if (ex instanceof InvalidInputException invalid) {
            log.warn("입력값 검증 실패: {}", invalid.getFields());
        } else if (ex instanceof MethodArgumentNotValidException invalid) {
            log.warn("입력값 검증 실패: {}", invalid.getBindingResult().getFieldErrors().stream()
                    .map(org.springframework.validation.FieldError::getField).distinct().toList());
        } else {
            log.warn("입력값 검증 실패: {}", ex.getClass().getSimpleName());
        }
        return body(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다.");
    }

    /** 서비스가 검증에서 거절한 값 → 400 (사용자에게 보여 줄 문구로 던진 것만 그대로 싣는다) */
    @ExceptionHandler(UserFacingException.class)
    public ResponseEntity<Map<String, String>> userFacing(UserFacingException ex) {
        return body(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    /** 라이브러리 등이 던진 IllegalArgumentException → 400, 메시지는 내부 정보일 수 있어 로그에만 (S5) */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> illegalArgument(IllegalArgumentException ex) {
        log.warn("잘못된 요청 값: {} {}", ex.getClass().getSimpleName(), ex.getMessage());
        return body(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다.");
    }

    /** 코드에서 상태를 정해 던진 경우 그대로 */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> responseStatus(ResponseStatusException ex) {
        HttpStatusCode status = ex.getStatusCode();
        String message = ex.getReason() != null ? ex.getReason() : "요청을 처리할 수 없습니다.";
        return ResponseEntity.status(status).body(Map.of("error", message));
    }

    /** 그 밖의 예외 → 500 (내부 정보는 로그에만) */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> internalError(Exception ex) {
        // 스택트레이스에는 키가 든 요청 URL 이 섞일 수 있어 남기지 않는다. 메시지는 로그 패턴이 키를 가린다
        log.error("처리하지 못한 예외: {} {}", ex.getClass().getSimpleName(), ex.getMessage());
        return body(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.");
    }

    private static ResponseEntity<Map<String, String>> body(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("error", message));
    }
}
