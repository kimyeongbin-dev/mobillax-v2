package MobilLax.Domain.Trip;

import java.util.Map;

/**
 * 예매 편을 화면에 보일 때 쓰는 표시 규칙 (장바구니·예매 내역 공용)
 * - 화면 문자열은 서버가 만든다 (보안 규칙 S10). 같은 표시를 화면마다 따로 만들지 않는다
 */
public final class TripDisplay {

    /** 결제 단위(TMAP mode)별 이름. 결제창은 이 단위로 한 번씩 열린다 */
    private static final Map<String, String> TRANSPORT_LABEL = Map.of(
            "TRAIN", "기차", "EXPRESSBUS", "고속·시외버스", "AIRPLANE", "항공");

    private TripDisplay() {
    }

    /** 결제 단위 이름 (모르는 값은 그대로) */
    public static String transportLabel(String mode) {
        return TRANSPORT_LABEL.getOrDefault(mode, mode);
    }

    /** yyyyMMddHHmm → "10월 18일 07:02", 형식이 아니면 빈 문자열 */
    public static String departure(String stamp) {
        if (stamp == null || !stamp.matches("\\d{12}")) {
            return "";
        }
        return Integer.parseInt(stamp.substring(4, 6)) + "월 "
                + Integer.parseInt(stamp.substring(6, 8)) + "일 "
                + stamp.substring(8, 10) + ":" + stamp.substring(10, 12);
    }
}
