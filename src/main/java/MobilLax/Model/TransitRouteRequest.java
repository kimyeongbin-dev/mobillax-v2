package MobilLax.Model;

/**
 * 대중교통 경로 검색 요청 (WGS84 경도 x, 위도 y)
 */
public record TransitRouteRequest(double startX, double startY, double endX, double endY) {

    /** 출발지·도착지가 모두 대한민국 범위 안의 좌표인지 */
    public boolean isInKorea() {
        return inKorea(startX, startY) && inKorea(endX, endY);
    }

    private static boolean inKorea(double lon, double lat) {
        return lon >= 124 && lon <= 132 && lat >= 33 && lat <= 39;
    }
}
