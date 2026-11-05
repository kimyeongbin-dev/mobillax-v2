package MobilLax.Domain.Admin;

import MobilLax.Common.Error.UserFacingException;
import MobilLax.Common.Validation.MessageText;
import MobilLax.Common.Validation.OtpCode;
import MobilLax.Domain.Inquiry.Inquiry;
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
 * 관리자 1:1 문의 (v2.12, 보안 규칙 S12) — OTP 까지 확인한 관리자(ROLE_ADMIN)만 연다 (SecurityConfig 의 /admin/**)
 * 답변은 지금 OTP 번호를 함께 받아 서비스가 다시 확인한다
 */
@Controller
@RequestMapping("/admin/inquiries")
@RequiredArgsConstructor
public class AdminInquiryController {

    private final AdminInquiryService inquiryService;

    @GetMapping
    public String list(@RequestParam(defaultValue = "WAITING") Inquiry.Status status,
                       @RequestParam(defaultValue = "0") @Min(0) int page,
                       Principal principal, HttpServletRequest request, Model model) {
        model.addAttribute("inquiries", inquiryService.list(principal.getName(), status, page, request.getRemoteAddr()));
        model.addAttribute("status", status);
        model.addAttribute("statuses", Inquiry.Status.values());
        return "admin/inquiries";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Principal principal, HttpServletRequest request, Model model) {
        model.addAttribute("inquiry", inquiryService.detail(principal.getName(), id, request.getRemoteAddr()));
        return "admin/inquiry-detail";
    }

    @PostMapping("/{id}/answer")
    public String answer(@PathVariable Long id, @RequestParam @NotNull @OtpCode String code,
                         @RequestParam @NotBlank @Size(max = Inquiry.CONTENT_MAX) @MessageText String answer,
                         Principal principal, HttpServletRequest request, RedirectAttributes redirectAttributes) {
        try {
            inquiryService.answer(principal.getName(), id, code, answer, request.getRemoteAddr());
            redirectAttributes.addFlashAttribute("successMessage", "답변을 보냈습니다. 사용자 마이페이지에 표시됩니다.");
        } catch (UserFacingException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            redirectAttributes.addFlashAttribute("draft", answer);
        }
        return "redirect:/admin/inquiries/" + id;
    }
}
