package MobilLax.Common.RateLimit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 컨트롤러 메서드의 호출 횟수를 제한한다 (RateLimitInterceptor 가 처리, 보안 규칙 S11)
 * 로그인 없이 열린 API 는 반드시 붙인다 (RateLimitCoverageTests 가 검사)
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimited {

    RateLimitPolicy value();
}
