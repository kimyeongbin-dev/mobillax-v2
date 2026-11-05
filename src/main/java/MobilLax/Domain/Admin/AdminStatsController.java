package MobilLax.Domain.Admin;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.security.Principal;

/** 관리자 통계 화면 (v2.13, 보안 규칙 S13) — OTP 까지 확인한 관리자만 연다 (SecurityConfig 의 /admin/**) */
@Controller
@RequestMapping("/admin/stats")
@RequiredArgsConstructor
public class AdminStatsController {

    private final AdminStatsService statsService;

    @GetMapping
    public String stats(@RequestParam(defaultValue = "WEEK") AdminStatsService.Range range, Principal principal,
                        HttpServletRequest request, Model model) {
        model.addAttribute("report", statsService.report(principal.getName(), range, request.getRemoteAddr()));
        model.addAttribute("ranges", AdminStatsService.Range.values());
        return "admin/stats";
    }
}
