package MobilLax.Domain.Nearby;

import MobilLax.Domain.Place.KakaoPlace;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 주변정보 화면에 보낼 장소 한 건 (서버가 만든 표시 값, 보안 규칙 S10)
 * - 좌표는 보내지 않는다. 지도는 카카오맵 장소 페이지(placeUrl)로 연다
 *
 * @param category 카카오 분류의 마지막 단계 (예: "음식점 > 한식 > 국밥" → "국밥")
 * @param distance "350m", "1.2km" (모르면 빈 문자열)
 * @param placeUrl 카카오맵 장소 페이지. 장소 ID 가 숫자일 때만 서버가 만든다
 */
public record NearbyPlaceView(String name, String category, String address, String phone, String distance,
                              String placeUrl) {

    private static final Pattern KAKAO_PLACE_ID = Pattern.compile("\\d{1,20}");
    private static final String KAKAO_PLACE_URL = "https://place.map.kakao.com/";

    public static NearbyPlaceView from(KakaoPlace place) {
        return new NearbyPlaceView(
                place.name(),
                lastCategory(place.categoryName()),
                place.roadAddress().isBlank() ? place.address() : place.roadAddress(),
                place.phone(),
                distance(place.distance()),
                KAKAO_PLACE_ID.matcher(place.id()).matches() ? KAKAO_PLACE_URL + place.id() : "");
    }

    private static String lastCategory(String categoryName) {
        String[] parts = categoryName.split(">");
        return parts[parts.length - 1].trim();
    }

    static String distance(int meters) {
        if (meters < 0) {
            return "";
        }
        return meters < 1000 ? meters + "m" : String.format(Locale.ROOT, "%.1fkm", meters / 1000.0);
    }
}
