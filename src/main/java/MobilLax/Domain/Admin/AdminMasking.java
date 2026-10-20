package MobilLax.Domain.Admin;

/**
 * 관리자 화면에 보이는 개인정보 가리기 (보안 규칙 S12)
 * - 계정을 구분할 만큼만 보여 준다. 어깨 너머로 보거나 화면을 캡처해도 전체 이메일·이름이 드러나지 않게
 */
final class AdminMasking {

    private AdminMasking() {
    }

    /** k***@gmail.com (도메인은 문의 대응에 필요해 보인다) */
    static String email(String email) {
        if (email == null) {
            return "";
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }

    /** 김** (첫 글자만) */
    static String name(String name) {
        if (name == null || name.isEmpty()) {
            return "";
        }
        return name.charAt(0) + "*".repeat(Math.max(1, name.length() - 1));
    }
}
