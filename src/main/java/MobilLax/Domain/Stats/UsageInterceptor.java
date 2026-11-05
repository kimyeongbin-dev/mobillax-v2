package MobilLax.Domain.Stats;

import MobilLax.Common.Usage.CountsUsage;
import MobilLax.Common.Usage.UsageMetric;
import MobilLax.Domain.Account.AccountRoles;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import java.util.regex.Pattern;

/**
 * 이용 통계를 요청 흐름에서 한 번에 센다 (v2.13, 보안 규칙 S13)
 * - 화면: GET 으로 뷰를 그린 요청을 페이지 조회로 세고, 오늘 처음 온 사람이면 방문자로도 센다. 새 화면은 따로 할 일이 없다
 * - API: @CountsUsage 가 붙은 메서드가 2xx 로 끝나면 센다
 * - 세지 않는 것: 관리자 화면과 관리자 계정의 요청, 브라우저가 아닌 요청(봇, curl, 컨테이너 헬스체크)
 */
@Component
@RequiredArgsConstructor
public class UsageInterceptor implements HandlerInterceptor {

    private static final String APP_PACKAGE = "MobilLax";
    private static final String ADMIN_VIEW_PREFIX = "admin/";
    private static final String REDIRECT_PREFIX = "redirect:";
    private static final String BROWSER_MARK = "Mozilla/";
    private static final Pattern NON_HUMAN = Pattern.compile("bot|crawl|spider|slurp|headless", Pattern.CASE_INSENSITIVE);

    private final UsageCounter counter;
    private final DailyVisitors visitors;

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler,
                           ModelAndView modelAndView) {
        if (!isPageView(request, response, handler, modelAndView) || !fromVisitor(request)) {
            return;
        }
        counter.count(UsageMetric.PAGE_VIEW);
        if (visitors.firstVisitToday(request.getRemoteAddr(), request.getHeader(HttpHeaders.USER_AGENT))) {
            counter.count(UsageMetric.VISITOR);
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        CountsUsage countsUsage = handler instanceof HandlerMethod method
                ? method.getMethodAnnotation(CountsUsage.class) : null;
        if (countsUsage != null && ex == null && succeeded(response) && fromVisitor(request)) {
            counter.count(countsUsage.value());
        }
    }

    private static boolean isPageView(HttpServletRequest request, HttpServletResponse response, Object handler,
                                      ModelAndView modelAndView) {
        if (modelAndView == null || !HttpMethod.GET.matches(request.getMethod()) || !succeeded(response)
                || !(handler instanceof HandlerMethod method)
                || !method.getBeanType().getPackageName().startsWith(APP_PACKAGE)) {
            return false;
        }
        String view = modelAndView.getViewName();
        return view != null && !view.startsWith(REDIRECT_PREFIX) && !view.startsWith(ADMIN_VIEW_PREFIX);
    }

    private static boolean succeeded(HttpServletResponse response) {
        return response.getStatus() >= 200 && response.getStatus() < 300;
    }

    private static boolean fromVisitor(HttpServletRequest request) {
        String userAgent = request.getHeader(HttpHeaders.USER_AGENT);
        return userAgent != null && userAgent.contains(BROWSER_MARK) && !NON_HUMAN.matcher(userAgent).find()
                && !request.isUserInRole(AccountRoles.ADMIN) && !request.isUserInRole(AccountRoles.ADMIN_PENDING);
    }
}
