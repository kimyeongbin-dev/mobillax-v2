package MobilLax.Domain.Health;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 서비스 상태 판정 (보안 규칙 S7, docs/SECURITY_RULES.md)
 * - DB: 연결이 유효한지만 본다 (쿼리·데이터 조회 없음). 비로그인 요청이 몰려도 연결 시도는 5초에 한 번
 * - 외부 API: 요청 때 호출하지 않고, 주기 점검(ExternalApiProbe)과 실제 호출 결과(TMAP)를 기록해 둔 값만 쓴다
 *   → 비로그인 사용자가 헬스체크로 외부 API 호출·한도 소모를 일으킬 수 없다
 * - 실패 이유는 로그에만 남기고, 응답에는 UP/DOWN 만 싣는다 (HealthController)
 */
@Slf4j
@Service
public class HealthService {

    /** 외부 API 이름 (로그용, 응답에는 싣지 않는다) */
    public enum Component { TMAP, TAGO, KAKAO, JUSO, PORTONE }

    static final Duration DB_CACHE = Duration.ofSeconds(5);

    private final DataSource dataSource;
    private final Clock clock;
    private final Map<Component, Boolean> external = new ConcurrentHashMap<>();
    private volatile Instant dbCheckedAt = Instant.EPOCH;
    private volatile boolean dbUp;

    @Autowired
    public HealthService(DataSource dataSource) {
        this(dataSource, Clock.systemUTC());
    }

    /** 테스트에서 시각을 바꿔 캐시 동작을 확인할 때 */
    HealthService(DataSource dataSource, Clock clock) {
        this.dataSource = dataSource;
        this.clock = clock;
    }

    /** DB 연결이 유효한지 (5초 동안은 마지막 결과를 다시 쓴다) */
    public synchronized boolean isDbUp() {
        Instant now = clock.instant();
        if (Duration.between(dbCheckedAt, now).compareTo(DB_CACHE) < 0) {
            return dbUp;
        }
        boolean up;
        try (Connection connection = dataSource.getConnection()) {
            up = connection.isValid(1);
        } catch (Exception e) {
            // 예외 메시지에 DB 주소·계정이 들어 있을 수 있어 종류만 남긴다
            log.warn("헬스체크: DB 연결 실패 ({})", e.getClass().getSimpleName());
            up = false;
        }
        dbUp = up;
        dbCheckedAt = now;
        return up;
    }

    /** 외부 API 점검 결과 기록. 상태가 바뀔 때만 로그를 남긴다 */
    public void record(Component component, boolean up) {
        Boolean before = external.put(component, up);
        if (before == null || before != up) {
            if (up) {
                log.info("헬스체크: {} 정상", component);
            } else {
                log.warn("헬스체크: {} 이상", component);
            }
        }
    }

    /** 아직 점검하지 않은 외부 API 는 실패로 보지 않는다 */
    public boolean isExternalUp() {
        return !external.containsValue(Boolean.FALSE);
    }

    /** 전체 상태: DB 와 모든 외부 API 가 정상 */
    public boolean isUp() {
        return isDbUp() && isExternalUp();
    }
}
