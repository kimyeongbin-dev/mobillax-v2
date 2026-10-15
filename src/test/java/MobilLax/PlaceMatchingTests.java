package MobilLax;

import MobilLax.Domain.Place.JusoAddress;
import MobilLax.Domain.Place.KakaoAddress;
import MobilLax.Domain.Place.PlaceMatching;
import MobilLax.Domain.Place.TransitKind;
import MobilLax.Domain.Place.TransitPlace;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TMAP 장소 ↔ TAGO 장소 매칭 규칙 (외부 호출 없음)
 * 예시 주소는 2026-10-15 도로명주소·카카오 API 실제 응답에서 가져왔다
 */
class PlaceMatchingTests {

    private static TransitPlace placeWith(String sgg, String road, int buildingNo, String emd, int lotNo, int lotSub) {
        TransitPlace place = new TransitPlace(TransitKind.EXPRESS_BUS, "NAEK010", "서울경부", null);
        place.applyAddress(new JusoAddress("강남고속버스터미널", "서울특별시 서초구 신반포로 194", "서울특별시 서초구 반포동 19-4",
                "서울특별시", sgg, road, buildingNo, 0, emd, lotNo, lotSub, false));
        return place;
    }

    @Test
    void matchesByRoadAddress() {
        TransitPlace place = placeWith("서초구", "신반포로", 194, "반포동", 19, 4);
        KakaoAddress kakao = new KakaoAddress("서울", "서초구", "반포동", "신반포로", 194, null, 19, 99, null, null);
        assertThat(PlaceMatching.sameAddress(place, kakao)).isTrue();
    }

    @Test
    void matchesByLotAddressWhenRoadDiffers() {
        TransitPlace place = placeWith("서초구", "신반포로", 194, "반포동", 19, 4);
        KakaoAddress kakao = new KakaoAddress("서울", "서초구", "반포동", "사평대로", 205, null, 19, 4, null, null);
        assertThat(PlaceMatching.sameAddress(place, kakao)).isTrue();
    }

    @Test
    void doesNotMatchDifferentBuildingOrDistrict() {
        TransitPlace place = placeWith("서초구", "신반포로", 194, "반포동", 19, 4);
        assertThat(PlaceMatching.sameAddress(place,
                new KakaoAddress("서울", "서초구", "반포동", "신반포로", 190, null, 19, 3, null, null))).isFalse();
        assertThat(PlaceMatching.sameAddress(place,
                new KakaoAddress("서울", "강남구", "반포동", "신반포로", 194, null, 19, 4, null, null))).isFalse();
    }

    @Test
    void normalizesStationAndTerminalNames() {
        assertThat(PlaceMatching.normalizeName("서울고속버스터미널")).isEqualTo("서울");
        assertThat(PlaceMatching.normalizeName("김포국제공항")).isEqualTo("김포");
        assertThat(PlaceMatching.normalizeName("진주(개양)")).isEqualTo("진주개양");
        assertThat(PlaceMatching.normalizeName("서울역")).isEqualTo("서울");
        // 이름 자체가 접미어뿐이면 지우지 않는다
        assertThat(PlaceMatching.normalizeName("역")).isEqualTo("역");
    }

    @Test
    void scoresNameSimilarity() {
        assertThat(PlaceMatching.nameScore("김포", "김포국제공항")).isEqualTo(3);
        assertThat(PlaceMatching.nameScore("서울고속버스터미널", "서울경부")).isEqualTo(2);
        assertThat(PlaceMatching.nameScore("진주(개양)", "개양")).isEqualTo(2);
        assertThat(PlaceMatching.nameScore("부산", "대전")).isZero();
    }

    @Test
    void normalizesSidoNames() {
        assertThat(PlaceMatching.sidoKey("서울")).isEqualTo(PlaceMatching.sidoKey("서울특별시"));
        assertThat(PlaceMatching.sidoKey("경남")).isEqualTo(PlaceMatching.sidoKey("경상남도"));
        assertThat(PlaceMatching.sidoKey("강원도")).isEqualTo(PlaceMatching.sidoKey("강원특별자치도"));
        assertThat(PlaceMatching.sidoKey("충청북도")).isEqualTo("충북");
    }

    @Test
    void filtersBuildingsByTransitKind() {
        assertThat(PlaceMatching.looksLikeKind(TransitKind.TRAIN, "서울역(철도역)")).isTrue();
        assertThat(PlaceMatching.looksLikeKind(TransitKind.TRAIN, "4호선 서울역")).isFalse();
        assertThat(PlaceMatching.looksLikeKind(TransitKind.INTERCITY_BUS, "강릉시외버스터미널")).isTrue();
        assertThat(PlaceMatching.looksLikeKind(TransitKind.AIRPLANE, "김포공항, 롯데몰, 스카이시티")).isTrue();
    }

    @Test
    void measuresDistance() {
        // 서울역 ↔ 부산역 직선 거리 약 325km
        double d = PlaceMatching.distanceMeters(37.5546, 126.9706, 35.1151, 129.0415);
        assertThat(d).isBetween(320_000d, 330_000d);
    }

    @Test
    void decidesTransitKindFromTmapLeg() {
        assertThat(TransitKind.fromTmapLeg("EXPRESSBUS", 22, null)).isEqualTo(TransitKind.INTERCITY_BUS);
        assertThat(TransitKind.fromTmapLeg("EXPRESSBUS", 23, null)).isEqualTo(TransitKind.EXPRESS_BUS);
        assertThat(TransitKind.fromTmapLeg("EXPRESSBUS", null, "시외버스:김해공항-부전시장")).isEqualTo(TransitKind.INTERCITY_BUS);
        assertThat(TransitKind.fromTmapLeg("BUS", 11, null)).isNull();
    }
}
