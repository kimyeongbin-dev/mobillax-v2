package MobilLax.Domain.Nearby;

import MobilLax.Domain.Health.HealthService;
import MobilLax.Domain.Place.KakaoLocalClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * 주변정보: 기준 좌표 주변의 장소를 카카오 로컬에서 찾아 화면용 값으로 돌려준다
 * - 카카오 키는 서버에만 둔다 (보안 규칙 S9). 좌표와 결과는 저장하지 않는다 (카카오 약관)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NearbyService {

    /** 걸어서 갈 만한 거리(m) */
    static final int RADIUS_METERS = 1000;

    /** 한 번에 보일 장소 수 (카카오 한 페이지 최대) */
    static final int MAX_PLACES = 15;

    private final KakaoLocalClient kakaoClient;
    private final HealthService healthService;

    public List<NearbyPlaceView> find(NearbyCategory category, double lat, double lon) {
        if (!kakaoClient.isConfigured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "주변정보를 사용할 수 없습니다.");
        }
        try {
            List<NearbyPlaceView> places = kakaoClient
                    .categorySearch(category.kakaoCode(), lat, lon, RADIUS_METERS, MAX_PLACES).stream()
                    .map(NearbyPlaceView::from)
                    .toList();
            healthService.record(HealthService.Component.KAKAO, true);
            return places;
        } catch (RuntimeException e) {
            // 요청 URL·키는 로그에 남기지 않는다
            log.warn("주변정보 조회 실패 ({}): {}", category, e.getClass().getSimpleName());
            healthService.record(HealthService.Component.KAKAO, false);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "주변정보를 불러올 수 없습니다. 잠시 후 다시 시도해 주세요.");
        }
    }
}
