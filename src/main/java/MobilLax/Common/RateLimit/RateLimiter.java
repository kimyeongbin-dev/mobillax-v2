package MobilLax.Common.RateLimit;

import MobilLax.Common.Time.KoreaTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 호출 횟수 세기 (보안 규칙 S11): 키마다 정해진 시간 칸(window) 안의 횟수를 센다
 * - 시간 칸은 한국 시간 기준으로 나눈다. 하루 칸은 한국 자정에 다시 시작한다 (TMAP 일일 한도와 같은 기준)
 * - 서버 한 대의 메모리에만 둔다. 서버를 다시 띄우면 처음부터 센다
 * - 키가 상한까지 차면(여러 IP 로 채우는 공격) 새 키는 잠시 거절한다. 외부 API 한도를 지키는 쪽을 고른다
 */
@Slf4j
@Component
public class RateLimiter {

    /** 메모리에 두는 키 수 상한 */
    static final int MAX_KEYS = 100_000;
    /** 키가 꽉 찼을 때 다시 시도하라고 알리는 시간(초) */
    private static final long FULL_RETRY_SECONDS = 60;

    /** 시간 칸 하나에 허용하는 횟수 */
    public record Limit(int max, Duration window) {

        public static Limit perMinute(int max) {
            return new Limit(max, Duration.ofMinutes(1));
        }

        public static Limit perHour(int max) {
            return new Limit(max, Duration.ofHours(1));
        }

        public static Limit perDay(int max) {
            return new Limit(max, Duration.ofDays(1));
        }
    }

    /** 허용 여부와, 거절이면 다시 쓸 수 있을 때까지 남은 초 */
    public record Decision(boolean allowed, long retryAfterSeconds) {

        static final Decision ALLOWED = new Decision(true, 0);

        /** "3분", "5시간" (올림) */
        public String retryAfterText() {
            long minutes = Math.max(1, (retryAfterSeconds + 59) / 60);
            return minutes < 60 ? minutes + "분" : (minutes + 59) / 60 + "시간";
        }
    }

    private record Window(long end, int count) {
    }

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final Clock clock;

    /** @param clock 테스트가 시각을 정할 수 있게 주입받는다 (ClockConfig) */
    public RateLimiter(Clock clock) {
        this.clock = clock;
    }

    /**
     * API 호출 한 번: 클라이언트의 한도가 모두 남아 있을 때만 세고 허용한다
     * 비로그인은 모든 비로그인 사용자를 합친 한도도 본다 (외부 API 한도를 회원 몫까지 쓰지 않게)
     */
    public Decision acquire(RateLimitPolicy policy, ClientKey client) {
        String prefix = "api:" + policy.name() + ":";
        Map<String, Limit> limits = new LinkedHashMap<>();
        for (Limit limit : client.member() ? policy.memberLimits() : policy.anonymousLimits()) {
            limits.put(prefix + client.value() + ":" + limit.window().toSeconds(), limit);
        }
        if (!client.member() && policy.anonymousTotal() != null) {
            limits.put(prefix + "anonymous-total", policy.anonymousTotal());
        }

        Decision denied = limits.entrySet().stream()
                .map(entry -> check(entry.getKey(), entry.getValue()))
                .filter(decision -> !decision.allowed())
                .max(Comparator.comparingLong(Decision::retryAfterSeconds))
                .orElse(Decision.ALLOWED);
        if (denied.allowed()) {
            limits.forEach(this::record);
        }
        return denied;
    }

    /** 세지 않고, 한도가 남았는지만 본다 */
    public Decision check(String key, Limit limit) {
        long now = clock.instant().getEpochSecond();
        Window window = windows.get(key);
        if (window == null && windows.size() >= MAX_KEYS && !purgeExpired()) {
            return new Decision(false, FULL_RETRY_SECONDS);
        }
        if (window == null || now >= window.end() || window.count() < limit.max()) {
            return Decision.ALLOWED;
        }
        return new Decision(false, window.end() - now);
    }

    /** 한 번 센다 (API 호출, 로그인 실패 등) */
    public void record(String key, Limit limit) {
        long now = clock.instant().getEpochSecond();
        windows.compute(key, (k, window) -> window == null || now >= window.end()
                ? new Window(windowEnd(now, limit.window()), 1)
                : new Window(window.end(), window.count() + 1));
    }

    /** 센 횟수를 지운다 (로그인 성공 등) */
    public void reset(String key) {
        windows.remove(key);
    }

    /** 끝난 시간 칸을 지운다 (10분마다). 지운 것이 있으면 true */
    @Scheduled(fixedDelay = 600_000)
    public boolean purgeExpired() {
        long now = clock.instant().getEpochSecond();
        int before = windows.size();
        windows.values().removeIf(window -> now >= window.end());
        if (windows.size() >= MAX_KEYS) {
            log.warn("호출 횟수 키가 상한({})에 닿아 새 클라이언트를 잠시 거절합니다", MAX_KEYS);
        }
        return windows.size() < before;
    }

    /** 테스트끼리 횟수가 섞이지 않게 비운다 (앱 코드는 부르지 않는다) */
    public void clear() {
        windows.clear();
    }

    /** 지금이 든 시간 칸의 끝 (한국 시간 기준으로 나눈다. 하루 칸이면 다음 한국 자정) */
    private long windowEnd(long epochSecond, Duration window) {
        long offset = KoreaTime.ZONE.getRules().getOffset(clock.instant()).getTotalSeconds();
        long length = window.toSeconds();
        return (Math.floorDiv(epochSecond + offset, length) + 1) * length - offset;
    }
}
