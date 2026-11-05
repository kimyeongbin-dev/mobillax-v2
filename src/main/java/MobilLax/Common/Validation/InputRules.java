package MobilLax.Common.Validation;

/**
 * 브라우저에서 오는 값의 형식 규칙 모음 (보안 규칙 S10, docs/SECURITY_RULES.md)
 * - 정규식·범위는 여기 한 곳에만 두고, DTO 는 이 값을 쓰는 제약 어노테이션(@TagoId 등)만 붙인다
 * - 브라우저가 보낸 값은 개발자도구로 얼마든지 바꿀 수 있으므로, 서버가 받는 모든 값은 형식·길이·범위를 확인한다
 * - 화면에 다시 보여 줄 문자열은 가능하면 서버가 만든 값(TAGO 이름 등)을 쓰고, 사용자 입력을 그대로 저장하지 않는다
 */
public final class InputRules {

    private InputRules() {
    }

    /** TMAP 구간 mode 중 예매 대상 */
    public static final String PAYABLE_MODE = "TRAIN|EXPRESSBUS|AIRPLANE";

    /** TAGO 조회에 쓰는 교통수단 (TransitKind) */
    public static final String TRANSIT_KIND = "TRAIN|EXPRESS_BUS|INTERCITY_BUS|AIRPLANE";

    /** TAGO 역·터미널·공항 ID (예: NAT010000, NAEK010) */
    public static final String TAGO_ID = "[A-Za-z0-9]{1,40}";

    /** TAGO 편 번호 (열차번호·노선 ID·편명) */
    public static final String TRIP_NO = "[A-Za-z0-9_-]{1,40}";

    /** 출발 일시 yyyyMMddHHmm */
    public static final String DATETIME_MINUTE = "\\d{12}";

    /** 날짜 yyyyMMdd */
    public static final String DATE = "\\d{8}";

    /** 서버가 결제 준비 때 만든 결제 번호 (payment-UUID) */
    public static final String PAYMENT_ID = "payment-[A-Za-z0-9-]{1,80}";

    /** 관리자 OTP: 인증 앱의 6자리 숫자 (v2.11) */
    public static final String OTP_CODE = "\\d{6}";

    /** 관리자 OTP 복구 코드: 소문자·숫자 4자리 두 묶음 (예: k3f9-2m7q) */
    public static final String RECOVERY_CODE = "[a-z0-9]{4}-[a-z0-9]{4}";

    /** 장소 이름 등 짧은 표시용 문자열: 꺾쇠·따옴표·제어 문자 없이 */
    public static final String PLAIN_TEXT = "[^<>\"'`\\p{Cntrl}]*";

    /**
     * 사용자가 쓰는 글 (1:1 문의 제목·내용, 관리자 답변, v2.12): 줄바꿈·탭은 되고 그 밖의 제어 문자는 안 된다
     * 따옴표·꺾쇠는 받는다(문장에 흔히 쓰임). 화면에는 th:text 로만 내보내 이스케이프한다 (web-escape 의 th:utext 검사)
     * 반복 그룹 대신 문자 클래스 하나로 써서 긴 글에서도 정규식이 재귀하지 않는다
     */
    public static final String MESSAGE_TEXT = "[^\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]*";

    /** 계정 이메일 (회원가입 때부터 쓰던 형식) */
    public static final String EMAIL = "^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$";

    /** 국내 좌표 범위 (TMAP·카카오가 주는 장소) */
    public static final String LAT_MIN = "33.0";
    public static final String LAT_MAX = "39.0";
    public static final String LON_MIN = "124.0";
    public static final String LON_MAX = "132.0";

    /** 한 번에 담거나 결제할 수 있는 구간 수 */
    public static final int MAX_LEGS = 10;

    /** 구간 한 개의 운임 상한 (원). TAGO 운임이 이보다 크면 비정상으로 본다 */
    public static final int MAX_FARE = 1_000_000;
}
