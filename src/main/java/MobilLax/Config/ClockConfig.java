package MobilLax.Config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** 앱이 쓰는 시계. 테스트는 이 빈을 바꿔 시각을 고정한다 (호출 횟수 제한의 시간 칸, RateLimitRegressionTests) */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
