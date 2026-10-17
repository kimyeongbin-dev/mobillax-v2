package MobilLax.Domain.Health;

import MobilLax.Domain.Health.HealthService.Component;
import MobilLax.Domain.Payment.PortOneClient;
import MobilLax.Domain.Place.JusoClient;
import MobilLax.Domain.Place.KakaoLocalClient;
import MobilLax.Domain.Trip.TagoTripClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * 외부 API 주기 점검 (보안 규칙 S7) — 30분마다 가벼운 호출로 키·서비스가 살아 있는지 본다
 * - TAGO: 열차 도시 코드 목록 / 카카오: 서울역 좌표 → 주소 / 도로명주소: "서울역" 검색
 * - PortOne: 없는 결제 번호 조회 (404 = 키 정상, 401 = 키 오류)
 * - TMAP 대중교통은 하루 10건 한도라 점검 호출을 하지 않고, 실제 경로 검색 결과(RouteController)로 판단한다
 * - 키를 설정하지 않은 선택 API(카카오 REST, 도로명주소)는 점검하지 않는다
 * - 테스트에서는 health.external.enabled=false 로 끈다 (build.gradle)
 */
@Slf4j
@org.springframework.stereotype.Component
@RequiredArgsConstructor
public class ExternalApiProbe {

    private static final int ATTEMPTS = 2;

    private final HealthService healthService;
    private final TagoTripClient tagoTripClient;
    private final KakaoLocalClient kakaoLocalClient;
    private final JusoClient jusoClient;
    private final PortOneClient portOneClient;

    @Value("${health.external.enabled:true}")
    private boolean enabled;

    @Scheduled(fixedDelayString = "${health.external.interval-ms:1800000}",
            initialDelayString = "${health.external.initial-delay-ms:15000}")
    public void probe() {
        if (!enabled) {
            return;
        }
        check(Component.TAGO, tagoTripClient::ping);
        if (kakaoLocalClient.isConfigured()) {
            check(Component.KAKAO, () -> kakaoLocalClient.coordToAddress(37.5547, 126.9706));
        }
        if (jusoClient.isConfigured()) {
            check(Component.JUSO, () -> jusoClient.search("서울역"));
        }
        check(Component.PORTONE, () -> portOneClient.find("health-check-not-a-payment"));
    }

    /** 한 번 실패는 일시적인 연결 끊김일 수 있어 한 번 더 시도하고, 두 번 모두 실패해야 이상으로 기록한다 */
    private void check(Component component, Runnable call) {
        for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
            try {
                call.run();
                healthService.record(component, true);
                return;
            } catch (RuntimeException e) {
                // 요청 URL·헤더에 키가 있을 수 있어 예외 메시지는 남기지 않는다
                log.warn("헬스체크: {} 호출 실패 {}/{} ({})", component, attempt, ATTEMPTS, e.getClass().getSimpleName());
            }
        }
        healthService.record(component, false);
    }
}
