package MobilLax.Domain.Place;

/**
 * 도로명주소 검색 API 결과 한 건 (business.juso.go.kr addrLinkApi)
 *
 * @param buildingName bdNm (예: 서울역(철도역), 동서울종합터미널)
 * @param siNm         시도 (예: 서울특별시)
 */
public record JusoAddress(
        String buildingName,
        String roadAddr,
        String jibunAddr,
        String siNm,
        String sggNm,
        String roadName,
        Integer buildingMainNo,
        Integer buildingSubNo,
        String emdNm,
        Integer lotMainNo,
        Integer lotSubNo,
        boolean underground) {
}
