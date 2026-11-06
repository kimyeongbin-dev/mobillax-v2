package MobilLax.Common.Web;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 방금 비밀번호를 다시 확인한 세션만 이 요청을 쓸 수 있다 (v2.14, 보안 규칙 S14)
 * 확인하지 않았으면 RecentPasswordInterceptor 가 컨트롤러 앞에서 막는다
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RecentPassword {

    /** 화면 요청: 확인한 뒤 돌아갈 화면. 비워 두면 fetch 요청으로 보고 403 JSON 으로 알린다 */
    String returnTo() default "";
}
