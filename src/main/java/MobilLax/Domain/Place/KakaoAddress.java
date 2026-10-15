package MobilLax.Domain.Place;

/**
 * 카카오 좌표→주소 변환 결과. 비교에만 쓰고 저장하지 않는다 (카카오 약관)
 *
 * @param region1 시도 (카카오는 "서울", "경남"처럼 줄여서 줌)
 * @param region2 시군구 (예: 서초구, 성남시 분당구)
 * @param region3 읍면동 (지번 주소 기준)
 */
public record KakaoAddress(
        String region1,
        String region2,
        String region3,
        String roadName,
        Integer buildingMainNo,
        Integer buildingSubNo,
        Integer lotMainNo,
        Integer lotSubNo,
        String roadAddr,
        String jibunAddr) {
}
