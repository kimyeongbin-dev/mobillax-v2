package MobilLax;

import MobilLax.Common.RateLimit.RateLimited;
import MobilLax.Config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 보안 회귀 테스트 S11 (구조) — 로그인 없이 여는 API 는 호출 횟수 제한을 빠뜨릴 수 없게 한다 (docs/SECURITY_RULES.md)
 * 이 테스트를 지우거나 약하게 바꾸지 않는다. 규칙을 바꿔야 하면 문서와 함께 리뷰를 거친다.
 *
 * 실제로 등록된 컨트롤러 메서드 중 SecurityConfig.ANONYMOUS_API_PATHS 에 걸리는 것은 모두 @RateLimited 여야 한다.
 * 목록에 있는데 컨트롤러가 없는 경로(지운 API 가 공개 목록에 남은 경우)도 실패한다.
 */
@SpringBootTest
class RateLimitCoverageTests {

    private static final AntPathMatcher MATCHER = new AntPathMatcher();

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping mapping;

    @Test
    void anonymousApisAreRateLimited() {
        List<String> covered = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        mapping.getHandlerMethods().forEach((info, method) -> {
            Set<String> patterns = info.getPatternValues();
            boolean anonymous = patterns.stream().anyMatch(pattern -> Stream.of(SecurityConfig.ANONYMOUS_API_PATHS)
                    .anyMatch(open -> MATCHER.match(open, pattern)));
            if (!anonymous) {
                return;
            }
            covered.addAll(patterns);
            if (!method.hasMethodAnnotation(RateLimited.class)) {
                missing.add(method.getBeanType().getSimpleName() + "." + method.getMethod().getName());
            }
        });

        assertThat(covered).as("공개 API 목록의 모든 경로에 컨트롤러가 있다")
                .containsAll(List.of(SecurityConfig.ANONYMOUS_API_PATHS));
        assertThat(missing).as("로그인 없이 열렸는데 @RateLimited 가 없는 API").isEmpty();
    }
}
