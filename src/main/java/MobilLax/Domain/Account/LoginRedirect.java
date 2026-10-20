package MobilLax.Domain.Account;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 로그인 뒤 돌아갈 화면 (v2.9) — 비로그인으로 경로를 보다가 "로그인하고 예매하기"를 누른 사용자를 그 화면으로 돌려보낸다
 * - 정해 둔 내부 화면만 받는다. 다른 사이트 주소(https://..., //...)로 보내는 열린 리다이렉트를 막는다
 */
public final class LoginRedirect {

    /** 로그인 폼이 넘기는 매개변수 이름 */
    public static final String PARAM = "next";
    public static final String DEFAULT = "/home";

    private static final Pattern ALLOWED = Pattern.compile("/(home|route|compare|nearby)(\\?index=\\d{1,2})?");

    private LoginRedirect() {
    }

    /** 허용한 화면이면 그대로, 아니면 빈 값 */
    public static Optional<String> safe(String next) {
        return next != null && ALLOWED.matcher(next).matches() ? Optional.of(next) : Optional.empty();
    }
}
