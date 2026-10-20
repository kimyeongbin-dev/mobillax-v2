package MobilLax;

import MobilLax.Common.RateLimit.RateLimiter;
import MobilLax.Domain.Account.UserAccount;
import MobilLax.Domain.Account.UserAccountRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 보안 회귀 테스트 S11 — 응답 시간으로 가입 여부·비밀번호를 알아내지 못하게 (v2.9, docs/SECURITY_RULES.md)
 * 이 테스트를 지우거나 약하게 바꾸지 않는다. 규칙을 바꿔야 하면 문서와 함께 리뷰를 거친다.
 *
 * 로그인 시간의 대부분은 BCrypt 비교다. 없는 이메일이면 비교를 건너뛰어 빨리 끝나면, 응답 시간만으로 가입한 이메일을
 * 골라낼 수 있다. 그래서 없는 이메일도 Spring Security 가 같은 비용의 가짜 해시와 한 번 비교하는지 확인한다
 * (AccountUserDetailsService 가 UsernameNotFoundException 을 던져야 이 경로를 탄다).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class LoginTimingRegressionTests {

    private static final String MEMBER = "timing-member@test.local";
    private static final String WRONG = "FAKE_wrong1";
    private static final int SAMPLES = 6;

    @Autowired
    MockMvc mvc;

    @Autowired
    RateLimiter rateLimiter;

    @Autowired
    UserAccountRepository userAccountRepository;

    @SpyBean
    PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        rateLimiter.clear();
        UserAccount account = new UserAccount();
        account.setEmail(MEMBER);
        account.setName("시간차검사");
        account.setPassword(passwordEncoder.encode("FAKE_correct1"));
        account.setRole("USER");
        userAccountRepository.save(account);
        clearInvocations(passwordEncoder);
    }

    @AfterEach
    void clearCounts() {
        rateLimiter.clear();
    }

    private long loginNanos(String email) throws Exception {
        long start = System.nanoTime();
        mvc.perform(post("/login").with(csrf()).param("email", email).param("password", WRONG));
        return System.nanoTime() - start;
    }

    @Test
    void unknownEmailAndWrongPasswordBothRunOneBcryptComparison() throws Exception {
        loginNanos("nobody-timing@test.local");
        verify(passwordEncoder, times(1)).matches(eq(WRONG), anyString());

        clearInvocations(passwordEncoder);
        loginNanos(MEMBER);
        verify(passwordEncoder, times(1)).matches(eq(WRONG), anyString());
    }

    @Test
    void responseTimesAreAlike() throws Exception {
        loginNanos("warmup@test.local"); // 가짜 해시는 처음 쓸 때 만든다
        rateLimiter.clear();
        long[] unknown = new long[SAMPLES];
        long[] wrongPassword = new long[SAMPLES];
        for (int i = 0; i < SAMPLES; i++) {
            unknown[i] = loginNanos("nobody" + i + "-timing@test.local");
            wrongPassword[i] = loginNanos(MEMBER);
            rateLimiter.clear(); // 잠금에 걸리지 않게
        }
        double ratio = (double) median(unknown) / median(wrongPassword);
        assertThat(ratio).as("없는 이메일 / 틀린 비밀번호 응답 시간 중앙값 비율").isBetween(0.5, 2.0);
    }

    /** 잠긴 동안은 비밀번호를 비교하지 않는다. 있는 이메일·없는 이메일 모두 같으므로 가입 여부는 드러나지 않는다 */
    @Test
    void lockedLoginSkipsComparisonForEveryEmailAlike() throws Exception {
        for (String email : new String[]{MEMBER, "nobody-locked@test.local"}) {
            for (int i = 0; i < 5; i++) {
                loginNanos(email);
            }
            clearInvocations(passwordEncoder);
            loginNanos(email);
            verify(passwordEncoder, never()).matches(anyString(), anyString());
        }
    }

    private static long median(long[] values) {
        long[] sorted = values.clone();
        Arrays.sort(sorted);
        return sorted[sorted.length / 2];
    }
}
