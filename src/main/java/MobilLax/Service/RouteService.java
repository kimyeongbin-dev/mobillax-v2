package MobilLax.Service;

import MobilLax.Model.TransitRouteRequest;

public interface RouteService {

    /**
     * TMAP 대중교통 API로 경로를 검색하고 응답 JSON 원문을 반환
     *
     * @throws org.springframework.web.client.HttpStatusCodeException TMAP 이 오류 상태로 응답한 경우
     * @throws org.springframework.web.client.ResourceAccessException TMAP 응답이 없는 경우
     */
    String searchTransitRoutes(TransitRouteRequest request);
}
