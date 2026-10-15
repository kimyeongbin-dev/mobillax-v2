package MobilLax.Domain.Place;

import java.util.Map;
import java.util.Objects;

/**
 * TMAP 장소와 TAGO 장소를 맞추는 규칙 (외부 호출 없는 순수 함수)
 * - 주소: 도로명(시군구·도로명·건물번호) 또는 지번(시군구·읍면동·번지)이 같으면 같은 곳
 * - 이름: 역·터미널·공항 같은 말을 떼고 비교해 후보를 고른다
 * - 거리: 주소가 어긋나는 공항·승강장은 좌표 거리로 보완
 */
public final class PlaceMatching {

    /** 거리로 같은 곳이라고 볼 최대 거리(m) */
    public static final int MAX_DISTANCE_METERS = 1000;

    private PlaceMatching() {
    }

    /** 카카오의 줄인 시도 이름 → 정식 이름 */
    private static final Map<String, String> SIDO = Map.ofEntries(
            Map.entry("서울", "서울특별시"), Map.entry("부산", "부산광역시"), Map.entry("대구", "대구광역시"),
            Map.entry("인천", "인천광역시"), Map.entry("광주", "광주광역시"), Map.entry("대전", "대전광역시"),
            Map.entry("울산", "울산광역시"), Map.entry("세종", "세종특별자치시"), Map.entry("경기", "경기도"),
            Map.entry("강원", "강원특별자치도"), Map.entry("충북", "충청북도"), Map.entry("충남", "충청남도"),
            Map.entry("전북", "전북특별자치도"), Map.entry("전남", "전라남도"), Map.entry("경북", "경상북도"),
            Map.entry("경남", "경상남도"), Map.entry("제주", "제주특별자치도"));

    /**
     * 시도 이름을 비교용 두 글자로 맞춘다 (서울특별시·서울 → 서울, 강원도·강원특별자치도 → 강원)
     */
    public static String sidoKey(String sido) {
        if (sido == null || sido.isBlank()) {
            return "";
        }
        String s = sido.trim();
        for (Map.Entry<String, String> e : SIDO.entrySet()) {
            if (s.equals(e.getKey()) || s.equals(e.getValue())) {
                return e.getKey();
            }
        }
        // 충청북도 → 충북, 경상남도 → 경남, 강원도 → 강원 처럼 정식·옛 이름 대응
        if (s.length() >= 4 && (s.endsWith("도"))) {
            return String.valueOf(s.charAt(0)) + s.charAt(2);
        }
        return s.length() >= 2 ? s.substring(0, 2) : s;
    }

    /** 도로명 또는 지번 주소가 같은지 */
    public static boolean sameAddress(TransitPlace place, KakaoAddress kakao) {
        if (place == null || kakao == null || place.getSggNm() == null) {
            return false;
        }
        if (!sameSgg(place.getSggNm(), kakao.region2())) {
            return false;
        }
        boolean sameRoad = place.getRoadName() != null && place.getRoadName().equals(kakao.roadName())
                && Objects.equals(place.getBuildingMainNo(), kakao.buildingMainNo())
                && Objects.equals(zero(place.getBuildingSubNo()), zero(kakao.buildingSubNo()));
        boolean sameLot = place.getEmdNm() != null && place.getEmdNm().equals(kakao.region3())
                && Objects.equals(place.getLotMainNo(), kakao.lotMainNo())
                && Objects.equals(zero(place.getLotSubNo()), zero(kakao.lotSubNo()));
        return sameRoad || sameLot;
    }

    private static boolean sameSgg(String a, String b) {
        return a != null && b != null && a.replace(" ", "").equals(b.replace(" ", ""));
    }

    private static int zero(Integer value) {
        return value == null ? 0 : value;
    }

    /**
     * 이름 비교용으로 다듬는다: 괄호·공백·특수문자와 역·터미널·공항 같은 말을 뗀다
     * 예) 서울고속버스터미널 → 서울, 진주(개양) → 진주개양, 김포국제공항 → 김포
     */
    public static String normalizeName(String name) {
        if (name == null) {
            return "";
        }
        String s = name.replaceAll("[^0-9A-Za-z가-힣]", "");
        String[] suffixes = {"국제공항", "공항", "종합버스터미널", "고속버스터미널", "시외버스터미널", "공용버스터미널",
                "버스터미널", "터미널", "고속버스정류장", "시외버스정류장", "정류장", "정류소", "승강장", "역"};
        boolean changed = true;
        while (changed) {
            changed = false;
            for (String suffix : suffixes) {
                if (s.length() > suffix.length() && s.endsWith(suffix)) {
                    s = s.substring(0, s.length() - suffix.length());
                    changed = true;
                }
            }
        }
        return s;
    }

    /**
     * 이름이 얼마나 비슷한지 (0: 무관, 1: 두 글자 이상 겹침, 2: 한쪽이 다른 쪽을 포함, 3: 같음)
     */
    public static int nameScore(String tmapName, String tagoName) {
        String a = normalizeName(tmapName);
        String b = normalizeName(tagoName);
        if (a.isEmpty() || b.isEmpty()) {
            return 0;
        }
        if (a.equals(b)) {
            return 3;
        }
        if (a.contains(b) || b.contains(a)) {
            return 2;
        }
        for (int i = 0; i + 2 <= a.length(); i++) {
            if (b.contains(a.substring(i, i + 2))) {
                return 1;
            }
        }
        return 0;
    }

    /** 두 좌표 사이 거리(m, 하버사인) */
    public static double distanceMeters(double lat1, double lon1, double lat2, double lon2) {
        double r = 6_371_000d;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double h = Math.pow(Math.sin(dLat / 2), 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.pow(Math.sin(dLon / 2), 2);
        return 2 * r * Math.asin(Math.sqrt(h));
    }

    /**
     * 도로명주소 검색 결과가 이 교통수단의 건물인지 (지하철역·주차장 같은 다른 건물을 거른다)
     */
    public static boolean looksLikeKind(TransitKind kind, String buildingName) {
        if (buildingName == null || buildingName.isBlank()) {
            return false;
        }
        String b = buildingName.replace(" ", "");
        return switch (kind) {
            case TRAIN -> b.contains("철도") || (b.endsWith("역") && !b.contains("호선") && !b.contains("지하철"));
            case EXPRESS_BUS, INTERCITY_BUS -> b.contains("터미널") || b.contains("정류장") || b.contains("정류소");
            case AIRPLANE -> b.contains("공항");
        };
    }
}
