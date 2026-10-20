package MobilLax.Domain.Account;

/**
 * 계정 권한 이름 (v2.11, 보안 규칙 S12) — 한 곳에서만 정한다
 * - USER: 모든 로그인 사용자
 * - ADMIN_PENDING: 관리자 계정이 비밀번호로만 로그인한 상태. OTP 화면(/admin/otp)만 열린다
 * - ADMIN: OTP 까지 확인한 관리자. 로그인할 때는 주지 않고, OTP 확인 뒤에만 세션에 더한다
 */
public final class AccountRoles {

    public static final String USER = "USER";
    public static final String ADMIN = "ADMIN";
    public static final String ADMIN_PENDING = "ADMIN_PENDING";

    private AccountRoles() {
    }

    /** Spring Security 권한 문자열 (ROLE_ 접두어) */
    public static String authority(String role) {
        return "ROLE_" + role;
    }
}
