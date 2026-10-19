package MobilLax.Domain.Nearby;

/**
 * 주변정보 분류 (카카오 로컬 카테고리 그룹 코드)
 * - 화면은 이 이름(enum)만 보낸다. 카카오에 넘기는 코드는 서버가 정한 값뿐이다 (보안 규칙 S10)
 */
public enum NearbyCategory {
    RESTAURANT("FD6", "음식점"),
    CAFE("CE7", "카페"),
    CONVENIENCE("CS2", "편의점"),
    SUBWAY("SW8", "지하철역"),
    LODGING("AD5", "숙박"),
    ATTRACTION("AT4", "관광명소"),
    PARKING("PK6", "주차장");

    private final String kakaoCode;
    private final String label;

    NearbyCategory(String kakaoCode, String label) {
        this.kakaoCode = kakaoCode;
        this.label = label;
    }

    public String kakaoCode() {
        return kakaoCode;
    }

    public String label() {
        return label;
    }
}
