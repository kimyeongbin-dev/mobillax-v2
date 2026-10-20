package MobilLax.Common.RateLimit;

import MobilLax.Common.RateLimit.RateLimiter.Limit;

import java.util.List;

import static MobilLax.Common.RateLimit.RateLimiter.Limit.perDay;
import static MobilLax.Common.RateLimit.RateLimiter.Limit.perHour;
import static MobilLax.Common.RateLimit.RateLimiter.Limit.perMinute;

/**
 * API 호출 횟수 한도 (보안 규칙 S11) — 한도 값은 여기에만 둔다
 * 비밀번호 실패 한도는 PasswordAttemptGuard 에 있다
 */
public enum RateLimitPolicy {

    /** TMAP 대중교통: FREE 요금제가 하루 10건이다. 비로그인은 한 사람 3건, 모두 합쳐 5건까지만 써서 회원 몫을 남긴다 */
    ROUTE_SEARCH("경로 검색", List.of(perDay(10)), List.of(perDay(3)), perDay(5)),

    /** 장소 매칭(TAGO·카카오)·편 조회(TAGO)·주변정보(카카오): 상세 화면 한 번에 여러 번 부르므로 분당으로 막는다 */
    EXTERNAL_LOOKUP("조회", List.of(perMinute(60)), List.of(perMinute(20), perDay(300)), null),

    /** 회원가입: 한 곳에서 계정을 대량으로 만들지 못하게 (로그인한 사용자는 가입 화면을 쓰지 않는다) */
    REGISTER("회원가입", List.of(), List.of(perHour(10)), null);

    private final String label;
    private final List<Limit> memberLimits;
    private final List<Limit> anonymousLimits;
    private final Limit anonymousTotal;

    /**
     * @param label           안내 문구에 쓰는 이름
     * @param memberLimits    로그인 사용자 한 명의 한도
     * @param anonymousLimits 비로그인 사용자(접속 IP) 하나의 한도
     * @param anonymousTotal  비로그인 사용자를 모두 합친 한도 (없으면 null)
     */
    RateLimitPolicy(String label, List<Limit> memberLimits, List<Limit> anonymousLimits, Limit anonymousTotal) {
        this.label = label;
        this.memberLimits = memberLimits;
        this.anonymousLimits = anonymousLimits;
        this.anonymousTotal = anonymousTotal;
    }

    public List<Limit> memberLimits() {
        return memberLimits;
    }

    public List<Limit> anonymousLimits() {
        return anonymousLimits;
    }

    public Limit anonymousTotal() {
        return anonymousTotal;
    }

    /** 거절 안내 문구. 회원 한도가 있는 정책이면 비로그인 사용자에게 로그인을 권한다 */
    public String message(RateLimiter.Decision decision, boolean member) {
        String text = label + " 가능 횟수를 모두 사용했습니다. " + decision.retryAfterText() + " 뒤에 다시 시도해 주세요.";
        return member || memberLimits.isEmpty() ? text : text + " 로그인하면 더 이용할 수 있습니다.";
    }
}
