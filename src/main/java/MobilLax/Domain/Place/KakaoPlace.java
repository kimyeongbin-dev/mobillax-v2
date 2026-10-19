package MobilLax.Domain.Place;

/**
 * 카카오 로컬 장소 검색 결과 한 건 (화면에 보일 값만 담는다. 좌표는 담지 않는다)
 *
 * @param id       카카오 장소 ID
 * @param distance 기준 좌표에서의 거리(m), 모르면 -1
 */
public record KakaoPlace(String id, String name, String categoryName, String roadAddress, String address,
                         String phone, int distance) {
}
