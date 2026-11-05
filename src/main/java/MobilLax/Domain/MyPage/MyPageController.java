package MobilLax.Domain.MyPage;

import MobilLax.Common.Validation.FormErrors;
import MobilLax.Common.Validation.PaymentId;
import MobilLax.Common.Error.UserFacingException;
import MobilLax.Domain.Inquiry.InquiryService;
import MobilLax.Domain.Payment.PaymentHistoryService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;

/**
 * 마이페이지: 정보 요약, 프로필 수정, 비밀번호 변경, 결제 내역, 회원 탈퇴 (1:1 문의는 InquiryController)
 * 로그인한 사용자 것만 다룬다 (Principal 의 이메일, 보안 규칙 S4)
 */
@Controller
@RequiredArgsConstructor
public class MyPageController {

    private final MyPageService myPageService;
    private final PaymentHistoryService paymentHistoryService;
    private final InquiryService inquiryService;

    @GetMapping("/mypage")
    public String showMyPage(Model model, Principal principal) {
        MyPageDto dto = myPageService.getMyPageInfo(principal.getName());
        model.addAttribute("name", dto.getName());
        model.addAttribute("email", dto.getEmail());
        model.addAttribute("recentRoute", dto.getRecentRoute());
        model.addAttribute("monthlyPayment", dto.getTotalPayment());
        model.addAttribute("cartCount", dto.getCartCount());
        model.addAttribute("newAnswers", inquiryService.newAnswerCount(principal.getName()));
        return "mypage";
    }

    @GetMapping("/mypage/edit")
    public String editPage(Model model, Principal principal) {
        model.addAttribute("profile", myPageService.getMyPageInfo(principal.getName()));
        return "mypage-edit";
    }

    @PostMapping("/mypage/edit")
    public String updateProfile(@Valid @ModelAttribute ProfileUpdateDto dto, BindingResult binding,
                                Principal principal, RedirectAttributes redirectAttributes) {
        // 이름 형식은 ProfileUpdateDto 의 @UserName 으로 검사한다 (S10)
        if (binding.hasErrors()) {
            redirectAttributes.addFlashAttribute("error", FormErrors.first(binding, "name"));
            return "redirect:/mypage/edit";
        }
        myPageService.updateProfile(principal.getName(), dto.getName());
        redirectAttributes.addFlashAttribute("successMessage", "프로필을 저장했습니다.");
        return "redirect:/mypage";
    }

    @GetMapping("/mypage/password")
    public String passwordPage() {
        return "mypage-password";
    }

    /**
     * 비밀번호 변경 (v2.5)
     * - 성공하면 세션 ID 를 바꾼다: 바꾸기 전 세션 ID 를 알던 사람이 계속 쓰지 못하게 (세션 고정 방지)
     */
    @PostMapping("/mypage/password")
    public String changePassword(@Valid @ModelAttribute PasswordChangeForm form, BindingResult binding,
                                 Principal principal, HttpServletRequest request,
                                 RedirectAttributes redirectAttributes) {
        if (binding.hasErrors()) {
            redirectAttributes.addFlashAttribute("error", FormErrors.first(binding, PasswordChangeForm.FIELD_ORDER));
            return "redirect:/mypage/password";
        }
        try {
            myPageService.changePassword(principal.getName(), form.getCurrentPassword(), form.getNewPassword());
        } catch (UserFacingException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/mypage/password";
        }
        request.changeSessionId();
        redirectAttributes.addFlashAttribute("successMessage", "비밀번호를 바꿨습니다.");
        return "redirect:/mypage";
    }

    /** 결제 내역 (예매 확인, v2.5) */
    @GetMapping("/mypage/payments")
    public String payments(Model model, Principal principal) {
        model.addAttribute("payments", paymentHistoryService.getHistory(principal.getName()));
        return "mypage-payments";
    }

    /**
     * 예매 내역에서 삭제 (v2.5): 화면에서만 숨기고 기록은 보관한다 (PaymentHistoryService.hide)
     * 남의 결제·없는 결제·형식이 틀린 번호는 거절된다 (S4·S10)
     */
    @PostMapping("/mypage/payments/hide")
    public String hidePayment(@RequestParam @NotNull @PaymentId String paymentId, Principal principal,
                              RedirectAttributes redirectAttributes) {
        paymentHistoryService.hide(principal.getName(), paymentId);
        redirectAttributes.addFlashAttribute("successMessage",
                "내역에서 삭제했습니다. 결제 기록은 관련 법령에 따라 보관되며, 다시 보려면 마이페이지의 1:1 문의로 요청해 주세요.");
        return "redirect:/mypage/payments";
    }

    @PostMapping("/mypage/delete")
    public String deleteAccount(Principal principal, HttpSession session) {
        myPageService.deleteAccountByEmail(principal.getName());
        session.invalidate(); // 세션 만료 처리 (로그아웃 효과)
        return "redirect:/home";
    }
}
