package MobilLax.Common.RateLimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.Map;

/**
 * @RateLimited 가 붙은 API 의 호출 횟수를 컨트롤러 전에 센다 (보안 규칙 S11)
 * - 한도를 넘으면 컨트롤러를 부르지 않고(외부 API 호출 없음) 429 {"error": 안내 문구}와 Retry-After 헤더(초)
 * - 형식이 틀린 요청도 센다: 입력 검증(RequestBodyValidationAdvice)보다 먼저 돈다
 */
@Component
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimiter rateLimiter;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        RateLimited rateLimited = handler instanceof HandlerMethod method
                ? method.getMethodAnnotation(RateLimited.class) : null;
        if (rateLimited == null) {
            return true;
        }
        ClientKey client = ClientKey.of(request);
        RateLimiter.Decision decision = rateLimiter.acquire(rateLimited.value(), client);
        if (decision.allowed()) {
            return true;
        }
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(decision.retryAfterSeconds()));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(),
                Map.of("error", rateLimited.value().message(decision, client.member())));
        return false;
    }
}
