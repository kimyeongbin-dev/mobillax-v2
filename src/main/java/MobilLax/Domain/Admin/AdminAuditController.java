package MobilLax.Domain.Admin;

import MobilLax.Common.Error.UserFacingException;
import MobilLax.Common.Time.KoreaTime;
import MobilLax.Common.Validation.OtpCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.time.YearMonth;

/** 감사 로그 정기 점검 화면 (v2.13, 보안 규칙 S12) — OTP 까지 확인한 관리자만 연다 (SecurityConfig 의 /admin/**) */
@Controller
@RequestMapping("/admin/audit")
@RequiredArgsConstructor
public class AdminAuditController {

    private static final String MONTH_PATTERN = "yyyy-MM";

    private final AdminAuditReviewService reviewService;

    @GetMapping
    public String review(@RequestParam(required = false) @DateTimeFormat(pattern = MONTH_PATTERN) YearMonth month,
                         @RequestParam(required = false) AdminAuditLog.Action action,
                         @RequestParam(required = false) @Min(1) Long target,
                         @RequestParam(defaultValue = "0") @Min(0) int page,
                         Principal principal, HttpServletRequest request, Model model) {
        YearMonth selected = month == null ? YearMonth.from(KoreaTime.today()) : month;
        model.addAttribute("review", reviewService.review(principal.getName(), selected, action, target, page,
                request.getRemoteAddr()));
        model.addAttribute("action", action);
        model.addAttribute("target", target);
        model.addAttribute("actions", AdminAuditLog.Action.values());
        return "admin/audit";
    }

    @PostMapping("/review")
    public String confirm(@RequestParam @NotNull @DateTimeFormat(pattern = MONTH_PATTERN) YearMonth month,
                          @RequestParam @NotNull @OtpCode String code, Principal principal,
                          HttpServletRequest request, RedirectAttributes redirectAttributes) {
        try {
            reviewService.confirm(principal.getName(), month, code, request.getRemoteAddr());
            redirectAttributes.addFlashAttribute("successMessage", month + " 점검 완료를 감사 로그에 남겼습니다.");
        } catch (UserFacingException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        redirectAttributes.addAttribute("month", month.toString());
        return "redirect:/admin/audit";
    }
}
