package MobilLax.Common.Usage;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 이 API 가 성공(2xx)하면 이용 통계에 한 번 센다 (v2.13, UsageInterceptor). 기능 코드는 통계 저장을 알 필요가 없다 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface CountsUsage {
    UsageMetric value();
}
