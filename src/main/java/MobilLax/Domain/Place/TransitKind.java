package MobilLax.Domain.Place;

/**
 * 예매 대상 장거리 교통수단 (TAGO API 와 1:1 대응)
 * - TMAP 은 고속버스와 시외버스를 모두 EXPRESSBUS 로 주고 type 으로 구분한다 (23 고속, 22 시외)
 */
public enum TransitKind {

    TRAIN("기차역", new String[]{"역"}),
    EXPRESS_BUS("고속버스 터미널", new String[]{"고속버스터미널", "터미널"}),
    INTERCITY_BUS("시외버스 터미널", new String[]{"시외버스터미널", "터미널"}),
    AIRPLANE("공항", new String[]{"공항"});

    private final String label;
    /** 도로명주소 검색어를 만들 때 이름 뒤에 붙여 볼 말 (앞에서부터 차례로 시도) */
    private final String[] searchSuffixes;

    TransitKind(String label, String[] searchSuffixes) {
        this.label = label;
        this.searchSuffixes = searchSuffixes;
    }

    public String label() {
        return label;
    }

    public String[] searchSuffixes() {
        return searchSuffixes;
    }

    /**
     * TMAP 구간의 mode·type·route 로 교통수단을 정한다. 장거리 교통수단이 아니면 null
     */
    public static TransitKind fromTmapLeg(String mode, Integer type, String route) {
        if (mode == null) {
            return null;
        }
        return switch (mode) {
            case "TRAIN" -> TRAIN;
            case "AIRPLANE" -> AIRPLANE;
            case "EXPRESSBUS" -> {
                if (type != null && type == 22) yield INTERCITY_BUS;
                if (type != null && type == 23) yield EXPRESS_BUS;
                yield route != null && route.startsWith("시외버스") ? INTERCITY_BUS : EXPRESS_BUS;
            }
            default -> null;
        };
    }
}
