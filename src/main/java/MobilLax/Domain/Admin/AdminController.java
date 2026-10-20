package MobilLax.Domain.Admin;

import MobilLax.Common.Error.UserFacingException;
import MobilLax.Common.Validation.OtpCode;
import MobilLax.Common.Validation.PlainText;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;

/**
 * 관리자 화면 (v2.11, 보안 규칙 S12) — OTP 까지 확인한 관리자(ROLE_ADMIN)만 연다 (SecurityConfig)
 * 정지·해제·잠금 풀기는 지금 OTP 번호를 함께 받아 서비스가 다시 확인한다
 */
@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminAccountService accountService;
    private final AdminAuditService auditService;

    /** 열람 이유 최대 길이 (감사 로그 내용 칸 200자 안에 들어가게) */
    static final int REASON_MAX = 100;

    @GetMapping
    public String dashboard(Model model) {
        model.addAttribute("summary", accountService.summary());
        model.addAttribute("recentLogs", auditService.recent().stream().limit(10).toList());
        return "admin/index";
    }

    @GetMapping("/users")
    public String users(@RequestParam(required = false) @Size(max = 100) @PlainText String q,
                        @RequestParam(defaultValue = "0") @Min(0) int page,
                        Principal principal, HttpServletRequest request, Model model) {
        model.addAttribute("users", accountService.search(principal.getName(), q, page, request.getRemoteAddr()));
        model.addAttribute("q", q == null ? "" : q);
        return "admin/users";
    }

    @GetMapping("/users/{id}")
    public String user(@PathVariable Long id, Principal principal, HttpServletRequest request, Model model) {
        model.addAttribute("detail", accountService.detail(principal.getName(), id, request.getRemoteAddr()));
        return "admin/user-detail";
    }

    /** 개인정보 보기: 성공하면 리다이렉트하지 않고 이 응답에만 실제 값을 담는다 (새로고침·뒤로 가기로 다시 보이지 않게) */
    @PostMapping("/users/{id}/reveal")
    public String reveal(@PathVariable Long id, @RequestParam @NotNull @OtpCode String code,
                         @RequestParam @NotBlank @Size(max = REASON_MAX) @PlainText String reason,
                         Principal principal, HttpServletRequest request, Model model, RedirectAttributes redirectAttributes) {
        try {
            AdminAccountService.Revealed revealed = accountService.reveal(principal.getName(), id, code, reason.trim(),
                    request.getRemoteAddr());
            model.addAttribute("revealed", revealed);
            model.addAttribute("detail", accountService.detail(principal.getName(), id, request.getRemoteAddr()));
            return "admin/user-detail";
        } catch (UserFacingException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/admin/users/" + id;
        }
    }

    @PostMapping("/users/{id}/suspend")
    public String suspend(@PathVariable Long id, @RequestParam @NotNull @OtpCode String code, Principal principal,
                          HttpServletRequest request, RedirectAttributes redirectAttributes) {
        return act(id, redirectAttributes, "이용을 정지했습니다. 열려 있던 로그인도 끝냈습니다.",
                () -> accountService.suspend(principal.getName(), id, code, request.getRemoteAddr()));
    }

    @PostMapping("/users/{id}/unsuspend")
    public String unsuspend(@PathVariable Long id, @RequestParam @NotNull @OtpCode String code, Principal principal,
                            HttpServletRequest request, RedirectAttributes redirectAttributes) {
        return act(id, redirectAttributes, "정지를 풀었습니다.",
                () -> accountService.unsuspend(principal.getName(), id, code, request.getRemoteAddr()));
    }

    @PostMapping("/users/{id}/unlock")
    public String unlock(@PathVariable Long id, @RequestParam @NotNull @OtpCode String code, Principal principal,
                         HttpServletRequest request, RedirectAttributes redirectAttributes) {
        return act(id, redirectAttributes, "로그인 잠금을 풀었습니다.",
                () -> accountService.unlockLogin(principal.getName(), id, code, request.getRemoteAddr()));
    }

    @GetMapping("/audit")
    public String audit(Model model) {
        model.addAttribute("logs", auditService.recent());
        return "admin/audit";
    }

    private String act(Long id, RedirectAttributes redirectAttributes, String done, Runnable action) {
        try {
            action.run();
            redirectAttributes.addFlashAttribute("successMessage", done);
        } catch (UserFacingException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/users/" + id;
    }
}
