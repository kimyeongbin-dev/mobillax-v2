package MobilLax.Common.RateLimit;

import MobilLax.Common.RateLimit.RateLimiter.Decision;
import MobilLax.Common.RateLimit.RateLimiter.Limit;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/** 호출 횟수 세기: 시간 칸 경계와 안내 문구 (S11) */
class RateLimiterTests {

    private static RateLimiter at(String instant) {
        return new RateLimiter(Clock.fixed(Instant.parse(instant), ZoneOffset.UTC));
    }

    @Test
    void countsUpToTheLimitThenDeniesUntilTheWindowEnds() {
        RateLimiter limiter = at("2026-10-20T03:00:30Z");
        Limit limit = Limit.perMinute(2);
        limiter.record("k", limit);
        limiter.record("k", limit);
        Decision denied = limiter.check("k", limit);
        assertThat(denied.allowed()).isFalse();
        assertThat(denied.retryAfterSeconds()).as("분 칸의 끝(03:01:00)까지").isEqualTo(30);
        assertThat(limiter.check("other", limit).allowed()).as("다른 키는 따로 센다").isTrue();
    }

    @Test
    void dayWindowEndsAtKoreanMidnightNotUtcMidnight() {
        // 한국 시간 2026-10-21 08:00 (UTC 10-20 23:00): 한국 자정까지 16시간
        RateLimiter limiter = at("2026-10-20T23:00:00Z");
        limiter.record("k", Limit.perDay(1));
        assertThat(limiter.check("k", Limit.perDay(1)).retryAfterSeconds()).isEqualTo(16 * 3600);
    }

    @Test
    void resetAndPurge() {
        RateLimiter limiter = at("2026-10-20T03:00:00Z");
        limiter.record("k", Limit.perMinute(1));
        limiter.reset("k");
        assertThat(limiter.check("k", Limit.perMinute(1)).allowed()).isTrue();
        assertThat(limiter.purgeExpired()).as("끝나지 않은 칸은 지우지 않는다").isFalse();
    }

    @Test
    void retryAfterTextRoundsUp() {
        assertThat(new Decision(false, 1).retryAfterText()).isEqualTo("1분");
        assertThat(new Decision(false, 61).retryAfterText()).isEqualTo("2분");
        assertThat(new Decision(false, 3600).retryAfterText()).isEqualTo("1시간");
        assertThat(new Decision(false, 3601).retryAfterText()).isEqualTo("2시간");
    }
}
